# QUAERO

Backend for [QUAERO](https://quaero.timmybotlabs.fyi/) — paste a URL, get a podcast-style audio
summary.

Java 21 · Spring Boot 4 · PostgreSQL 17. QUAERO fetches an article, works out what kind of page
it is, extracts the readable text (and what its figures say), compresses it into a
spoken-length narration with an LLM, synthesises speech with a self-hosted Kokoro instance,
and serves the MP3 with word-level timestamps. State lives in Postgres, so the pipeline
survives restarts and never pays for the same work twice.

**This repository is backend only.** The browser client is a separate concern that consumes
the API defined here.

## How a URL becomes audio

1. **Fetch & classify** — the page is fetched under SSRF controls and classified before
   extraction commits to a strategy: full extraction for prose-rich pages, a lowered floor for
   genuinely short ones, a hosted-reader fallback or metadata brief for JavaScript shells.
2. **Extract** — readable text with recommendation rails pruned; figures become `[Figure N]`
   placeholders, ranked so an optional vision model reads only the ones worth reading.
3. **Summarise** — an LLM writes spoken narration under a word ceiling derived from the
   article itself; if it overshoots it gets one explicit tightening pass, then a truncation at
   a sentence boundary.
4. **Speak** — Kokoro synthesises the narration in chunks cut on sentence boundaries, and the
   MP3 parts are joined without re-encoding. A reasoning model's hidden thinking counts
   against the output budget, so the finish reason is checked before trusting the text.

The full design notes are in [docs/ARCHITECTURE_PIPELINE.md](docs/ARCHITECTURE_PIPELINE.md).
The *why* behind each non-obvious decision — including the failures — is in
[docs/devlog/](docs/devlog/), 33 dated decision records.

## Quickstart

```bash
docker compose up -d        # Postgres 17 + Kokoro (CPU image; the first start is slow)
export SUMMARY_API_KEY=...  # any OpenAI-compatible gateway; OpenRouter by default
./mvnw spring-boot:run
```

```bash
curl -X POST localhost:8020/api/v1/bookmarks \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/some-article"}'

# poll until status is READY, then:
curl localhost:8020/api/v1/audio/{id} --output narration.mp3
```

## Configuration

Every secret is an environment variable; `src/main/resources/application.properties` carries
the full list, defaults, and the reason each default is what it is. The essentials:

| Env var | What it configures |
|---|---|
| `SUMMARY_API_KEY` / `SUMMARY_BASE_URL` | LLM gateway for summarization (OpenRouter by default) |
| `DB_HOST` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | Postgres |
| `R2_ENDPOINT` `R2_ACCESS_KEY` `R2_SECRET_KEY` `R2_BUCKET` | Object storage for audio (Cloudflare R2, S3-compatible) |
| `JINA_API_KEY` + `READER_ENABLED=true` | Optional hosted reader fallback for JS-rendered pages |
| `KOKORO_*` | Self-hosted Kokoro TTS endpoint and tuning |

## Deployment

The `Dockerfile` builds a layered image from the jar CI just verified — the pushed image is
the artifact the build ran against, never a second compile. `.github/workflows/build.yml`
verifies, pushes to GHCR, and triggers the Coolify deploy only behind a green build.
`compose.deploy.yaml` is the single-service deployment compose; Postgres, Kokoro and the
reverse proxy are pre-existing neighbours on the same host.

## Figure debug workbench

Run the backend with the builder-only viewer enabled:

```bash
READCAST_FIGURE_DEBUG_VIEWER_ENABLED=true ./mvnw spring-boot:test-run
```

Open `http://localhost:8020/debug/figures`. The viewer reads `target/figure-debug` directly and
shows every figure decision (selected, rejected with a reason, preserved with its markup) and
each insertion point in the narration.

For a remote dev deployment, set `READCAST_FIGURE_DEBUG_DIR=/data/figure-debug`,
`READCAST_FIGURE_DEBUG_VIEWER_ENABLED=true`, and `READCAST_FIGURE_DEBUG_VIEWER_TOKEN` to a private
password. The browser will prompt for Basic authentication; any username works. The named
`readcast-figure-debug` volume persists traces across redeploys. Keep the viewer disabled in production.

## Docs

- [docs/ReadCast_PRD.md](docs/ReadCast_PRD.md) — the original requirements doc. A point-in-time
  snapshot: the devlog records where reality diverged from it and why.
- [docs/ARCHITECTURE_PIPELINE.md](docs/ARCHITECTURE_PIPELINE.md) — queue semantics, the lease,
  the failure taxonomy. Read before touching `pipeline/`.
- [docs/devlog/](docs/devlog/) — the decision log: what was chosen, what was rejected, what it cost.

## Related writing

The build-in-public article series lives at https://writing.timmybotlabs.fyi/.

## License

[MIT](LICENSE)
