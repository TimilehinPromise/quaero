# 0030 — A figure is judged by its fetchable image, not by its first img element

Date: 2026-11-02
Stage: 9 — Figures
Status: accepted

## Context

Three site classes produced the same failure shape in one debugging session: the figure's
real URL is not in `img.src`, and the code was reading only `img.src`. Cloudflare's blog
parks a 1×1 data-URI placeholder *ahead* of the real image inside the same `<figure>`, so
`selectFirst("img")` grabbed the placeholder and the whole figure died as `data-uri` while
the real image was skipped as nested. Medium's `<img>` elements carry **no src at all** —
the only copy of the URL lives in `<source srcset>`. AWS blogs and Figma wrap a small
in-body thumbnail in `<a href="full-size.png">`, and the blanket `linked-image` rejection
(0025) discarded full-size diagrams whose better URL was sitting in the href. In each
case the raw page contained everything needed; extraction was looking in the wrong place.
A browser-UA change made mid-investigation turned out to be irrelevant — Cloudflare's
blog serves the identical DOM to both user agents — and is kept only for sites that
genuinely UA-gate.

## Decision

`FigureMarkup` resolves the representative image of a figure through three ordered
fallbacks:

1. **Placeholder skipping.** Among a figure's imgs, take the first non-`data:` src,
   largest declared dimensions first; fall back to the placeholder only when no fetchable
   src exists at all.
2. **Srcset resolution.** When the img has no usable src, try its own `srcset`, then
   sibling `<source srcset>` elements; parse the candidates and take the widest — vision
   gets the best quality the page ships (Medium's `resize:fit:1400` variant) rather than
   whatever happened to be first.
3. **Image links followed.** When an image is wrapped in a link whose href is itself an
   image file (http/https, image extension), the href becomes the figure URL — the CMS has
   linked its thumbnail to the full-size original. Lightbox links back to the article page
   keep the `linked-image` rejection, and the thumbnail's declared dimensions no longer
   gate acceptance since they do not describe what is fetched.

Dimensions and alt still come from the img element itself, so the existing
`min-dimension` rejection keeps working. The markdown route keeps its existing rules —
none of the observed failures were reachable through Jina's markdown.

## Alternatives

**Reject data-URIs earlier in the walk.** The placeholder was not surviving to the URL
check; it was being *selected* while the real image was skipped as nested. Earlier
rejection changes nothing.

**Take the last srcset candidate without parsing.** Medium orders ascending, so this
usually equals widest-candidate — but candidate order is not contractual, and width
descriptors are cheap to read.

**Demote linked images in ranking instead of following them.** Keeps the thumbnail URL,
so the vision model receives a worse image than the page offers; the full-size original
costs nothing extra to reference.

**Fetch every candidate and sniff content-type.** Correct but adds a request per image;
URL shape is sufficient evidence for the follow decision, and a wrong follow costs one
cached `NONE`.

## Consequences

The linked-image rule's old protection against nav and logo links now rests on href shape
rather than intent: an image wrapped in a link to an image file is assumed editorial. A
wrong assumption costs one vision call and is cached as `NONE`, so the blast radius is one
lookup per mistake. The three fallbacks are ordered by evidence strength, not by
confidence — a figure that matches more than one (placeholder inside a linked thumbnail)
uses the first that yields a URL. Live results after the change: Cloudflare 11 of 12 body
figures recovered, Medium 9 of 12, AWS's single architecture diagram recovered, Figma's
Sanity CDN images recovered through the same href path.
