# 0026 — Kokoro word timestamps use a pinned /dev contract

Date: 2026-10-29
Stage: 6 — TTS and audio storage
Status: accepted

## Context

Word highlighting needs timings produced during synthesis, but Spring AI's
`TextToSpeechModel.call` exposes only audio bytes. Kokoro exposes captions through
`/dev/captioned_speech`, which upstream explicitly classifies as unstable compared with `/v1`.
The deployment previously followed the mutable `latest` image. Kokoro also normalizes caption text,
so caption strings cannot safely identify words in `BookmarkDetail.summaryText`.

## Decision

Call Kokoro's non-streaming captioned-speech route behind the application's
`CaptionedSpeechModel` boundary, with explicit connect and request timeouts. The local Compose image
is pinned to `ghcr.io/remsky/kokoro-fastapi-cpu:v0.9.1`; its response contract is base64 `audio`,
`audio_format`, and `timestamps[]` containing `word`, `start_time`, and `end_time`.

Persist version 1 JSON at the deterministic key `<audio-key>.timestamps.json`. Exact-match anchors
and monotonic gap alignment associate captions with UTF-16 ranges in the stored transcript. Chunk
offsets are the sums of the exact MP3 frame durations retained by `Mp3Join`, not the rounded
`audioDurationSec`. Audio and sidecar use the same retention and deletion operation.

## Alternatives

Forced alignment with another speech model would add a second expensive provider and was rejected.
The stable OpenAI-compatible speech route remains insufficient because it has no word timings.
Exact caption-string matching was rejected because number expansion and acronym pronunciation
overrides change the text. A database word list or presigned timing URL would enlarge bookmark
responses and make offline caching depend on an expiring credential.

## Consequences

Upgrading Kokoro now requires checking the `/dev` response and running the disabled live smoke test
when credentials are available; contract or decode failures stop audio from advancing to READY.
Existing READY MP3s remain playable but return `word-timestamps-not-found` until explicitly
regenerated. There is no startup backfill. Alignment is deterministic and handles normalization
between exact anchors, but heavily rewritten caption text without useful anchors remains an
approximation supplied by the TTS provider rather than a new forced-alignment guarantee.
