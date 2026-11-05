# 0008 — Self-hosted TTS and audio storage

Date: 2026-08-23
Stage: 6 — TTS and audio storage
Status: accepted

Supersedes the TTS half of devlog 0004, which deferred the provider.

## Context

Devlog 0004 left the TTS provider open on cost, and devlog 0007 tightened the budget further
by moving summarization to Sonnet 5 (~$11.55/month, leaving ~$18 of the PRD's $30). The owner
chose to self-host **hexgrad/Kokoro-82M**, which removes per-character billing entirely.

## Decision

**Kokoro runs behind `remsky/Kokoro-FastAPI`, which exposes an OpenAI-compatible
`/v1/audio/speech`.** So the provider is Spring AI's stock
`spring-ai-starter-model-openai` with `spring.ai.openai.base-url` pointed at the container —
**no custom `TextToSpeechModel` at all.**

Verified against `spring-ai-openai-2.0.1.jar` before committing to it, because one detail
decides the whole approach:

```
OpenAiAudioSpeechModel implements TextToSpeechModel
  .voice(String)           -> "af_bella"   not a closed enum
  .responseFormat(String)  -> "mp3"
  .model(String)           -> "kokoro"
  .baseUrl(String)         -> http://kokoro:8880
```

Had `voice` been an enum of OpenAI's own voices, none of this would work.

Both starters coexist via `spring.ai.model.chat=anthropic` and
`spring.ai.model.audio.speech=openai`, so the OpenAI starter contributes speech only and never
competes for the `ChatModel` bean.

**The pipeline lease went from 5 minutes to 20.** Kokoro on CPU takes minutes per article. A
lease shorter than the slowest step means the row reappears mid-synthesis and a second worker
starts the same job — double CPU on a box that has little to spare. This is the first time a
step's real duration has constrained the queue design.

**Audio duration is read from MP3 frame headers** (`Mp3Duration`) rather than pulling in an
audio library for one number.

**The R2 client is built lazily**, on first use.

## Alternatives

**Writing an `ElevenLabsTextToSpeechModel`**, as the implementation plan assumed. Unnecessary
once the provider speaks the OpenAI audio API — and it would have been a hand-maintained HTTP
client for no gain.

**Constructing the S3 client eagerly.** This is what was written first, and it broke every
`@SpringBootTest`: the AWS SDK validates credentials in the constructor and throws
`Access key ID cannot be blank`. That is not merely a test problem — **the application could
not boot at all without R2 credentials**, which is an unreasonable price for a feature most
local work never touches. Lazy construction means the app starts and a missing key surfaces
as a clear `AudioStorageException` when audio is actually generated.

**Estimating duration from file size and bitrate.** Correct for CBR and silently wrong for
VBR.

## Consequences

- **TTS now costs CPU, not money.** The ~$18/month left after Sonnet 5 is no longer needed for
  speech. Kokoro needs ~1.5 GB RAM; whether the Contabo box has headroom alongside Postgres
  and the JVM is unverified and should be checked before deploying.
- **Synthesis latency is now the pipeline's slowest step by far** and is unmeasured. The 20m
  lease is a guess sized to be generous. Once real timings exist it should be tuned — too long
  and a crashed worker's bookmark stalls for 20 minutes.
- **Nothing tests against a real Kokoro.** `AudioGenerationStepTest` stubs `TextToSpeechModel`.
  The first live run is the first real test of the integration, as with the prompt in 0007.
- **`compose.yaml` pins `kokoro-fastapi-cpu:latest`**, which is not reproducible. Fine for
  local development, wrong for stage 7 — pin a digest there.

## Verification worth noting

Two bugs were caught by testing against real things rather than mocks:

1. **Presigned URLs 404'd against MinIO.** The client wrote path-style while the presigner
   signed virtual-host style (`bucket.host/key` vs `host/bucket/key`). A mocked S3 would have
   returned a happy answer. `presignedUrlActuallyDownloadsTheObject` performs a real HTTP GET
   against the signed URL, which is the only assertion that could have caught it. The same bug
   would have hit R2, which also uses an endpoint override.
2. **Duration was exactly half.** The first `Mp3Duration` used MPEG-1 tables for everything.
   Kokoro emits 24 kHz, which is **MPEG-2** — different bitrate table, half the samples per
   frame. The fixture is a real `ffmpeg`-generated tone of known length; a hand-written byte
   array would have encoded my own misunderstanding. Both MPEG-1 (44.1 kHz) and MPEG-2
   (24 kHz) fixtures are now asserted.
