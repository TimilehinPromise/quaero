# 0012 — Word ceilings enforced in code, and the cache that masked prompt fixes

Date: 2026-08-23
Stage: 5 — Summarization
Status: accepted

## Context

Two findings from the first live model shoot-out forced this.

**Every free model overshoots a word target.** Same article, same prompt across every
available free model: 1.35x to 3.86x over. Rewriting the prompt — ceiling not target, a
paragraph count as a structural proxy, the limit restated as the last line — brought the
winner from 2.42x down to 0.82x on one article. But reproduced against the Tokenized ep. 95
article at MEDIUM (750 words), the *same* hard-ceiling prompt still came back at 1.26x and
1.16x. The mechanism is visible in the output: the model obeys the paragraph count, writes
one ~180-word paragraph per article section, and ignores the ceiling — **a model cannot count
its own words**, so "750 words" is a number it can never verify. No prompt change fixes that.

**The reuse cache was serving stale summaries.** `SummarizationStep` reuses summaries keyed
by `contentHash + summaryLength`. Any summary generated before a prompt improvement keeps
being served for identical articles forever, so "we fixed the prompt but medium still runs
long" can be the cache, not the model.

Also ruled out by experiment: `google/gemma-4-31b-it:free` as an alternative model — six
attempts over seven minutes, all HTTP 429, rate-limited upstream at Google AI Studio's shared
free pool. Its own error message names the escape hatch (attach your own Google AI Studio key
under OpenRouter integrations for a private quota), but a model that cannot be reached cannot
be built on.

## Decision

**The ceiling is enforced in code in `SummarizationService`, with a 1.30x tolerance.**

1. Generate. If word count ≤ target × 1.30, accept — normal variation should not cost a retry.
2. Over the tolerance: one corrective call. The model gets its own over-long text back, its
   actual word count, and the ceiling restated. Cheaper than resending the article, and the
   ask — compress what you wrote — is identical either way.
3. Still over: truncate at a sentence boundary so the narration never ends mid-thought. This
   is the only step that *guarantees* the ceiling.

**The reuse cache goes through the same gate.** A cache hit whose stored text exceeds the
tolerance is regenerated, not served. No schema change, no migration: over-long rows clean
themselves up lazily on next encounter.

## Alternatives

**Prompt-only iteration.** Already at its limit — the hard-ceiling prompt is a large
improvement and stays, but the overshoot mechanism (section-per-paragraph) survives it. A
prompt can shrink the violation, never prevent it.

**Truncate immediately, skip the corrective retry.** Simpler and guaranteed, but it throws
away a chance to compress *well*: a tightened rewrite keeps the article's full arc in fewer
words, while truncation just amputates the tail. One extra call on overshoot is a fair price;
the fallback still bounds the outcome.

**Add a prompt version to the reuse key** (schema column, so a prompt change invalidates old
summaries). Rejected as disproportionate: it forces a migration and eager regeneration of
every cached row whenever the prompt moves, to solve a problem the ceiling gate already
handles lazily. If summaries are ever invalidated for a *quality* reason rather than length,
this becomes worth revisiting.

**Retry until compliant instead of once.** Unbounded calls against a rate-limited free tier
is how a digest batch eats the daily cap. One correction, then the deterministic fallback.

## Consequences

- **Length is now bounded by construction, not by prompt luck.** Worst case per summary is
  two model calls plus a clean truncation; best case is unchanged. A future model swap
  (including one as sloppy as the reasoning-leaking candidate from the shoot-out) cannot push
  an over-long episode to TTS.
- **An overshooting article costs two calls.** On OpenRouter's free tier, retries count
  against the same per-minute and per-day caps devlog 0009 flagged; a corpus of long articles
  at LONG could approach them. Watch the warn logs for retry frequency.
- **Truncation is lossy by design.** A model that ignores the correction loses its ending —
  conclusions usually live there. The warn log (`still over the ceiling; truncating`) is the
  signal to notice if this path ever fires in practice.
- **The gate checks length, not content.** It does not catch a model leaking its chain of
  thought or markdown into the narration — the failure mode that disqualified one candidate.
  That sanity check is still unbuilt and now sits naturally next to the word count.
- **Tolerance means some episodes legitimately run up to 30% over target.** That is the
  owner's accepted trade-off between length discipline and retry churn.
