# 0007 — Summarization via Spring AI

Date: 2026-08-22
Stage: 5 — Summarization
Status: accepted

## Context

Stage 4 left bookmarks parked at `SUMMARIZING` with no handler. This is also the first step
that spends money per invocation, so the at-least-once delivery accepted in devlog 0005 stops
being an abstract property and starts having a bill attached.

## Decision

**Spring AI 2.0.1 (`spring-ai-starter-model-anthropic`), resolved clean against Boot 4.1.1.**
`SummarizationService` targets `ChatClient`; nothing above it names a provider. Model is
`spring.ai.anthropic.chat.options.model`, overridable by `READCAST_SUMMARY_MODEL`.

**The prompt is a resource file** (`prompts/summarise-article.st`), not an inline string.
Prompts change more often than code and belong in reviewable diffs.

**Content-hash reuse before generating.** `SummarizationStep` looks for an existing summary
with the same `content_hash` *and* the same `SummaryLength`, and reuses it rather than calling
the model. Matched across users: article text is public content, so there is nothing to leak,
and two people saving the same Hacker News link is the common case.

**Model is `claude-sonnet-5`**, chosen by the owner for narration quality over the cheaper
`claude-haiku-4-5` recorded in devlog 0001. **This supersedes the model line in 0001** — that
entry's ~$3/month figure no longer applies. Revised estimate below.

**Overlong articles are truncated, not failed**, at `readcast.summarization.max-input-words`
(40,000), with a logged warning.

## Alternatives

**The Anthropic Java SDK directly.** Fewer layers, and it would have hardcoded the provider
into the one part of the system most likely to change — the owner explicitly asked for
LiteLLM-style swapping (devlog 0001, D2).

**Reuse keyed on URL rather than content hash.** Misses syndicated articles at different URLs
and, worse, would reuse a stale summary after a page is rewritten. The hash is of the text we
actually extracted, so it is exactly right by construction.

**Reuse scoped per user.** Safer-sounding and pointless: it would forgo most of the saving to
protect content that was public to begin with.

**Failing articles over the context budget.** A 40,000-word article is rare and a partial
summary beats a dead bookmark.

## Consequences

- **Reuse does not cover the crash case.** If a worker calls the model, gets a summary, and
  dies before committing, nothing was saved, so the reclaim regenerates and we pay twice.
  Bounded by `max-attempts`, and unavoidable without provider-side idempotency keys. The hash
  covers duplicate saves and re-adds after soft delete, which is the frequent case.
- **Summarization now costs roughly 3x what devlog 0001 assumed.** At Sonnet 5's $3/$15 per
  MTok and 500 articles/month (~2,700 input, ~1,000 output tokens each):

  ```
  input   1.35M tok  x  $3   =  $4.05
  output  0.50M tok  x  $15  =  $7.50
                               -------
                                $11.55 / month     (Haiku 4.5 would have been ~$3.85)
  ```

  Against the PRD's <$30 total that leaves **~$18 for TTS**, where before there was ~$26.
  Introductory pricing ($2/$10) applies through 2026-08-31, so early months run nearer $7.70.
  This tightens, but does not break, the budget — and it raises the stakes on the TTS
  decision deferred in devlog 0004. **These rates should be re-checked before relying on
  them.**
- **`spring.ai.anthropic.api-key` defaults to `not-configured`** so the context loads without
  a key. Tests never reach the network — `SummarizationServiceTest` drives a stub `ChatModel`,
  and `SummarizationStepTest` overrides the service. **Nothing verifies the real provider
  call.** The first live run is the first real test of the prompt, and prompt quality cannot
  be asserted in CI anyway.
- **`GENERATING_AUDIO` is now the terminal resting state** until stage 6.
