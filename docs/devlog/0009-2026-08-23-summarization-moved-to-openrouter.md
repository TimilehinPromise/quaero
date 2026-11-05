# 0009 — Summarization moved to OpenRouter's free tier

Date: 2026-08-23
Stage: 5 — Summarization (revisited)
Status: accepted

Supersedes the model decision in devlog 0007, which itself superseded devlog 0001.

## Context

Devlog 0007 put summarization on `claude-sonnet-5` at ~$11.55/month. Two things then changed
the picture.

First, **Anthropic API billing is entirely separate from a Claude subscription.** The Console
API is pay-per-token and must be funded independently; a Pro or Max plan covers claude.ai and
Claude Code, not an application's API calls. So the $11.55 was real additional out-of-pocket
spend, not something an existing subscription absorbed.

Second, devlog 0008 removed TTS from the budget entirely by self-hosting Kokoro. With speech
free, the only remaining cost was the LLM — and the owner chose to remove that too.

## Decision

**Summarization goes through OpenRouter to `nvidia/nemotron-3-ultra-550b-a55b:free`.**
Verified against OpenRouter's model list before committing: prompt `0`, completion `0`,
context 1,000,000.

OpenRouter is OpenAI-compatible, so this reuses `spring-ai-starter-model-openai` — already on
the classpath for Kokoro — rather than adding anything.

**Chat and speech are now both "openai"-flavoured but point at different hosts.** Each model
type carries its own `base-url` and `api-key`
(`spring.ai.openai.chat.*` vs `spring.ai.openai.audio.speech.*`), so they coexist without
collision.

The Anthropic starter and configuration stay. Moving back is
`spring.ai.model.chat=anthropic` plus `ANTHROPIC_API_KEY`, no code change — which is the
provider abstraction from D2 doing exactly what it was chosen for.

## Alternatives

**Staying on Sonnet 5.** Better narration, and ~$11.55/month of genuinely new spend for a
portfolio project with no revenue. The owner's call.

**Haiku 4.5 as a middle ground.** ~$3.85/month. Still real money against a free option.

## Consequences

- **Narration quality is now unproven and probably worse.** Sonnet 5 was chosen in 0007
  specifically for narration quality; a free Nemotron is an unknown at this task. The prompt
  is the same, so this is a fair comparison to make once both have been heard.
- **Free-tier rate limits become the operational constraint, not cost.** OpenRouter caps free
  models per-minute and per-day. **The V2 scheduled digest generating a batch at 07:30 is
  exactly the shape that trips a daily cap** — this needs checking before stage 8, not after.
- **Article text goes to a third party under a permissive data policy.** OpenRouter free
  endpoints generally require opting into prompt logging and training. The content is public
  articles, so the exposure is low, but it is the user's reading list leaving the system.
- **Two things had to change that "OpenAI-compatible" did not cover**, both found by running
  it rather than reasoning about it:
  1. The TTS Basic-auth interceptor was a **global** `OpenAiHttpClientBuilderCustomizer` bean
     and applied to every OpenAI-flavoured client — it would have overwritten OpenRouter's
     bearer token with Kokoro's Basic header on every chat call. It is now scoped by host.
  2. The OpenAI starter also autoconfigures transcription, embedding, image and moderation
     models, each demanding its own credentials. Removing the global
     `spring.ai.openai.api-key` broke every `@SpringBootTest` until those were switched off
     with `spring.ai.model.*=none`.
