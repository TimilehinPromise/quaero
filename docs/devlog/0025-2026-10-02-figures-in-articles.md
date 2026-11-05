# 0025 — What an article's figures say is narrated, read by a vision model chosen by test

Date: 2026-10-02
Stage: 9 — Figures (`docs/IMPLEMENTATION_PLAN.md`)
Status: superseded by 0027

## Context

Extraction keeps `content.text()` of the content container. Every image is dropped with its
alt text, `svg` is stripped as chrome, and captions survive as loose sentences with nothing
around them. For technical and financial writing the chart is often the claim; the narration
summarises the paragraphs around a hole.

Narration is audio, so the listener needs what a figure *says*, not what it looks like. And a
model reading a chart can be confidently wrong in a way no one listening can check, so a
wrong figure costs more trust than a missing one.

## Decision

**Two layers, one positioned article stream.** First, keep what the markup already says: a
usable caption or alt text becomes an inline `[Figure: …]` marker where the image was, and junk
alt text (filenames, "image", two words or fewer, the title repeated) counts as absent. The
figure's document position and surrounding paragraphs survive extraction. Then, behind a flag,
a vision model recovers knowledge still missing from the text. Captions and alt text are the
no-model baseline and useful context, not proof that a technical figure needs no reading.

**Selection by HTML, then one vision call per image.** Images outside the content container,
small, `data:` URIs, inline SVG and linked images are dropped for free; at most four go to the
model. External SVG is rasterised to bounded PNG bytes with scripts, entities, external resources
and network access disabled. This filter favours keeping a questionable figure over discarding an
informative one. Each call gets the image, title, caption, alt text and surrounding paragraphs,
and returns either `NONE` or what the figure contributes. Relevance classification and knowledge
extraction are one call.

**Knowledge, not a visual description.** The result is short narration-ready text about the
relationships, flow, trend or values the figure conveys, never a walkthrough of shapes and
colours. Surrounding text supplies terminology and relevance, not facts that are invisible in
the image. Unreadable numbers and unsupported claims are omitted; a numeric confidence score is
not evidence of correctness. Replacing the figure's existing marker puts the knowledge where the
author put the figure, so there is no separate AI placement stage.

**Enriched inside the summarisation step, like the listener brief.** It is enrichment that must
never fail a bookmark: one retry per image, then the figure is skipped. Figure extractions are
cached by image hash, so a retried summary never pays twice. Summary reuse is keyed by the
enriched input, not raw text containing interchangeable `[Figure n]` markers: identical markers
over different images must never share a summary.

**The model is chosen by a test on real images, not by price or leaderboard.** At ~1,750
tokens per image, a mid-tier model costs about $0.002 per image; the cheapest about a tenth of
that. Either is small next to TTS. The test checks both the recovered knowledge and its use in
the final narration against a text-only baseline. A model that invents a single chart number is
out, whatever it costs.

## Alternatives

**Jev (TypeSafe AI) as a cheap classifier before the vision model.** Jev returns typed
decisions in ~100ms at $0.04/MTok, but its input is text only (Jev Router accepts images and
only forwards them to another model). It would have to classify from alt text, caption,
filename and surrounding paragraph. Those can establish that a figure belongs to the article,
but not whether its visual relationships matter. It is also three weeks old and in early
access. Revisit if the logged `NONE` rate shows many vision calls wasted on images HTML
filtering let through.

**Sending the images to the summary model in one multimodal call.** Fewer moving parts. But it
ties the summary model choice to vision ability, which 0024's comparison never weighed, pays image
tokens on every summary retry, and lets one bad image fail the whole summary.

**Standalone OCR, diagram graphs and specialised chart parsers.** OCR recovers labels but not
what their relationships mean, and a different parser for every figure type is a document-AI
platform rather than an enrichment stage. Start with a vision model tested on real figures;
add a specialised tool only for a recurring, measured failure it can fix.

**A separate pipeline status, `DESCRIBING_FIGURES`.** Fits "one bookmark, one step, one
commit" more cleanly, and gives figures their own retry budget. It costs a migration to the
status check constraint, a change to the claim SQL, and a new status every client has to know
(`docs/FRONTEND_API.md` §4) — for a step that is designed never to fail anything. The lens
brief set the precedent of enrichment inside the summarisation step; this follows it.

**Free or stealth vision models.** No cost, but rate-limited, liable to vanish, and free tiers
often log prompts. Not for something in the default pipeline.

## Consequences

- F1 changes the text extraction produces, so `content_hash` changes for every page with a
  captioned figure: the first re-save of each such article misses the summary cache once. F3's
  summary-reuse key must also cover the enriched figure content; image-hash caching alone does
  not make raw placeholder text a safe summary key.
- Image URLs come from the page, which comes from the user. Image fetching is another SSRF
  surface and goes through the same validator, with the same redirect re-check and gap
  (`0003`). SVG parsing is another active-content surface: Batik runs with scripts, doctype and
  entity declarations, external resources and network access disabled, plus byte and dimension
  limits. Only the resulting PNG reaches the vision model and its cache; inline SVG stays out.
- Two vision-model failure modes the test cannot fully rule out: inventing a number, and
  `NONE` on an image that mattered. The prompt and disqualification rule favour the second.
- The shortlist prices were read from aggregator pages on 2026-10-02 and will be stale within
  weeks; the test, not this table, picks the model.
- Standalone OCR, diagram-to-graph conversion and specialised parsers are deferred. Images
  rendered only by JavaScript and images inside PDFs are not handled.
