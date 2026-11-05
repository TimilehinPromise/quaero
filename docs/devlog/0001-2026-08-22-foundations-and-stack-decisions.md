# 0001 — Foundations and stack decisions

Date: 2026-08-22
Stage: 0 — Repo foundations
Status: accepted

## Context

`docs/ReadCast_PRD.md` is a product document covering the whole of ReadCast — backend, PWA,
and hosting. This repository is the **backend only**. Before writing code we needed to settle
where responsibility sits, and several PRD technical choices did not survive contact with
their constraints.

The repo at this point was a bare Spring Boot 4.1.1 scaffold on Java 21: one
`@SpringBootApplication`, an empty Liquibase master changelog, Testcontainers wiring, and
nothing else. It was not under version control.

## Decision

Six decisions, recorded together because they were taken as one set.

**D1 — Article extraction happens on the backend, using jsoup. Mozilla Readability is not
used anywhere in the product.**

**D2 — Spring AI 2.0.1 is the provider abstraction for both LLM and TTS.** Summarization
targets `ChatClient`/`ChatModel`; TTS targets `org.springframework.ai.audio.tts.TextToSpeechModel`.

**D3 — Identity is an `X-Device-Id` header.** The user row is auto-provisioned on first
sight. No login, no JWT, no password reset for MVP. Resolves PRD open question #2.

**D4 — Generated audio is stored in Cloudflare R2** (S3-compatible, AWS SDK v2) and served as
short-lived presigned URLs via a `302` from `GET /api/v1/audio/{bookmarkId}`.

**D5 — The job queue uses `SELECT … FOR UPDATE SKIP LOCKED`,** not Postgres advisory locks.

**D6 — `WebClient` comes from `spring-boot-starter-webflux`, but the server stays
Servlet/MVC.** `spring-boot-starter-webmvc` remains the web layer.

## Alternatives

**D1 — frontend extraction with Readability.js (the PRD's stated preference).** Rejected on
two counts. A PWA cannot `fetch()` a third-party article's HTML: no site sends
`Access-Control-Allow-Origin`, so the browser blocks it, and Readability.js needs a DOM that
already contains the article. Working around that would mean the backend proxying raw HTML
anyway. More decisively, it breaks the V2 scheduled digest outright — at 07:30 there is no
browser open to run anything. A hybrid (accept either a URL or pre-extracted text) was also
considered and rejected as API surface we have no consumer for yet.

**D2 — the Anthropic Java SDK directly, or hand-rolled OpenAI calls over WebClient.** Both
work, but neither gives cheap provider swapping, which was the explicit requirement (raised
as "can Spring AI do what LiteLLM does for us"). Verified before committing: Spring AI 2.0.1
starters declare `spring-boot-starter:4.1.1` — an exact match for our parent, not a
compatibility gamble. Verified in `spring-ai-model-2.0.1.jar`: the
`org.springframework.ai.audio.tts` package exists.

**D4 — the local VPS filesystem.** Simpler and free, but audio does not survive a VPS rebuild
and all playback bandwidth crosses the box.

**D5 — advisory locks, per PRD §8.** A `pg_advisory_lock` on a single queue key serialises
all workers behind one lock. `FOR UPDATE SKIP LOCKED` is the idiomatic Postgres job-queue
pattern, gives per-row concurrency, and releases on transaction end — so a worker that dies
mid-job does not strand a lock the way an unreleased session-level advisory lock does.

## Consequences

- **D1** puts extraction quality on us. jsoup + density heuristics will lose to Readability.js
  on some layouts, and will extract nothing from JS-rendered SPA shells. Stage 4 must treat
  degraded extraction as an expected outcome with a fallback, not an error.
- **D1** also makes the backend fetch arbitrary user-supplied URLs from inside the VPS, which
  is textbook SSRF. An allowlist-scheme + private-IP-range check is now a required security
  control, re-run on every redirect hop.
- **D2** does not give us everything LiteLLM does: no proxy process, no cross-provider spend
  tracking, no virtual keys, no built-in fallback routing. Provider fallback, if we want it,
  is ours to write — cheap, since both providers sit behind one interface.
- **D2** has a gap on TTS: **there is no ElevenLabs starter in Spring AI**, only OpenAI
  implements `TextToSpeechModel` in-tree. We write `ElevenLabsTextToSpeechModel` ourselves.
  The upside is that OpenAI TTS becomes a zero-code fallback.
- **D3** means a cleared browser or a new device is a new user with an empty library, and
  anyone holding a device id has full access to that library. Acceptable for a portfolio MVP;
  the resolver is isolated in one class so V2 can swap in JWT without touching controllers.
- **D4** adds credentials and a second failure mode before there are any users. CI tests
  against an S3-compatible container, never live R2.
- **D5** is a documented divergence from the PRD. Anyone reading PRD §8 will expect advisory
  locks and should be pointed here.
- **D6** creates a standing risk that someone sees WebFlux on the classpath and starts a
  reactive rewrite. The web layer is MVC. WebFlux is present for `WebClient` only.

Model choice for MVP is `claude-haiku-4-5` ($1/$5 per MTok, 200K context — a full article
fits without chunking). At the PRD's 500 articles/month this is roughly $3/month against a
<$30 budget. ElevenLabs, not the LLM, is the cost driver.

Also decided: retry bookkeeping (`attempts`, `last_error`, `next_attempt_at`) goes into the
first bookmarks migration. PRD §9.1 omits it but §7 requires max-3 retries, and adding it
later means a second migration for no reason.
