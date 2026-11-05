# 0027 — External SVG is rasterised, and local figure runs leave file traces

Date: 2026-11-01
Stage: 9 — Figures
Status: accepted

## Context

The figure pipeline initially rejected every SVG, but the state-machine diagram in
`postgres-is-my-queue` used that format. Accepting arbitrary markup is not equivalent to accepting
an image: SVG can contain scripts, entities, external stylesheets and resource URLs. A vision model
also needs a raster image, not XML. Separately, Gemini exhausted the initial 500-token output budget
through hidden reasoning, and evaluating real runs from logs alone made it hard to compare the exact
prompt, model response, enriched article and final narration.

## Decision

Accept external `.svg` URLs but keep inline `<svg>` and compressed `.svgz` out. Use Apache Batik to
produce PNG before hashing or vision. Parsing and transcoding reject doctype and entity
declarations, scripts, event handlers, `foreignObject`, external stylesheets and external-resource
references. Network access is disabled independently in the transcoder's user agent. Embedded fonts
remain available. Input and output are each capped at 5 MB, dimensions at 4096px per side and the
canvas at 16 megapixels. Only the PNG reaches `FigureVisionService`, and its bytes key the existing
`figure_descriptions` cache.

Raise the default vision output budget to 4096 tokens because the cap includes hidden reasoning;
the prompt still limits the visible answer to three short sentences. The default is overridable with
`FIGURE_VISION_MAX_TOKENS`.

For local evaluation, `readcast.figures.debug.directory` (`FIGURE_DEBUG_DIR`) enables one trace
directory per bookmark. It contains the original and enriched article, final or reused summary, and
each figure's metadata, rasterized PNG, exact prompt and raw response or error. Tracing is disabled
by default, never includes the listener profile, and write failures cannot change pipeline outcomes.
Startup and per-stage logs expose the effective model, selection reason, persistence count and
enrichment result without replacing the files.

## Alternatives

Sending SVG directly to the provider was rejected because image support varies and it would not
remove active content. Browser screenshots were rejected as another browser runtime and a larger
network-capable attack surface. An external ImageMagick process would add process isolation and
deployment requirements without making validation simpler. Raw SVG is therefore accepted only after
static, bounded conversion.

Always-on trace files were rejected because full article text and images are potentially sensitive
and grow quickly. Debug logging alone was insufficient for inspecting the exact artifacts and too
noisy for routine local use. Disabling tracing by default and keeping it under an ignored local
directory is the explicit testing escape hatch, not a production observability system.

## Consequences

Batik is now a rendering dependency, and its DOM and PNG-writer contracts must remain covered by
fixtures. The real `quaero-pipeline-1.svg` proves the target diagram renders; malformed SVG,
byte/dimension limits, scripts, external entities and external-resource references are rejection
tests, including zero requests for a referenced resource. Rasterization changes the cache identity
from source bytes to PNG bytes, so changing rasterization settings can legitimately produce a new
description. Trace files must be treated as sensitive local data and are not cleaned up
automatically. A completed summary whose figure failed is not regenerated merely because the token
budget changed; evaluation needs a fresh bookmark.
