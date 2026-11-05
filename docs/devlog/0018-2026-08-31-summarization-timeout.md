# 0018 — The summarization timeout has to ride on the call

Date: 2026-08-31
Stage: 5 — Summarization (revisited)
Status: accepted

## Context

Summarizing a 2,036-word article at LONG failed after 68 seconds with
`OpenAIInvalidDataException: Error reading response`, caused by
`StreamResetException: stream was reset: CANCEL`. The stored `lastError` read like a network
fault, which is what made it worth writing down.

It was not a network fault. The chat model is autoconfigured from `spring.ai.openai.chat.*`
and no timeout was set anywhere, so it took openai-java's 60-second default. Extraction took
about eight seconds; the completion ran the remaining sixty and was cancelled. OpenRouter
serves HTTP/2, where an expired request timeout cancels the stream rather than closing a
socket, so the client's own timeout arrives dressed as a peer reset. `BookmarkPipeline.describe()`
then records the root cause, which is the reset rather than the timeout that caused it.

This is the chat-side twin of the speech bug `SpeechTimeoutTest` already documents, and it was
not compensated for anywhere. Probed against these versions, on the wire:

| Configuration | `X-Stainless-Timeout` |
|---|---|
| nothing set | 60 |
| `spring.ai.openai.chat.timeout=10m` | 60 |
| `spring.ai.openai.chat.options.timeout=10m` | 60 |
| hand-built `OpenAiChatModel` with `options.timeout` | 600 |
| per-call `.options(OpenAiChatOptions.builder().timeout(...))` | 600 |

`spring.ai.openai.chat.timeout` advertises a 60s default in its configuration metadata, which
is what makes it look like the knob. It is not.

## Decision

`SummarizationService` sets the timeout on every call it makes, from
`readcast.summarization.timeout` (default 5m), on both the generate and the tighten call.

Options set per call reach the wire, and merge over the configured model and token limit
rather than replacing them — asserted, because a per-call option that replaced them would send
a request with no model and OpenRouter answers that with a 400.

`SummarizationTimeoutTest` asserts both halves on the wire: ours sends the configured value,
autoconfiguration alone still sends 60. The second assertion fails when upstream fixes the
bug, which is the signal to delete the workaround.

## Alternatives

**Build the chat model by hand, as `SpeechModelConfig` does for TTS.** Works — the probe
confirms 600 on the wire — and it is the established pattern in this repo. Rejected because it
costs more than it buys here. A hand-built `ChatModel` bean competes with the autoconfigured
one, so the autoconfiguration has to be switched off, and that takes with it the
one-property move to Anthropic that `spring.ai.anthropic.*` is kept for. The per-call option
gets the same result without touching bean wiring.

**Raise the timeout only, and leave the requested summary length alone.** Would have stopped
this bookmark failing while leaving the reason it took so long in place. See 0019: the article
was being asked for a summary 74% of its own length. Both changes are needed — this one
because a genuinely long article at LONG can still exceed 60 seconds.

## Consequences

The timeout is set in application code rather than configuration, so it is invisible to anyone
reading `application.properties` looking for it. The property comment points at the service.

The option is OpenAI's. Setting `spring.ai.model.chat=anthropic` now needs this line changed
too — a one-line cost, where the hand-built alternative would have removed the escape hatch
entirely. The comment on `timeoutOption()` says so at the point where it matters.

Two calls can now take five minutes each. That fits inside the 45-minute pipeline lease set in
0017, but the two numbers are related and neither says so.
