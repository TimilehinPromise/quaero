# ReadCast Backend — Implementation Plan

## Context

`know-commute` is the **backend** for ReadCast (`docs/ReadCast_PRD.md`): paste a URL, get a
podcast-style audio summary. Today the repo is a bare Spring Boot 4.1.1 scaffold — one
`@SpringBootApplication` class, an empty Liquibase master changelog, and Testcontainers
wiring. No entities, no controllers, no pipeline.

The PRD is a product document, not a build order. It leaves the risky parts implicit and a
few of its technical choices don't survive contact with the constraints. This plan turns it
into staged backend work, records where we diverge and why, and sets up the two artefacts
asked for: a **devlog** (dated decision history) and **coding standards**.

Scope is backend only. No React, no PWA, no service worker — the frontend consumes the API
defined here.

### Decisions locked this session

| # | Decision | Rationale |
|---|---|---|
| D1 | Backend extracts articles with **jsoup**; Readability.js is not used anywhere | A PWA can't `fetch()` third-party article HTML (CORS). Server-side extraction is also the only thing that makes the V2 07:30 digest possible with no browser open. |
| D2 | **Spring AI 2.0.1** as the provider abstraction for LLM + TTS | The JVM's LiteLLM analogue, and it compiles against Spring Boot 4.1.1 exactly (verified: its starters declare `spring-boot-starter:4.1.1`). Swap provider = dependency + config change, no code change. |
| D3 | Identity = **`X-Device-Id` header**, user row auto-provisioned | PRD open question #2. No login, no JWT, no password reset for MVP. |
| D4 | Audio in **Cloudflare R2** (S3-compatible), served as presigned URLs | Survives VPS rebuild, offloads bandwidth from the box. |
| D5 | Job queue via **`SELECT … FOR UPDATE SKIP LOCKED`**, not advisory locks | Diverges from PRD §8. See "Divergences" below. |

### On Spring AI and provider swapping (answering the LiteLLM question)

Spring AI gives us most of what LiteLLM gives a Python project, but not all of it:

- **Summarization — fully covered.** Code targets `ChatClient`/`ChatModel`. Switching
  Anthropic → OpenAI → Ollama → Bedrock is a starter swap plus `application.properties`.
  Starters exist for anthropic, openai, azure-openai, bedrock, vertex-ai-gemini, mistral,
  deepseek, ollama, and ~15 more.
- **TTS — partially covered.** The `org.springframework.ai.audio.tts.TextToSpeechModel`
  interface exists in `spring-ai-model:2.0.1` (verified in the jar), but **there is no
  ElevenLabs starter** — only OpenAI implements it in-tree. So we write
  `ElevenLabsTextToSpeechModel implements TextToSpeechModel` over WebClient. We still get
  the swap-ability, and OpenAI TTS becomes a zero-code fallback.
- **What it does *not* give us** that LiteLLM does: a proxy process, cross-provider spend
  tracking, virtual keys, provider-level fallback routing. If we want fallback (Anthropic
  down → OpenAI), we write that ourselves — it's ~20 lines around `ChatClient` since both
  sit behind one interface.

Model choice for MVP: **Claude Haiku 4.5** (`claude-haiku-4-5`) — $1/$5 per MTok, 200K
context so a full article fits with no chunking. At the PRD's 500 articles/month
(~2.7K in / ~700 out each) that is **~$3/month**, comfortably inside the <$30 budget.
ElevenLabs is the real cost driver, not the LLM.

### Divergences from the PRD (each gets a devlog entry)

1. **Queue mechanism.** PRD says "Spring Scheduler + PostgreSQL advisory lock". We use
   `SELECT … FOR UPDATE SKIP LOCKED` instead — it's the idiomatic Postgres job-queue
   pattern, gives per-row concurrency rather than one global lock, and releases naturally on
   transaction end so a crashed worker doesn't strand the lock.
2. **`WebClient` without WebFlux-as-a-server.** We add `spring-boot-starter-webflux` for
   `WebClient` only; the server stays Servlet/MVC (`spring-boot-starter-webmvc`, already
   present). This is supported and intentional — do not let it drift into a reactive
   rewrite.
3. **Extraction library.** PRD hedges "Mozilla Readability (via Java port or jsoup +
   heuristics)". Settled: jsoup + heuristics (D1).
4. **Schema additions.** The PRD's `Bookmark` has no retry bookkeeping, but §7 requires "max
   3 retries". Adding `attempts`, `last_error`, `next_attempt_at` in the first migration so
   we don't migrate twice.

---

## Scope assessment

Backend MVP is the product minus the UI. Nine stages; stages 1–6 are the shippable MVP.

| Stage | Deliverable | Risk |
|-------|-------------|------|
| 0 | Repo foundations, docs, devlog, git | none |
| 1 | Schema + entities + repositories | low |
| 2 | Identity, bookmark CRUD, error handling, SSRF guard | low |
| 3 | Pipeline state machine + queue worker | **high — architectural heart** |
| 4 | Article extraction | **high — quality determines everything downstream** |
| 5 | Summarization via Spring AI | medium |
| 6 | TTS + R2 storage + audio endpoint | medium |
| 7 | Deployment (Docker, Nginx, CI) | low |
| 8 | V2: digest schedule + Web Push | medium |

Stages 3 and 4 carry the risk. Stage 3 because everything hangs off the state machine;
stage 4 because bad extraction produces bad summaries and no amount of prompt work recovers
it.

---

## Stage 0 — Repo foundations

**The repo is not under git yet.** `git init`, `.gitignore` is already sensible, initial commit.

Create:

- `docs/IMPLEMENTATION_PLAN.md` — this plan, as the living project document
- `docs/CODING_STANDARDS.md` — see below
- `docs/devlog/README.md` — devlog format and rules
- `docs/devlog/0001-2026-08-22-foundations-and-stack-decisions.md` — D1–D5 and the four divergences
- `CLAUDE.md` — points future agents at the standards and devlog so they're actually followed

### Coding standards (content of `docs/CODING_STANDARDS.md`)

Explicitly per your ask — **no comment littering**:

- Comments explain **why**, never **what**. If a comment restates the code, delete the
  comment. If the code needs a comment to be readable, rename things instead.
- No Javadoc on self-evident members. Javadoc only on public API with non-obvious contracts
  (nullability, side effects, thread safety, units).
- No commented-out code, no `// TODO` without a ticket reference, no banner comments.
- Records for DTOs. Constructor injection only, no field `@Autowired`.
- Package-by-feature (`bookmark/`, `pipeline/`, `extraction/`), not package-by-layer.
- Entities never cross the controller boundary — map to records.
- No business logic in controllers; no HTTP types in services.
- `Optional` for return values, never for fields or parameters.
- Every external call gets an explicit timeout. No unbounded retries.
- Tests name the behaviour, not the method (`rejectsPrivateNetworkUrls`, not `testValidate`).
- Lombok limited to `@RequiredArgsConstructor`, `@Getter`, `@Builder`. No `@Data` on
  entities (breaks `equals`/`hashCode` with JPA).

### Devlog format (content of `docs/devlog/README.md`)

One file per meaningful decision or diversion, never one per commit:

```
docs/devlog/NNNN-YYYY-MM-DD-short-slug.md
```

```markdown
# NNNN — Title
Date: YYYY-MM-DD
Stage: <stage number/name>
Status: accepted | superseded by NNNN | reverted

## Context      — what forced a decision
## Decision     — what we chose
## Alternatives — what we rejected and why
## Consequences — what this costs us later
```

Written when: we diverge from the PRD, pick between real alternatives, hit a non-obvious
constraint, or revert something. **Not** written for routine feature work — the git history
already covers that.

---

## Stage 1 — Schema and persistence

`pom.xml`: add `spring-boot-starter-validation`.

Liquibase changesets in `src/main/resources/db/changelog/changes/` (master already
`includeAll`s them in filename order):

- `001-users.yaml` — `id`, `device_id` (unique, indexed), `email` nullable, `timezone`
  default `UTC`, timestamps
- `002-bookmarks.yaml` — PRD §9.1 fields plus `attempts int not null default 0`,
  `last_error text`, `next_attempt_at timestamptz`, `word_count int`, `content_hash`.
  Indexes: `(user_id, created_at desc)`, `(status, next_attempt_at)` for the queue poll,
  unique `(user_id, url) where deleted_at is null` for dedupe.
- `003-digest-schedules.yaml`, `004-push-subscriptions.yaml` — created now, used in stage 8.

Status/length columns are `varchar` + check constraint, not Postgres enum types — far easier
to extend via Liquibase.

Java, package-by-feature under `org.timmy.knowcommute`:

- `bookmark/Bookmark.java`, `BookmarkStatus.java`, `SummaryLength.java`, `BookmarkRepository.java`
- `user/DeviceUser.java`, `DeviceUserRepository.java`

`spring.jpa.hibernate.ddl-auto=validate` is already set, so a mismatch between entity and
changeset fails the context load — that is our schema test.

**Verify:** `./mvnw test` — `contextLoads` now proves Liquibase and JPA agree against a real
Postgres 17 via Testcontainers. Add repository tests for the dedupe constraint and the queue
poll index.

---

## Stage 2 — Identity and bookmark API

- `security/DeviceIdArgumentResolver` — `HandlerMethodArgumentResolver` resolving a
  `@CurrentUser DeviceUser` parameter from the `X-Device-Id` header; auto-provisions on
  first sight. Missing/malformed header → 401. One class to swap for JWT at V2.
- `bookmark/BookmarkController` — the four endpoints from PRD §10.2, records for
  request/response.
- `web/ApiExceptionHandler` — `@RestControllerAdvice` returning RFC 9457 `ProblemDetail`.
- `bookmark/UrlValidator` — **this is a security control, not a formatting check.** We fetch
  arbitrary user-supplied URLs from inside the VPS, which is textbook SSRF. Must reject:
  non-http(s) schemes; hosts resolving to loopback, link-local (`169.254.0.0/16` — cloud
  metadata), private ranges, and unique-local IPv6. Re-check after every redirect in stage 4,
  because DNS can rebind between validation and fetch.

New bookmarks are persisted `PENDING` and returned `201` immediately. No processing on the
request thread.

**Verify:** slice tests for the controller; unit tests for `UrlValidator` asserting
`http://169.254.169.254/latest/meta-data/` and `http://localhost:5432` are rejected.

---

## Stage 3 — Pipeline state machine (highest-value stage)

`pom.xml`: add `spring-boot-starter-webflux` (for `WebClient` — server stays MVC).

Design:

- `pipeline/PipelineStep` — `BookmarkStatus handles()`, `void execute(Bookmark)`. Stages 4,
  5, 6 each contribute one implementation. Stage 3 ships **stub implementations** so the
  whole machine is testable end-to-end before any external API exists.
- `pipeline/PipelineWorker` — `@Scheduled(fixedDelay)` poll:

  ```sql
  SELECT * FROM bookmarks
  WHERE status IN ('PENDING','SUMMARIZING','GENERATING_AUDIO')
    AND (next_attempt_at IS NULL OR next_attempt_at <= now())
    AND deleted_at IS NULL
  ORDER BY created_at
  FOR UPDATE SKIP LOCKED
  LIMIT :batch
  ```

- `pipeline/RetryPolicy` — max 3 attempts, exponential backoff into `next_attempt_at`,
  `last_error` recorded, terminal `FAILED`.

Three rules that decide whether this works:

1. **Each step commits its own transition.** A crash mid-pipeline resumes at the next step
   rather than restarting from `PENDING` and re-spending AI money.
2. **No external call inside a transaction.** Fetch/summarize/TTS happen outside; only the
   status write is transactional. Holding a pooled connection through a 60s TTS call
   exhausts the pool at trivial load.
3. **Idempotency by `content_hash`.** If the extracted text hashes to something we already
   summarized, reuse it. Cheapest possible cost control.

Concurrency: bounded worker pool, and a per-user in-flight cap so one user pasting 50 URLs
can't monopolise the queue. This is also where PRD §13's "5 articles/day" rate limit lands.

**Verify:** integration test driving a bookmark `PENDING → READY` through stubs; a test where
a step throws three times and lands `FAILED` with `last_error` set; a test that two workers
polling concurrently never claim the same row.

---

## Stage 4 — Article extraction

`pom.xml`: add `org.jsoup:jsoup:1.23.1`.

- `extraction/ArticleFetcher` — WebClient with connect/read timeouts, a response size cap,
  a redirect limit, and a real User-Agent. Re-runs `UrlValidator` on each redirect hop.
- `extraction/ArticleExtractor` — jsoup: prefer `<article>`, `[role=main]`, `<main>`, else
  score candidate blocks by text-to-link density; strip `nav/aside/footer/script/style/form`.
- `extraction/ArticleMetadata` — title (`og:title` → `<title>`), source domain, word count,
  read time.
- Fallback per PRD §13: if extraction yields below a word threshold, fall back to whole-body
  text and flag it rather than failing.

**Tests use saved HTML fixtures under `src/test/resources/fixtures/`, never the network.**
Grab 6–8 real pages (a news site, a blog, an HN-linked long-form piece, a paywalled page, a
JS-heavy SPA shell) and assert on extracted word counts and titles. The SPA shell is the
honest one — it will extract nothing, and the test should assert we degrade rather than
crash.

---

## Stage 5 — Summarization

`pom.xml`: add `spring-ai-bom:2.0.1` to `dependencyManagement`, then
`spring-ai-starter-model-anthropic`.

- `summary/SummarizationService` — wraps `ChatClient`. Takes extracted text + `SummaryLength`,
  returns summary text.
- Prompt as a versioned resource file, not an inline string — prompts change more often than
  code and belong in diffs you can read.
- Length mapping: SHORT ~300 words, MEDIUM ~750, LONG ~1500 (roughly 2/5/10 min narrated).
- Guard very long articles against the context window; truncate with a logged warning rather
  than failing.
- Config-driven model id so provider swap needs no recompile.

**Verify:** unit tests with a stubbed `ChatModel` (no API key, no network). One manually-run
integration test, `@Disabled` by default, for real-provider smoke checks.

---

## Stage 6 — TTS and audio storage

`pom.xml`: add `software.amazon.awssdk:s3:2.54.2`.

- One `TextToSpeechModel` implementation over WebClient, streaming the MP3 body rather than
  holding it in heap. **Provider undecided** — the PRD names ElevenLabs, but it is being
  re-evaluated on cost (see devlog 0004). Code targets the Spring AI interface, so the
  choice is a class swap, not a rewrite.
- `storage/AudioStorage` + `R2AudioStorage` — S3 client with R2's endpoint override.
- `GET /api/v1/audio/{bookmarkId}` — `302` to a short-lived presigned URL. Keeps audio
  bandwidth off the VPS entirely, which is the whole point of D4.
- Duration: derive from MP3 frame headers.
- `storage/RetentionJob` — `@Scheduled` daily, deletes objects older than 30 days and nulls
  the bookmark's audio fields.

**Verify:** WebClient tests against MockWebServer; R2 tests against an S3-compatible
Testcontainer (MinIO/LocalStack) — no live R2 in CI.

---

## Stage 7 — Deployment

Target host is a **Contabo VPS**, not the Hetzner box named in PRD §8.

`Dockerfile` (layered jar, JRE 21), `docker-compose.yml` (app + Postgres 17), Nginx config
for SSL termination and proxying, GitHub Actions running `./mvnw verify`. Secrets via env
vars — never in `application.properties`. `spring-boot-starter-actuator` is already present;
expose `/actuator/health` for the compose healthcheck.

---

## Stage 8 — V2: digest and push

`pom.xml`: add `nl.martijndwars:web-push:5.1.2`.

`digest/DigestScheduleController` + `DigestWorker` evaluating per-user cron in the user's own
timezone (store `next_run_at` in UTC, compute in `ZoneId`). VAPID keys from env.
`push/PushService` prunes subscriptions on `410 Gone`. Endpoints per PRD §10.2.

---

## Verification (whole project)

- `./mvnw verify` — needs Docker running for Testcontainers (confirmed running locally).
- `./mvnw spring-boot:test-run` or `TestKnowCommuteApplication.main()` boots the app against
  a throwaway Postgres with no local DB install.
- Manual end-to-end once stage 6 lands:
  ```
  curl -XPOST localhost:8080/api/v1/bookmarks -H 'X-Device-Id: <uuid>' \
       -H 'Content-Type: application/json' -d '{"url":"...","summaryLength":"MEDIUM"}'
  curl localhost:8080/api/v1/bookmarks -H 'X-Device-Id: <uuid>'   # poll to READY
  curl -L localhost:8080/api/v1/audio/<id> -o out.mp3 && afplay out.mp3
  ```

### On the PRD's success metrics

§3's targets are mostly unmeasurable from the backend: "% of articles consumed" needs
playback telemetry we aren't building, and DAU/MAU needs analytics. "99% uptime" and "100
concurrent users" are aspirational for a single unmonitored VPS. Recommend treating §3 as
narrative for the portfolio README, and tracking what we can actually observe: pipeline
success rate, p95 end-to-end processing time against the <30s/<60s targets in §7, and
monthly API spend.

---

## Execution order

Stage 0 first (git + docs + devlog, so decisions are captured from the start), then 1 → 2 →
3. Stage 3's stubs let stages 4/5/6 be built and tested independently — they can be done in
any order, or in parallel, once the state machine exists.
