# 0033 — Figure evaluation lives in a backend-hosted workbench

Date: 2026-11-02
Stage: 9 — Figures
Status: accepted

## Context

The file traces from 0027 made every evaluation run a sequence of opening individual text and
image files. They also omitted rejected candidates, cache outcomes, and the exact location where
recovered knowledge entered the article. Comparing model output with its evidence therefore
required reconstructing decisions from logs and manually diffing documents. The workbench needed
to serve both the existing trace dataset and richer future runs without becoming another reader UI.

## Decision

Serve a builder-only, read-only workbench from the existing Spring MVC backend at
`/debug/figures`, with no frontend build or database dependency. A filesystem catalog reads the
existing artifacts directly; static HTML, CSS, and vanilla JavaScript provide run filtering,
figure/evidence/response cards, candidate decisions, insertion review, and document comparison.

New runs add `run.json`: article identity, every candidate and score component, rejection reasons,
vision outcomes (including cache and failure), and exact enriched-text insertion spans. Enrichment
uses temporary per-run markers to locate the actual placeholder replacement after whitespace
normalisation, rather than searching for knowledge that might also occur earlier in the prose.
Legacy traces remain readable and receive explicitly approximate insertion mappings.

The viewer has a separate default-off switch. A remote dev deployment can persist traces on a
named volume and require browser Basic authentication; production keeps the viewer disabled.

## Alternatives

**Generate static HTML reports into `target/figure-debug`.** Portable and useful for snapshots,
but stale until regenerated and awkward as a live browser for local text files. It remains a
possible export feature, not the primary interface.

**Build a separate frontend or standalone viewer process.** Either could host the same UI, but a
frontend build is disproportionate for a builder tool and another process duplicates the debug
directory configuration. Backend hosting already provides routing and safe local image serving.

**Infer all decisions and insertion positions from the old text artifacts.** Zero-figure runs,
rejected candidates, fetch failures, and cache hits cannot be reconstructed honestly. A manifest
was necessary; legacy inference is only a compatibility fallback.

## Consequences

The deployment now includes a persistent debug volume and an optional authentication filter, so
remote traces require deliberate storage and access configuration. The catalog has to understand
both legacy directories and versioned manifests. Existing runs never gain rejected-candidate data,
and their insertion positions are approximate when the same knowledge occurs more than once.
Run-to-run comparison, annotations, reruns, and static export remain outside this version.
