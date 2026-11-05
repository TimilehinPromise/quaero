# The bookmark pipeline

How a submitted URL becomes playable audio. Read this before changing anything under
`org.timmy.knowcommute.pipeline`.

## The problem

A bookmark needs three slow, expensive, failure-prone things done to it: fetch and extract,
summarise (costs money), synthesise speech (costs more money). Each takes seconds to a minute.
`POST /api/v1/bookmarks` returns in milliseconds with `PENDING`, so all of it happens later —
in the background, surviving restarts, without paying twice.

**State lives in Postgres, never in memory.** There is no in-memory queue and no future
holding progress. Kill the JVM at any instant, restart, and work resumes from the database.
That constraint drives every other decision here.

## States

```
   ┌─────────┐  extract   ┌─────────────┐ summarise ┌──────────────────┐   speak   ┌───────┐
   │ PENDING │ ─────────► │ SUMMARIZING │ ────────► │ GENERATING_AUDIO │ ────────► │ READY │
   └─────────┘            └─────────────┘           └──────────────────┘           └───────┘
        │                        │                           │
        └────────────────────────┴───────────────────────────┘
                                 │  attempts exhausted
                                 ▼
                            ┌────────┐
                            │ FAILED │
                            └────────┘
```

**The status names the work to be done next, not the work just finished.** A bookmark in
`SUMMARIZING` is not being summarised right now — it has finished extraction and is queued for
summarisation. `PENDING` means "needs extracting". This is why step lookup is simply
`steps.get(bookmark.getStatus())`: the status is the routing key.

The names read present-progressive but mean "queued for". Inherited from PRD §9.2; know it
before it confuses you.

`READY`, `FAILED` and `BLOCKED` are terminal (`BookmarkStatus.isTerminal()`) and are never claimed.

## Components

| Class | Job |
|---|---|
| `PipelineScheduler` | The clock. `@Scheduled` every `readcast.pipeline.poll-interval`. Nothing else. |
| `PipelineWorker` | Claims a batch, fans it across a fixed thread pool, waits. |
| `BookmarkClaimer` | The SQL. Claims due rows, returns their ids. |
| `BookmarkPipeline` | Runs **one** bookmark through **one** step. |
| `RetryPolicy` | Exhaustion check and exponential backoff. |

`BookmarkPipeline` is deliberately dumb: one bookmark, one step, return. It does not loop a
bookmark to `READY`. Three stages means three ticks, three claims, three independent commits.
That is what makes it resumable.

## The claim

```sql
UPDATE bookmarks
   SET attempts = attempts + 1, next_attempt_at = :leaseUntil, updated_at = now()
 WHERE id IN (
       SELECT id FROM bookmarks
        WHERE deleted_at IS NULL
          AND status IN ('PENDING','SUMMARIZING','GENERATING_AUDIO')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        ORDER BY created_at
          FOR UPDATE SKIP LOCKED
        LIMIT :batch)
RETURNING id
```

`FOR UPDATE` locks the selected rows. `SKIP LOCKED` is the important half: a second worker
running the same statement steps over locked rows rather than blocking. Four workers hit the
queue at once and each leaves with a disjoint set — no coordination, no Redis, no advisory
lock.

## Lease, not lock

Row locks die with the transaction, and the step **must** run outside a transaction — a 60s
TTS call holding a pooled connection exhausts the pool at trivial load. So the lock is gone
before the work starts. Something else keeps a second worker off the row.

That is `next_attempt_at = :leaseUntil`. The claim pushes the timestamp into the future; the
poll only selects rows whose `next_attempt_at` has passed. The row is invisible while being
worked. If the worker dies, nothing needs cleaning up — the lease lapses and the row
reappears.

`next_attempt_at` therefore carries two meanings that are the same meaning: *retry backoff*
and *lease*. Both say "don't touch this row until T". Hence no `locked_until` column.

Tuning: too short and a slow-but-healthy step gets claimed twice concurrently; too long and a
crashed worker's bookmark stalls. The shipped default is 45 minutes against Kokoro synthesis
that runs minutes per article on CPU (devlog 0008) — a lease that lapses mid-synthesis
reclaims the row and burns the work twice.

## Failure

```
step throws
     │
     ▼
recordFailure()
     │
     ├── SiteBlockedException ──────► block(msg)                status = BLOCKED, terminal
     ├── NoReadableContentException ► block(msg)                status = BLOCKED, terminal
     ├── UnsupportedContentException ► fail(msg)                status = FAILED, terminal
     ├── ExtractionException, not retryable ──► fail(msg)       status = FAILED, terminal
     ├── attempts >= maxAttempts ──► fail(msg)                  status = FAILED, terminal
     └── otherwise                ──► scheduleRetry(msg, now + backoff)
                                        status unchanged, row reappears after backoff
```

Retries exist for *transient* failures — a timeout, a 5xx, a rate limit that lifts. A
*deterministic* failure answers the same way on every attempt: the bytes received contain too
few words, or the page renders its content with JavaScript and the markup has none. Those are
terminal on the first attempt; the backoff series would only delay the same answer.
`ExtractionException.isRetryable()` carries the distinction, and `NoReadableContentException`
(a JS-rendered shell, or a page with no readable text) blocks rather than fails because the
recovery is the same as for a refused fetch: the user pastes the text.

Backoff doubles from `initial-backoff`: 30s, 60s, 120s.

**`attempts` counts starts, not failures** — incremented in the claim SQL, before the work
runs. Counting on failure is the obvious reading of "max 3 retries" and it is wrong: a step
that kills the worker never reaches `recordFailure`, so the counter never moves and the row
retries forever. Counting at claim time bounds crash loops.

**Advancing resets it to zero.** Otherwise a bookmark passing all three stages sits at
`attempts = 3` after three *successes*. Attempts are per-step.

## Transaction boundaries

```
┌─ tx ──────────┐                                    ┌─ tx ─────────┐
│ load bookmark │  ── step.execute() ── no tx ──►    │ save result  │
└───────────────┘     (network, 10-60s)              └──────────────┘
```

Three commits, never one spanning the work. Connections are not held across network calls, and
each step's result is durable the moment it lands — a crash during summarisation does not roll
back extraction already paid for.

These are `TransactionTemplate` calls, not `@Transactional` annotations, and that is not
style. `@Transactional` works by proxy: callers get a wrapper that opens a transaction and
delegates. Inside the bean, `this` is the real object, not the wrapper, so a self-call is
never intercepted. `process()` calls `load`/`save`/`recordFailure` on itself — annotated, they
would silently run with no transaction at all. `TransactionTemplate` puts the boundary at the
call site where it is visible.

## At-least-once

**A worker that finishes a step but dies before committing loses the write, and the row is
reprocessed once the lease lapses.**

Harmless for extraction. For summarisation and TTS it means paying twice for one article.
That is what `bookmarks.content_hash` is for: stage 5 checks whether text with that hash was
already summarised before spending anything. **Steps 4, 5 and 6 must tolerate running twice on
the same bookmark. This is not optional.**

Audio generation writes the MP3 and its word-timing JSON to deterministic bookmark keys and only
then advances to `READY`. Replaying after a crash overwrites both artifacts rather than creating
new ones. The timing endpoint derives its sidecar key from the owned bookmark's audio key, so no
expiring URL or second storage key needs a database column.

## Configuration

```properties
readcast.pipeline.enabled=true        # off in tests so it cannot race fixtures
readcast.pipeline.poll-interval=5s
readcast.pipeline.batch-size=10
readcast.pipeline.concurrency=4
readcast.pipeline.max-attempts=3
readcast.pipeline.lease=45m
readcast.pipeline.initial-backoff=2m
```

## Still not built

- **No per-user fairness.** Global FIFO, so one user pasting 50 URLs delays everyone. Postgres
  forbids `FOR UPDATE` with window functions, so partitioning by `user_id` needs a second
  statement.
- **No rate limit.** PRD §13's 5 articles/day is unimplemented.

The three steps (`ExtractionStep`, `SummarizationStep`, `AudioGenerationStep`) shipped in
stages 4–6. Placeholder steps were never an option: a step that quietly "succeeds" would move
bookmarks to `READY` with no audio.

Background and rejected alternatives: `devlog/0005-2026-08-22-queue-and-attempt-semantics.md`.
