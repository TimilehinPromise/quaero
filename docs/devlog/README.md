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
