# Devlog

Dated record of technical decisions and diversions, so anyone (human or agent) picking this
repo up later can see **why** it looks the way it does — not just what it does.

Git history records what changed. This records why we chose it and what we rejected.

## When to write an entry

Write one when:

- We diverge from `docs/ReadCast_PRD.md`.
- We pick between real alternatives and the loser was defensible.
- We hit a non-obvious constraint that shaped the design (a CORS rule, a library gap, a
  provider limit).
- We revert or supersede an earlier decision.

Do **not** write one for routine feature work, refactors, or bug fixes. The diff already
explains those. An entry per commit makes the log worthless.

## Format

One file per decision:

```
docs/devlog/NNNN-YYYY-MM-DD-short-slug.md
```

`NNNN` is a zero-padded sequence, never reused. The date is when the decision was made.

```markdown
# NNNN — Title

Date: YYYY-MM-DD
Stage: <stage number and name from docs/IMPLEMENTATION_PLAN.md>
Status: accepted | superseded by NNNN | reverted

## Context
What forced a decision. The constraint, not the task.

## Decision
What we chose, stated plainly.

## Alternatives
What we rejected, and the actual reason — not a strawman.

## Consequences
What this costs us later. The honest downside, including what it makes harder.
```

## Superseding

Never edit an accepted entry's decision. Write a new entry, and change the old one's
`Status:` line to `superseded by NNNN`. The wrong turns are the useful part of a decision log.

## Index

| # | Date | Title | Status |
|---|------|-------|--------|
| [0001](0001-2026-08-22-foundations-and-stack-decisions.md) | 2026-08-22 | Foundations and stack decisions | accepted |
| [0002](0002-2026-08-22-schema-constraints.md) | 2026-08-22 | Schema constraints that shaped the first migrations | accepted |
| [0003](0003-2026-08-22-ssrf-control.md) | 2026-08-22 | SSRF control and its known gap | accepted |
| [0004](0004-2026-08-22-tts-provider-deferred.md) | 2026-08-22 | TTS provider deferred; hosting is Contabo | accepted |
| [0005](0005-2026-08-22-queue-and-attempt-semantics.md) | 2026-08-22 | Queue claim and attempt semantics | accepted |
| [0006](0006-2026-08-22-extraction-and-blocked-sites.md) | 2026-08-22 | Extraction, and the sites that refuse to be extracted | accepted |
| [0007](0007-2026-08-22-summarization.md) | 2026-08-22 | Summarization via Spring AI | accepted |
| [0008](0008-2026-08-23-tts-and-audio-storage.md) | 2026-08-23 | Self-hosted TTS and audio storage | accepted |
| [0009](0009-2026-08-23-summarization-moved-to-openrouter.md) | 2026-08-23 | Summarization moved to OpenRouter's free tier | accepted |
| [0010](0010-2026-08-23-talking-to-the-deployed-kokoro.md) | 2026-08-23 | Talking to the deployed Kokoro | accepted |
| [0011](0011-2026-08-23-pronunciation-of-proper-nouns.md) | 2026-08-23 | Pronunciation of proper nouns | accepted |
| [0012](0012-2026-08-23-word-ceiling-enforced-in-code.md) | 2026-08-23 | Word ceilings enforced in code, and the cache that masked prompt fixes | accepted |
| [0013](0013-2026-08-23-extraction-titles-and-authors.md) | 2026-08-23 | Real titles and authors from extraction, not the LLM | accepted |
| [0014](0014-2026-08-29-content-type-decides-the-extractor.md) | 2026-08-29 | Content type decides the extractor, and PDFs get one | accepted |
| [0015](0015-2026-08-30-deployment-topology.md) | 2026-08-30 | Deployment: Coolify, a prebuilt image, and a host that was already occupied | accepted |
| [0016](0016-2026-08-30-off-the-free-tier.md) | 2026-08-30 | Off the free tier, onto the same model | accepted |
| [0017](0017-2026-08-31-narration-synthesised-in-chunks.md) | 2026-08-31 | Narration is synthesised in chunks, and the parts are joined without re-encoding | accepted |
| [0018](0018-2026-08-31-summarization-timeout.md) | 2026-08-31 | The summarization timeout has to ride on the call | accepted |
| [0019](0019-2026-08-31-summary-length-capped-by-article.md) | 2026-08-31 | The requested summary length is capped by what the article can support | accepted |
| [0020](0020-2026-09-22-classify-before-extract.md) | 2026-09-22 | Classify the page before extracting, and stop retrying deterministic failures | accepted |
| [0021](0021-2026-09-25-acronyms-said-as-words.md) | 2026-09-25 | Acronyms said as words get a pinned pronunciation | accepted |
| [0022](0022-2026-09-26-browser-client-access.md) | 2026-09-26 | Opening the API to a browser frontend on another origin | accepted |
| [0023](0023-2026-09-30-output-limit-includes-reasoning.md) | 2026-09-30 | The output token limit has to cover the model's reasoning | accepted |
| [0024](0024-2026-09-30-listener-lens.md) | 2026-09-30 | Summaries explained through a listener's lens | accepted |
| [0025](0025-2026-10-02-figures-in-articles.md) | 2026-10-02 | What an article's figures say is narrated, read by a vision model chosen by test | superseded by 0027 |
| [0026](0026-2026-10-29-kokoro-word-timestamps.md) | 2026-10-29 | Kokoro word timestamps use a pinned /dev contract | accepted |
| [0027](0027-2026-11-01-svg-figures-and-local-traces.md) | 2026-11-01 | External SVG is rasterised, and local figure runs leave file traces | accepted |
| [0028](0028-2026-11-01-prune-related-content-rails.md) | 2026-11-01 | Recommendation rails are pruned before figure selection and before the word count | accepted |
| [0029](0029-2026-11-01-rank-figures-before-the-cap.md) | 2026-11-01 | Figures are ranked before the cap, by signals that survived measurement | accepted |
| [0030](0030-2026-11-02-judge-figures-by-the-fetchable-image.md) | 2026-11-02 | A figure is judged by its fetchable image, not by its first img element | accepted |
| [0031](0031-2026-11-02-degrade-to-reader-below-floor.md) | 2026-11-02 | A readable-looking page that extracts below its floor degrades to the hosted reader | accepted |
| [0032](0032-2026-11-02-demo-gifs-left-unread.md) | 2026-11-02 | Animated demo GIFs are left unread | accepted |
| [0033](0033-2026-11-02-figure-debug-workbench.md) | 2026-11-02 | Figure evaluation lives in a backend-hosted workbench | accepted |
