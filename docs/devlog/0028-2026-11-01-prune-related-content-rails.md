# 0028 — Recommendation rails are pruned before figure selection and before the word count

Date: 2026-11-01
Stage: 9 — Figures
Status: accepted

## Context

The first production figure logs (Uber engineering posts) showed `cap-reached` skips whose
URLs were all related-stories thumbnails: six of twelve images inside the chosen content
root were off-article, competing for the four vision slots. The rail sits inside `main`,
past the article body — the extractor's chrome pass missed it because BaseWeb's class names
are hashed (`css-gqcHgx`), so `.related` and every class-substring convention failed to
match. On that page the FIFO cap had *luckily* cut the rail, because Uber renders the body
first; document order is not a strategy.

## Decision

Two mechanisms, both running on the content root before figure selection and before the
word count, so prose and figures benefit together:

- Attribute-substring selectors in the existing chrome removal for conventionally named
  rails: `[class*=related]`, `[id*=recommended]`, `[class*=read-next]`, `[data-testid*=related]`
  and similar. Lowercase only: jsoup 1.23.1's `[attr*=value i]` silently matches nothing,
  which cost one debugging round before the tests caught it.
- Heading-driven pruning for hashed-class CMSs: an element whose **own text** fully matches
  a rail label (`related articles`, `you may also like`, `recommended for you`, `more from …`,
  `read next`, and similar, capped at 40 chars) marks its closest `section`/`[role=region]`
  ancestor for removal. A section larger than half the article's word count is treated as
  article body and never removed, so a shared body+rail section can only lose the rail
  images, never the prose.

## Alternatives

**Raise the cap** (`max-per-article` is a config value). Fixes the symptom by brute force at
two-to-four times the vision tokens per heavy article — the spend the whole figure layer is
designed to avoid.

**Rank the junk out instead of removing it.** Ranking (0029) was built later and does
demote rail images, but ranking cannot help what should never have been a candidate: every
rail image that survives to the rank step costs a fetch or a slot.

**Class/id selectors only, no heading pass.** Handles conventionally named rails and misses
exactly the case observed (hashed classes, no `data-testid` on the rail container). The
heading pass alone misses rails with no heading element. The two cover different CMS
families; neither is sufficient.

**Leave the rail images to vision `NONE`.** The model correctly judges rail thumbnails
uninformative — at ~1,700 tokens per image to learn nothing, on every article from any site
with a rail. Deterministic removal is free.

## Consequences

The label list is English-only; rails in other languages, or rails whose heading matches no
label and whose classes are hashed, still reach figure selection — the size guard then
declines to remove anything. A related rail that shares its section with the article body
keeps its images (the guard cannot tell them apart); observed in tests and accepted.
Substring selectors stay lowercase-only until the jsoup `i`-flag behaviour is re-verified
on upgrade. Debug artifacts from before this change are preserved under
`target/figure-debug-archive-before-placeholder-fix/`.
