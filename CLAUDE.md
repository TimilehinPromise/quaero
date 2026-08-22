# know-commute

Backend for **ReadCast** — paste a URL, get a podcast-style audio summary.
Spring Boot 4.1.1, Java 21, PostgreSQL 17, Liquibase.

**This repository is backend only.** No React, no PWA, no service worker. The frontend is a
separate concern that consumes the API defined here.

## Read before changing anything

| Document | What it is |
|---|---|
| `docs/ReadCast_PRD.md` | Product requirements. The *what*. Not a build order. |
| `docs/IMPLEMENTATION_PLAN.md` | Staged build order, stage 0–8. The *how*. |
| `docs/CODING_STANDARDS.md` | **Binding.** Reviewers reject on these. |
| `docs/ARCHITECTURE_PIPELINE.md` | How the bookmark pipeline works. Read before touching `pipeline/`. |
| `docs/devlog/` | Dated decision history. The *why*. |

Where the PRD and `docs/IMPLEMENTATION_PLAN.md` disagree, the plan wins and the divergence is
recorded in `docs/devlog/`.

## Standards, in brief

Full rules in `docs/CODING_STANDARDS.md`. The ones most often violated:

- **Comments explain _why_, never _what_.** No comment restating the code, no banner
  comments, no commented-out code, no Javadoc repeating a signature. If code needs a comment
  to be readable, rename things instead.
- Package by feature (`bookmark/`, `pipeline/`, `extraction/`), never by layer.
- Records for DTOs. Constructor injection only. Entities never reach a controller.
- Liquibase owns the schema; `ddl-auto=validate` stays. Applied changesets are immutable.
- Every external call has an explicit timeout, and **never runs inside a transaction**.
- No network in tests. Real Postgres via Testcontainers, never H2.

## Architecture notes that are easy to get wrong

- **WebFlux is on the classpath for `WebClient` only.** The web layer is Servlet/MVC
  (`spring-boot-starter-webmvc`). Do not start a reactive rewrite.
- **The queue uses `SELECT … FOR UPDATE SKIP LOCKED`,** not advisory locks. PRD §8 says
  otherwise; see `docs/devlog/0001-2026-08-22-foundations-and-stack-decisions.md`.
- **The backend fetches arbitrary user-supplied URLs.** SSRF validation (scheme allowlist,
  private/loopback/link-local IP rejection) is a required control, re-run on every redirect.
- **LLM and TTS go through Spring AI interfaces** (`ChatClient`, `TextToSpeechModel`) so
  providers swap by config. Do not call a provider SDK directly.

## Devlog

Add an entry when you diverge from the PRD, choose between real alternatives, hit a
non-obvious constraint, or revert a decision. Do **not** add one per commit — format and
rules in `docs/devlog/README.md`.

## Commands

```bash
./mvnw verify                  # full build + tests (needs Docker for Testcontainers)
./mvnw spring-boot:test-run    # run locally against a throwaway Postgres, no local DB needed
```
