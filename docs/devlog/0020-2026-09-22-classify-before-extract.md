# 0020 — Classify the page before extracting, and stop retrying deterministic failures

Date: 2026-09-22
Stage: 4 — Article extraction (revisited)
Status: accepted

## Context

A bookmark of `https://qwen.ai/blog?id=qwen-image-2.1` sat in `PENDING`, retrying:
`ExtractionException: Extracted only 0 words, below the 150-word minimum`, twice in four
minutes and scheduled for a third attempt. The fetched HTML tells the real story: the page is
a client-rendered SPA — HTTP 200, empty `<body>`, no `__NEXT_DATA__`, no JSON-LD, all content
produced by JS bundles at runtime. **No retry of an identical fetch can change those bytes.**

Two product problems, one mechanism:

1. The retry ladder assumed every failure is transient. Deterministic failures — empty shell,
   too few words, unreadable markup — burned the whole backoff series to reach the answer the
   first attempt already had.
2. One fetch strategy plus one 150-word floor assumed every URL is a server-rendered article.
   Genuinely short pages (release notes, announcements) were refused by a floor meant for long
   reads, and JS-rendered pages failed opaquely instead of being recognised for what they are.

## Decision

**Extraction is two-phase: classify, then route.** `PageClassifier` scans the fetched page
and `ExtractionStep` routes on the result:

- `ARTICLE` (≥ `readcast.extraction.minimum-words`, 150) — extract at the normal floor.
- `THIN` (≥ `readcast.extraction.thin-minimum-words`, 50) — extract at the lowered floor.
  Short pages are read, not refused; supersedes the consequence recorded in 0006.
- `EMPTY` — no readable text in the markup. If the meta description is long enough to carry
  real content (≥ `readcast.extraction.brief-minimum-chars`, 80), it is narrated as a degraded
  brief (title + description); otherwise `NoReadableContentException`, which the pipeline
  routes to `BLOCKED` — the paste-text recovery path that already exists.

**Failures carry whether retrying can help.** `ExtractionException.isRetryable()` marks
deterministic failures (below floor, unparseable HTML, redirect loop) as non-retryable;
`BookmarkPipeline` fails them on the first attempt. Retries remain for transient failures —
timeouts, 5xx, rate limits that lift.

## Alternatives

**Headless-browser rendering (Playwright) as the answer to JS shells.** The real fix for the
content, and deliberately deferred: a Chromium dependency, 3–10s per page, real memory. The
classifier makes it a drop-in later — the `EMPTY` route gains a renderer before the brief
fallback. Chosen over doing nothing because nothing left the user with a bookmark that spins
for minutes and then fails; chosen over building it now because the hosted-reader option
(e.g. Jina/Firecrawl) is a cheaper way to validate how often the route fires.

**Always BLOCK on EMPTY, no metadata brief.** Simpler, and avoids narrating a site-wide blurb
on sites whose `og:description` is generic boilerplate — which no length gate can detect.
Kept the brief because a short degraded episode beats a dead bookmark, and the failure path is
unchanged when the description carries nothing.

**Adaptive floors per document type in one extractor** (a single heuristic inside
`ArticleExtractor` rather than a routing step). Rejected because the pipeline needs the
distinction too: the same "0 words" input must be non-retryable, and that decision belongs at
the failure boundary, not buried in extraction logic. The classifier also keeps
`ArticleExtractor` untouched for the ARTICLE path.

## Consequences

A `BLOCKED` bookmark no longer always means the site refused us — it now also covers "the
content exists only after JavaScript runs". `docs/design/ui-states.md` copy for BLOCKED
(paste-text primary, no retry button) still fits, but a future UI pass should distinguish the
two causes for the user.

The metadata brief is narrated indistinguishably from a full extraction: no flag marks it as
degraded, so the UI cannot label it. If that matters, it wants a `partial`/`degraded` marker
on the bookmark — a schema addition held back from this change.

The EMPTY route sees only what a plain fetch sees. Sites that server-render a teaser plus JS
content will classify on the teaser; a future render fallback inherits the routing.

The qwen.ai URL this entry started from extracts a generic site description as its brief — the
heuristic's known blind spot, preserved deliberately rather than blocking a page that might
have had a real description.

**Update, 2026-09-23:** the hosted reader is no longer deferred. A `PageReader` SPI sits on the
EMPTY route — Jina Reader implemented first (provider `jina`, `X-Engine:
cf-browser-rendering`) — with reader HTML re-entering the normal classification and extraction
and reader text used directly at the thin floor. Service errors propagate instead of
downgrading to a brief: 429/5xx retry, 402 fails on the spot. Adding a provider is one bean +
one properties record under `readcast.reader.<provider>.*`; nothing downstream names a service.
