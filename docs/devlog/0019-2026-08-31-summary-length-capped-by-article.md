# 0019 — The requested summary length is capped by what the article can support

Date: 2026-08-31
Stage: 5 — Summarization (revisited)
Status: accepted

## Context

`SummaryLength` was applied blind. LONG means 1,500 words whatever the source, so a
2,036-word article was asked for a summary 74% of its own length. That is an abridgement, not
a summary, and it is not a request a model can satisfy well: the only way to reach the count
is to walk the article section by section — which is what the prompt forbids in as many words,
and exactly the overshoot mechanism recorded in 0012.

The failure surfaced as the timeout in 0018, and fixing only the timeout would have left the
cause. Three separate harms, one root: a generation long enough to outrun the client timeout,
a narration padded to length, and output tokens paid for that make the episode worse.

PRD F-13 defines the lengths as listening durations — 2 / 5 / 10 minutes — which the enum maps
to 300 / 750 / 1500 words at roughly 150 wpm. Nothing in that contract says a ten-minute
listen must be producible from any article the user happens to paste.

## Decision

The requested length is capped at a share of the source and floored so a very short article
still gets a coherent narration:

```
targetWords = min(length.targetWords(), max(articleWords × ⅓, 150))
```

`readcast.summarization.max-source-fraction` (0.33) and `min-words` (150), both
environment-overridable. A third is where the model has to select and compress rather than
transcribe.

| Article | Requested | Aims for | Listen |
|---|---|---|---|
| 4,896 w | LONG | 1,500 — uncapped | ~10 min |
| 2,036 w | LONG | 672 | ~4.5 min |
| 1,000 w | LONG | 333 | ~2 min |
| 150 w (extraction minimum) | LONG | 150 (floor) | ~1 min |

`SummaryTarget` owns the arithmetic so every consumer reads the same number. That matters most
for the paragraph count: the prompt carries one because a model can obey a paragraph count and
cannot count words (0012), so a capped word target with an uncapped paragraph count is
precisely how the model would be talked back over the ceiling. Both now derive from the same
target.

The cache gate reads it too, so `exceedsCeiling` takes the article the summary would be served
for.

## Alternatives

**Snap down to the next enum tier** — LONG becomes MEDIUM on a short article. Simpler to
explain and to show a user. Rejected because the tiers are too coarse at the bottom: a
1,000-word post and a 2,000-word post would both land on MEDIUM, and a 300-word post has no
tier that fits it.

**Reject the combination at bookmark creation** and make the user pick again. Honest, and
wrong: the user pastes a URL and has no idea of its word count, so the error would be
unactionable and would arrive before extraction could tell them.

**Surface the effective length in the API** so a client can say "too short for a long
summary". Not rejected so much as deferred — `audioDurationSec` already tells the truth once
the episode exists, and adding a field to `BookmarkDetail` before anyone is confused is
speculative. Worth doing if it does confuse anyone.

**Leave it, and fix only the timeout.** See 0018. It would have made this bookmark succeed and
produced a padded ten-minute episode from a four-and-a-half-minute article.

## Consequences

A user who asks for a 10-minute listen can get four and a half, silently. That is the deliberate
trade — the prompt already tells the model "being under is fine" — but nothing yet tells the
user why, and the reason lives only here.

Existing over-long summaries of short articles now fail the cache gate and regenerate on next
encounter. No migration, same lazy invalidation 0012 designed. It does mean a burst of
regeneration the first time popular short articles are revisited.

The ratio is a guess. A third is defensible and untested against real listening — it wants
checking against a few actual episodes, which is why it is an environment variable rather than
a constant.

Most existing summarization tests use three-word articles to exercise the ceiling machinery, so
they now pass a fraction large enough never to bind. The cap has its own tests at realistic
values, but the older tests no longer exercise the path they run through in production.

The cap interacts with `max-input-words`, which is not obvious from either property. An article
over the input budget is truncated before it is summarised, so the target has to be derived
from the truncated input — a cap computed from words the model never sees asks for a summary
its input cannot support, and if the generation and the cache gate disagree about which count
to use, a summary can fail the gate that judged it. Both read what the model is actually given.
With the shipped budget the requested length binds first (0.33 x 40,000 is 13,200, well above
LONG's 1,500), so this is only reachable if `max-input-words` drops below about 4,546. Noted
here because the two properties look independent and are not.
