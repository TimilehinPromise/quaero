# 0014 — Content type decides the extractor, and PDFs get one

Date: 2026-08-29
Stage: 4 — Extraction
Status: accepted

## Context

A bookmark for `https://www.trydecide.ai/reports/dax-1-technical-report.pdf` reached READY and
produced 58 seconds of narration about "font definitions, image streams, page layouts and
hyperlink annotations". Nothing failed; every stage reported success.

The fetcher sent `Accept: text/html` but never looked at what came back, and
`bodyToMono(String.class)` decodes any content type as UTF-8. So 476 KB of PDF was decoded into
mojibake, handed to Jsoup as HTML, and — finding no tags — fell through the content selectors to
`densestBlock`, which returned the whole body. The result was 9029 "words" of the PDF's own
uncompressed internals: XMP metadata, font descriptors, and the `/Annots` link targets. That
cleared the 150-word minimum, so the gate designed to catch empty extractions waved it through,
and the summary and the TTS run were both paid for.

Two separate holes: nothing checked that the bytes were the format we asked for, and a URL the
pipeline can never process was retried on the normal backoff schedule.

## Decision

**A `DocumentExtractor` per format, selected by response content type.** Each implementation
declares the media types it reads. `ArticleExtractor` takes HTML and XHTML; the new
`PdfExtractor` takes `application/pdf` via PDFBox. The fetcher derives both its `Accept` header
and its rejection rule from those same declarations, so a new format is a new bean rather than
edits in three places.

`FetchedPage` now carries raw bytes and the content type instead of a decoded string. HTML
decoding moved into `ArticleExtractor`, which lets Jsoup re-read the charset from the meta tag
or BOM when the header omits it or lies — better than the header-only decoding it replaces.

**Unreadable content types are refused on the response headers**, before the body is read, so a
video is not pulled down to the 5 MB cap only to be discarded. A missing `Content-Type` is read
as HTML rather than refused: servers that omit it are overwhelmingly serving pages, and being
strict there would reject articles that work today.

**`UnsupportedContentException` is terminal on the first attempt.** `BookmarkPipeline` sends it
straight to FAILED. The same URL returns the same bytes, so retrying it three times on a
2-minute backoff only delays the same answer.

## Alternatives

**Reject non-HTML and stop there** — the cheap half of the fix, and it does end the silent
garbage. Rejected because the PDF is a legitimate reading-list item: a technical report is
exactly the thing someone wants narrated on a commute, and telling them "we can't read this" is
a worse product than reading it.

**Sniff the `%PDF-` magic bytes instead of trusting the header** — robust against servers that
mislabel, and about three lines. Rejected for now because the header was never the problem here
(trydecide.ai sends `application/pdf` correctly), and a body-sniffing path only earns its place
once a real mislabelled URL turns up.

**Send `Accept: */*`** — fewer 406s from picky servers. Rejected because it invites exactly the
class of response this entry exists to stop, and the fetcher has never sent it.

## Consequences

PDF text arrives as the page paints it. Running headers, footers and page numbers land inline in
the prose, and a two-column layout can interleave. It is coherent enough to summarise — the
DAX-1 report extracts 4896 words of real text — but it is not clean, and a summary of a
heavily-laid-out PDF will read worse than one of an HTML article. Stripping repeated
header/footer lines is the obvious next move if it shows up in output.

The 5 MB response cap now doubles as the PDF size limit, which is tighter for PDFs than for
pages. Summarization already truncates at 40 000 words, so a long document costs no more than a
long article.

PDFBox is a real dependency with a real footprint, pinned at 3.0.4 because the Boot BOM does not
manage it. Adding it for one format is the cost of supporting that format at all.
