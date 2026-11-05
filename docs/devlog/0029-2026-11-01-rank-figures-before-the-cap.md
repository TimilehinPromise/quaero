# 0029 — Figures are ranked before the cap, by signals that survived measurement

Date: 2026-11-01
Stage: 9 — Figures
Status: accepted

## Context

After 0028, an Uber post with sixteen valid body figures hit the cap: FIFO selection kept
the first four images in document order and silently dropped twelve real diagrams — all
the same URL shape as the keepers, so the rejection pass could not help. The obvious
ranking signals had to be checked against evidence before being trusted. A first
measurement pass (two articles, six vision calls, all debug artifacts persisted) showed:
a real `figcaption` predicted a useful figure 2/2; alt-text richness predicted nothing —
the decorative hero carried rich, descriptive alt text and vision returned `NONE` on it;
and Uber's captions live in sibling paragraphs (`Figure 1: Generalized Ads Marketplace
architecture.`), invisible to any `figcaption`-only rule.

## Decision

Selection is two-phase in `FigureMarkup`: collect every candidate that passes the rejection
rules, score it, and keep the top `maxPerArticle` by score. Weights: meaningful caption
+30, caption-like adjacent paragraph (`^fig(ure)?\s*\d+\s*[:.)]`) +20, `<figure>` wrapper
+10, junk URL smell (`logo`, `icon`, `avatar`, `sprite`…) −30, meaningful alt +5
(tiebreak only — the data falsified it as a primary signal), document position −0.01×index
as the final tiebreak so equal images keep FIFO order and the sort is fully deterministic.
Ordinals are assigned in document order regardless of rank, because the `[Figure N]`
placeholders are positional in the text. Ranking only decides who gets a vision slot:
nothing is filtered from the article, and vision `NONE` remains the final arbiter of
usefulness. Every selection and cut is logged with its score.

## Alternatives

**Vision on all candidates, select afterwards.** Content-aware and simple, but pays image
tokens on every figure including the ones ranking can dismiss for free — on the 16-figure
article, four times the cost for the same four slots.

**Text-only LLM triage** (one small call over caption/alt/surrounding paragraphs picks the
winners). Best quality-per-dollar in principle and catches what heuristics cannot ("this
caption is SEO filler"). Held in reserve: it adds a call per article for a failure mode
that had not yet been observed, and the logged scores give the data to evaluate it later
without new plumbing.

**Alt-text quality as the primary ranking signal.** Falsified by the measurement pass —
rich alt appears on both the decorative hero and the valuable diagrams alike; scoring on
it ranks the duds equal to the keepers.

**FIFO with a higher cap.** Document order is not evidence. It had produced acceptable
cuts only because the junk (related rails) happened to come after the body; once real
figures outnumber the cap, FIFO loses content with no signal at all.

## Consequences

Ranking cannot see image *content*: a bare screenshot with no caption pattern, no wrapper
and no alt scores ≈0 and lives or dies by document position. That is the known residual
gap, now measurable — the logs distinguish `ranked-below-cut score=0.0` from informative
cuts, so a future data round can grep for exactly the cases that justify the triage step.
The weights are hand-set from a five-figure sample; the score bands they produce
(captioned ≈40, wrapped ≈10-15, alt-only ≈5) were stable across every site tested
afterwards, but they remain constants in code, not learned values.
