# 0031 — A readable-looking page that extracts below its floor degrades to the hosted reader

Date: 2026-11-02
Stage: 4 — Article extraction (revisited)
Status: accepted

## Context

A Figma blog post failed extraction permanently: "Extracted only 12 words, below the
150-word minimum" — a non-retryable refusal, so the bookmark was headed for the
paste-text fallback. The cause was a measurement mismatch, not a threshold. The page
classifier counts visible words across the whole body; navigation and footer chrome alone
on a marketing-heavy JS shell easily exceeds the 150-word ARTICLE floor. The extractor
counts words inside its chosen content root, and a client-rendered shell's content root
holds a dozen words. The page classified ARTICLE, took the direct-extraction route, and
failed — the hosted reader (0020's EMPTY route, enabled in production) only runs for pages
classified EMPTY, so it never fired. Database history put the frequency honestly: two
below-floor failures ever, zero JavaScript blocks — rare, but unrecoverable when it hit.

## Decision

In `ExtractionStep`, when ARTICLE or THIN extraction throws the non-retryable below-floor
`ExtractionException`, the bookmark no longer fails. The step re-classifies the page
EMPTY-style — a new `PageClassifier.degraded()` fills the brief title/description fields
regardless of the body word count — and runs the existing EMPTY route: hosted reader
first, metadata brief as backstop. Retryable failures propagate unchanged, and the EMPTY
route's own semantics (reader error propagates, brief only when long enough) are reused
exactly.

## Alternatives

**Lower the EMPTY threshold.** Wrong lever: the threshold was never in play. The page
classed ARTICLE on 150+ body words; no EMPTY cutoff would have caught it without also
re-routing genuinely readable pages through the paid reader.

**Make the classifier count content-root words like the extractor.** Removes the mismatch
by duplicating `selectContent` logic in two places that must then agree forever, and still
guesses: a shell with a content root of 160 words of boilerplate would class ARTICLE and
extract nothing useful anyway.

**Route every app-shell-marked page to the reader.** Any page with `#__next`/`#root` pays
for a render even when direct extraction works — the common case by far — multiplying
reader spend for certainty the fallback already provides at zero cost on success.

**Keep failing and rely on paste-text.** The reader exists precisely for pages a plain
fetch cannot read; routing a recoverable page to a manual fallback is a UX surrender the
architecture already paid to avoid.

## Consequences

New spend is one reader render per page that would otherwise have *failed* — bounded by
construction, since the fallback only runs after direct extraction has thrown. Pages that
extract under their floor for reasons other than a JS shell (degenerate markup) also pay
the reader call now; at the observed failure frequency that is noise. The measurement
mismatch itself survives: body word count and content-root word count still disagree, and
this entry treats the symptom at the routing layer rather than unifying the two counts —
unification stays available if the failure ever becomes common enough to justify the
coupling.
