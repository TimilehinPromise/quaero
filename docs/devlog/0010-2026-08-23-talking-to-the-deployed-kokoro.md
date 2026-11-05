# 0010 — Talking to the deployed Kokoro

Date: 2026-08-23
Stage: 6 — TTS (revisited against a real deployment)
Status: accepted

## Context

Devlog 0008 wired up Kokoro against nothing — the tests stubbed `TextToSpeechModel`, and it
said so. A Kokoro-FastAPI instance now exists at a `tts.` subdomain, behind Cloudflare
with HTTP Basic auth on openresty, because the main app has no authentication of its own.

Pointing Spring AI at it surfaced three things that "OpenAI-compatible" did not cover.

## What was actually wrong

**1. The base URL must include `/v1`.** Spring AI appends only `/audio/speech`, so a bare host
yields `/audio/speech` → 404. `KOKORO_BASE_URL` is now `https://…/v1`.

**2. Basic auth cannot go through `customHeaders`.** Spring AI always sends
`Authorization: Bearer <api-key>`, and the options' `customHeaders` **appends** rather than
replaces. Both headers go out, and openresty rejects a duplicate `Authorization` with **400**.

The 400 was misleading — it looks like a malformed request, so it was chased as a body or path
problem first. Isolating it took pointing the client at a local capture server to read the
exact bytes it sends, then reproducing each hypothesis with curl:

```
correct path + single Basic header     -> 200
wrong path (/audio/speech)             -> 404
correct path + DUPLICATE Authorization -> 400   <- the actual cause
```

The fix is an OkHttp interceptor via `httpClientBuilderCustomizer`, which uses OkHttp's
`header()` (replaces) rather than `addHeader()` (appends). That is `TtsAuthConfig`.

**3. The interceptor had to be scoped by host.** It is a global bean applying to every
OpenAI-flavoured client, and devlog 0009 put chat on OpenRouter — so unscoped it would have
overwritten OpenRouter's bearer token. It now only rewrites requests whose host matches the
speech base URL.

`TtsAuthConfig` is conditional on `readcast.tts.basic-auth-user`. In production the app
reaches Kokoro by container name on the internal network, so it never activates; the auth
exists for external access, which is how a laptop reaches it during development.

## Measurements

First real numbers, from the deployment rather than an estimate:

```
output format   24 kHz mono 128 kbps MP3   (MPEG-2 — the path fixed in 0008)
speed           0.75x realtime

SHORT   300 words -> ~1.7 min audio, ~1.3 min to synthesise
MEDIUM  750 words -> ~4.4 min audio, ~3.3 min to synthesise
LONG   1500 words -> ~8.7 min audio, ~6.5 min to synthesise
```

**This retroactively justifies the lease change in 0008.** At the original 5-minute lease, a
LONG summary taking ~6.5 minutes would have had its row reclaimed mid-synthesis on every
single run, burning double CPU. 20 minutes gives roughly 3x headroom on the worst case.

It also confirms the MPEG-2 fix was necessary rather than theoretical: Kokoro really does emit
24 kHz, and `Mp3Duration` read genuine Kokoro output correctly in the smoke test.

## Consequences

- `KokoroSmokeTest` exists, `@Disabled`, runnable with `KOKORO_BASE_URL`, `KOKORO_USER` and
  `KOKORO_PASSWORD` set. It is the only test that touches a real provider.
- Credentials live in environment variables only. The pair used during development was shared
  over chat and **should be rotated**.
- The measurements are from one ~104-word sample on an idle server. Concurrency will change
  them, and `readcast.pipeline.concurrency=4` means four synthesis jobs could run at once on a
  box also running Postgres and the JVM. Unmeasured.
