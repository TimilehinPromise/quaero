# 0004 — TTS provider deferred; hosting is Contabo

Date: 2026-08-22
Stage: 3 — Pipeline state machine (decision affects stage 6)
Status: accepted

## Context

PRD §8 names ElevenLabs Turbo v2.5 for TTS and a Hetzner VPS for hosting. Both were stated
as settled; neither is.

Hosting is **Contabo**, not Hetzner. The R2 account for D4 exists.

TTS provider is **open**, on cost grounds. This matters more than it looks, because TTS —
not the LLM — is what the <$30/month budget in PRD §7 actually turns on.

## The number that decides it

Synthesis is billed per character, so the figure to shop with is characters per month, not
articles:

```
MEDIUM summary target        750 words   (SummaryLength.MEDIUM)
English averages           ~5.7 chars/word incl. spaces
                          ────────────────
per article                ~4,300 characters
× 500 articles/month (PRD §7)
                          ────────────────
                           ~2.2M characters/month
```

Against a <$30/month **total** budget, of which summarization already takes ~$3
(devlog 0001), leaving roughly $25 for speech:

- at ~$15 per 1M characters (typical neural tier) → **~$33/month — over budget on TTS alone**
- at ~$4 per 1M characters (basic/standard voices) → ~$9/month, comfortable
- self-hosted on the Contabo box → $0 marginal, CPU time only

**These per-character rates are from memory and are not verified.** They are order-of-magnitude
only, for sizing the problem. Actual provider pricing must be checked at stage 6 before any
commitment — do not quote these figures to anyone.

## Decision

Defer the provider choice to stage 6. Stage 6 ships exactly one implementation of Spring AI's
`TextToSpeechModel`, and nothing above that interface knows which provider it is.

## Consequences

- The cost risk is real and lands entirely on this choice. If the neural-tier estimate above
  is close, the PRD's §7 budget and its §6.2 `SummaryLength` options are in tension: LONG
  (~1500 words) doubles the character count. Shortening the default summary is a cheaper
  lever than switching provider.
- **Self-hosting deserves a serious look**, and the architecture happens to favour it. The
  pipeline is asynchronous and the V2 digest runs on a schedule, so synthesis latency is
  nearly free — a CPU-only model taking 30s per article is fine when nothing is waiting on it.
  That is not true of most products, and it is worth exploiting given the VPS already exists.
  The cost then moves from per-character billing to Contabo CPU and disk.
- Deferring costs us nothing structurally, because D2 already put TTS behind an interface.
  This entry exists so the deferral is visible rather than looking like an oversight.
