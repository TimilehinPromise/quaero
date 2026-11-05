# 0013 — Real titles and authors from extraction, not the LLM

Date: 2026-08-23
Stage: 4 — Extraction
Status: accepted

## Context

The first X long-form article through the pipeline stored the title
`Hiten Shah (@hnshah) on X` — X's `og:title` for article pages is the author's profile label,
not the headline. The actual headline ("Notes on building products people love when software
gets cheap") sits in the page's `<h1>` and, duplicated, in `og:description`. The extractor read
`og:title → <title>` and stopped, so the worst-available source won.

The same inspection showed authorship was equally available and equally ignored:
`twitter:creator` carries `@handle`, `article:author` a profile URL. A reading-list app wants
the writer as data — for display, grouping, and eventually for the narration opening.

## Decision

**Deterministic metadata extraction; the LLM never names anything.**

*Titles*: a placeholder-title detector (`… (@handle) on X`, `… on X: …`) disqualifies junk
`og:title`s, and the fallback chain becomes `og:title → og:description (if short enough to be
a headline, ≤ 150 chars) → first <h1> → <title>`.

*Authors*: a new nullable `bookmarks.author` column (migration 006), populated from
`twitter:creator` → `meta[name=author]` → `article:author`, surfaced on both bookmark DTOs.
Nullable because plenty of sites publish no author metadata at all.

Same session, smaller piece: `POST /api/v1/bookmarks/{id}/retry` requeues a FAILED or BLOCKED
bookmark to PENDING with attempts cleared. Debugging the pipeline had shown the only recovery
was delete-and-recreate, which also loses the row's history.

## Alternatives

**Ask the summariser for a title (and author).** The model reads the content anyway, so this
is zero parsing work. Rejected: titles become non-deterministic and change on re-summarisation,
display metadata gets entangled with the narration prompt, and the answer was already sitting
in the HTML for free. Metadata is a parsing problem; keep it deterministic.

**Prefer `<h1>` unconditionally over `og:title`.** On many sites they agree, so it would be
harmless there — but plenty of pages put navigation or a site slogan in the first `<h1>`, and
`og:title` is the more deliberately-curated field when it's honest. Detecting junk and falling
back keeps the good default while fixing the silos that abuse it.

**A general silo-abstraction (per-site extractor plugins).** The pattern list will grow one
silo at a time — today X, maybe LinkedIn or Threads next. A plugin interface now would be
speculative generality for a list of two regexes.

## Consequences

- **The placeholder-title pattern list is a maintenance surface.** Each silo that publishes
  junk titles needs its pattern added; until then its bookmarks carry the junk title. The
  fallback chain degrades gracefully (h1 is usually right), so the cost of a missing pattern
  is cosmetic, not a failure.
- **`author` values are heterogeneous by construction.** `@handle` from X, a full name from
  `meta[name=author]`, a profile URL from `article:author` when nothing better exists.
  Consumers must treat it as a display string, not an identifier. Normalising handles into
  identities is a separate, deliberate step if it's ever needed.
- **Old rows keep their junk titles.** Extraction only runs once per bookmark; the retry
  endpoint is the way to re-run it (PENDING → extraction → new title/author). No backfill
  was written — the library is days old and a retry per affected row is trivial.
- **The narration could now open with the author** ("Hiten Shah on why…"). Not wired into the
  prompt yet — the prompt changes are their own tested surface, and this waits until someone
  listens and wants it.
