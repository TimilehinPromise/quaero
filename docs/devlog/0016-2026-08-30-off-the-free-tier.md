# 0016 — Off the free tier, onto the same model

Date: 2026-08-30
Stage: 5 — Summarization (revisited again)
Status: accepted

Supersedes the model-tier decision in devlog 0009. The rest of 0009 — OpenRouter as the
gateway, the host-scoped auth interceptor, `spring.ai.model.*=none` — is unchanged and still
load-bearing, so that entry stays accepted rather than being marked superseded wholesale.

## Context

Devlog 0009 named the risk exactly: *"Free-tier rate limits become the operational constraint,
not cost."* It came true within an hour of the first real deployment, though not as a rate
limit — as capacity.

The first two bookmarks through the deployed pipeline both failed at SUMMARIZING, with two
different exceptions from the same call site:

```
OpenAIInvalidDataException: Error reading response
  caused by: InterruptedIOException: timeout
  caused by: StreamResetException: stream was reset: CANCEL

OpenAIInvalidDataException: `choices` is not set
```

Reproduced directly against OpenRouter with the same key and model:

```
HTTP 200 in 1.16s
{"error":{"message":"Upstream error from Nvidia: Service temporarily overloaded","code":502}}
```

**HTTP 200 with an error body and no `choices` array.** That is one condition wearing two
faces: OpenRouter either fails fast like this, or holds the connection open sending keepalive
whitespace until the client's timeout fires and cancels the HTTP/2 stream. Neither is a bug in
this application, and neither is an auth or deployment problem — both were ruled out first.

The thing that made 0009's decision look worse than a trade-off: **the account was never on the
free tier.** `GET /api/v1/key` reports `is_free_tier: false`, `limit: null`, lifetime usage
$32.86. The `:free` suffix was routing a funded account onto best-effort shared capacity for no
benefit it could not already afford.

## Decision

**Drop the suffix. `READCAST_SUMMARY_MODEL=nvidia/nemotron-3-ultra-550b-a55b`.**

Same model, same weights, paid endpoint. That is the entire point of choosing it over a
different model: the prompt work in devlog 0011 (proper-noun respelling) and 0012 (word
ceilings enforced in code) was tuned against these weights, and a different model would
invalidate both without notice.

At $0.50/M input and $2.20/M output, a typical article — roughly 7k input tokens and 2k output
for a LONG summary — costs about **$0.008**, or **~$4/month at 500 articles**. Against the
PRD's <$30 budget, and a third of devlog 0007's $11.55 estimate for `claude-sonnet-5`.

An environment variable, no code change and no image rebuild.

## Alternatives

**`nvidia/nemotron-3-super-120b-a12b`** at $0.085/$0.40 per million — about $0.70/month, five
times cheaper. Rejected for now purely because it is different weights, and the prompt has been
tuned twice against the 550B. Worth measuring if $4/month ever becomes a number that matters.

**Retry harder against the free endpoint.** Already three attempts on a 2-minute backoff. The
failure is capacity on somebody else's cluster with no signal about when it clears, so more
retries buy a queue that stalls silently instead of failing.

**Back to Anthropic.** Still one property away — `spring.ai.model.chat=anthropic` — and still
the reason the abstraction exists. Rejected as roughly three times the cost of the paid
Nemotron, and it reopens the narration-quality comparison 0009 left unresolved without anyone
having actually listened to the two side by side yet.

## Consequences

LLM cost is back. Roughly $4/month against 0009's $0. Kokoro still keeps TTS free, so this is
the whole recurring bill.

**The data-policy concern in 0009 narrows.** Paid endpoints do not require opting into prompt
logging and training the way free ones generally do. Article text still leaves the system to a
third party; the training exposure does not.

**0009's warning about the V2 digest tripping a daily cap is much weaker** — paid endpoints are
not subject to the free tier's daily limits — but it is not gone, and the check before stage 8
is still worth doing.

Narration quality is unchanged, because the model is unchanged. The comparison 0009 wanted
between Sonnet and Nemotron is still unmade.

**The failure mode is the part worth remembering.** A provider that answers HTTP 200 with an
error payload is invisible to any handling that keys off status codes, and the only symptom
here was a deserialization complaint about a missing field. If this shape recurs with another
provider, the answer is to read the body, not the status. It was also only diagnosable at all
because devlog 0014's root-cause logging had shipped in the same release — without it,
`lastError` would have read `OpenAIInvalidDataException` and stopped there.
