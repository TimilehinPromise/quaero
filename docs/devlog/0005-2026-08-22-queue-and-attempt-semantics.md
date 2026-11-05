# 0005 — Queue claim and attempt semantics

Date: 2026-08-22
Stage: 3 — Pipeline state machine
Status: accepted

## Context

D5 settled `FOR UPDATE SKIP LOCKED` over advisory locks, but not what a *claim* means. Row
locks release when the transaction ends, and the step itself must run **outside** any
transaction (a 60s TTS call holding a pooled connection exhausts the pool). So the lock is
gone before the work starts, and something else has to stop a second worker taking the same
row.

## Decision

**A claim leases the row by pushing `next_attempt_at` into the future**, in the same statement
that selects it:

```sql
UPDATE bookmarks SET attempts = attempts + 1, next_attempt_at = :leaseUntil
 WHERE id IN (SELECT id FROM bookmarks WHERE … ORDER BY created_at
              FOR UPDATE SKIP LOCKED LIMIT :batch)
RETURNING id
```

The poll only sees rows whose `next_attempt_at` has passed, so a leased row is invisible until
the lease lapses. No new column: "retry backoff" and "lease" both mean *don't touch this row
until T*.

**`attempts` counts starts, not failures**, and is incremented at claim time. **Advancing a
step resets it to zero**, so it means "attempts at the current step".

Transaction boundaries are explicit `TransactionTemplate` calls, not `@Transactional` methods.

## Alternatives

**Incrementing `attempts` on failure.** The obvious reading of PRD §7's "max 3 retries", and
wrong: a step that kills the worker — OOM, JVM crash, pod eviction — never reaches the failure
handler, so the counter never moves and the row retries forever. Counting at claim time bounds
crash loops too.

**Leaving `attempts` cumulative across the pipeline.** A bookmark passing extraction,
summarization and audio would reach `attempts = 3` after three *successes* and be failed by a
`maxAttempts = 3` policy. Per-step counting is what "3 retries" actually means.

**A dedicated `locked_until` or `worker_id` column.** Equivalent, and a second column meaning
the same thing as one we already have.

**`@Transactional` on the pipeline's own methods.** This was written first and was a bug:
`process()` calls them on `this`, so Spring's proxy never intercepts and none of the
annotations do anything. It would have run every step in no transaction at all, silently.
Caught before it ran; `TransactionTemplate` makes the boundaries visible at the call site,
which suits a class whose whole job is where transactions begin and end.

## Consequences

- **Delivery is at-least-once, not exactly-once.** A worker that finishes the step but dies
  before committing loses the write, and the row is reprocessed after the lease lapses.
  Steps 4–6 must tolerate being run twice on the same bookmark — this is what the
  `content_hash` idempotency check in stage 5 is for.
- **The lease duration is a real tuning parameter.** Too short and a slow-but-healthy step is
  claimed twice concurrently; too long and a crashed worker's bookmark stalls. Default is 5
  minutes against a <60s expected step (PRD §7), which is generous on purpose.
- **Per-user fairness is not implemented.** The queue is global FIFO, so one user pasting 50
  URLs delays everyone. Postgres forbids `FOR UPDATE` with window functions, so partitioning
  by `user_id` needs a second statement, and it was not worth the complexity before there are
  users. PRD §13's "5 articles/day" rate limit is the other half of this and is also
  outstanding. **Both are still open.**
- **No `PipelineStep` beans exist yet**, so the scheduler currently claims rows, finds no
  handler, and releases the lease. Stages 4–6 supply the implementations. Stubs live in test
  code only — shipping placeholder steps that quietly "succeed" would be worse than doing
  nothing visibly.
- The scheduler is behind `readcast.pipeline.enabled`, off in tests so it cannot race the
  fixtures.

## Verification

`ConcurrentClaimTest` runs 4 threads against 40 rows and asserts the claims contain no
duplicates and cover every row exactly once — the property `SKIP LOCKED` exists to provide.
`BookmarkPipelineTest` covers the walk to READY, terminal failure after `maxAttempts`, backoff
before retry, lease expiry, and soft-deleted and terminal rows being ignored.
