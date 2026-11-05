# 0023 — The output token limit has to cover the model's reasoning

Date: 2026-09-30
Stage: 5 — Summarization (revisited)
Status: accepted

## Context

A model comparison for the listener-lens feature ran each summary with the finish reason and
token usage recorded, and several calls came back with `finish_reason: length` and an empty or
half-written narration. Nemotron 3 Ultra, Kimi K2.6 and DeepSeek V4 Pro are reasoning models:
they think before they write, and that thinking is billed as output and counted against
`max_tokens`. Nothing in the reply marks it — the narration is simply short or missing.

Measured across 12 summaries each (two calls per summary):

| Model | Reasoning tokens in one call, max |
|---|---|
| Nemotron 3 Ultra (production) | 4,732 |
| DeepSeek V4 Pro | 8,192 — the whole limit, nothing written |
| Kimi K2.6 | 8,192 — the whole limit, nothing written |

Production ran with `max-tokens=4096`, so Nemotron can spend the whole budget thinking. An empty
reply is exactly what commit b3e8061 ("Keep the draft when the tightening call comes
back empty") had to work around; this is the likely cause, though it was not confirmed against
production logs.

The worse failure is the partial one. `SummarizationService` read `.content()` and never looked
at why the model stopped, so a narration cut off mid-sentence passed the empty-reply check and
was stored and synthesised as if complete.

## Decision

**Raise the limit to 16384, overridable with `SUMMARY_MAX_TOKENS`,** for both the OpenAI-
compatible and the Anthropic configuration. It is a ceiling, not a spend: only tokens used are
billed.

**Read the finish reason on every call.** `LENGTH` (OpenAI-compatible) or `MAX_TOKENS`
(Anthropic) on the summary call fails the step with "hit the output token limit" so it retries
rather than storing a truncated narration. On the tightening call it keeps the draft, the same
way b3e8061 treats an empty reply — the draft is finished and the sentence-boundary cut after it
brings it under the ceiling.

## Alternatives

**Cap reasoning separately** through OpenRouter's `reasoning` request field, which
`OpenAiChatOptions.extraBody` could carry. It bounds cost and latency, but trades away the
quality the reasoning buys, and whether `extraBody` survives to the wire in Spring AI 2.0.1 is
unverified — 0018 found other options that silently do not. Worth revisiting if a
reasoning-heavy model is ever chosen; Kimi K2.6 used up to 22,000 reasoning tokens per summary.

**A larger limit alone.** Makes truncation rarer but leaves it silent when it happens.

## Consequences

- A model that reasons past 16k tokens still fails, now loudly, and retries at the same limit.
  If that shows up in `lastError`, raise `SUMMARY_MAX_TOKENS` or cap reasoning.
- OpenRouter may refuse a request the balance could not cover at the full ceiling, even though
  it bills only what is used. A low balance fails sooner than it would at 4096.
- Finish-reason names are provider strings, not an enum; a third provider needs its name added
  to `hitTokenLimit`.
