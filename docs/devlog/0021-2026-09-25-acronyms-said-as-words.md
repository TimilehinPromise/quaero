# 0021 — Acronyms said as words get a pinned pronunciation

Date: 2026-09-25
Stage: 6 — TTS
Status: accepted

## Context

Narration read "EBITDA" as "E-B-I-T-D-A". Kokoro's phonemiser, misaki, reads any all-capitals
word missing from its lexicon one letter at a time. That is the right guess for CEO and the
wrong one for EBITDA, Nasdaq or Opec.

Devlog 0011's prompt rule was meant to cover this ("acronyms said as words"), but the same
rule told the model to leave well-known names alone. EBITDA is well known to a reader, so the
model left it in capitals. Well known to a reader is not the same as known to the synthesiser.

## Decision

Two layers.

**A pronunciation table applied only to the text sent for synthesis** (`SpokenAcronyms`).
Known acronyms are rewritten into misaki's inline override, `[EBITDA](/ibˈɪtdɑ/)`, just before
chunking. Matching ignores case, so the model's "Ebitda" and a literal "EBITDA" land on the same
sound. The stored `summaryText` keeps the ordinary spelling.

**A separate prompt rule for acronyms.** It is no longer tied to the proper-noun rule and has
no well-known exemption: capitals only for abbreviations said letter by letter, and first
letter only for acronyms said as words. This is the fallback for terms the table does not
list, since a mixed-case word goes to espeak's guess instead of being spelled out.

## Alternatives

**Prompt rule alone.** It covers unseen terms, but it already failed on exactly this word, and
it adds more deliberate misspellings to the displayed summary.

**Lower-casing every all-capitals word before synthesis.** It fixes EBITDA and turns every real
initialism (NBA, CBN) into a made-up word. Telling the two apart needs knowledge the code
doesn't have.

**Why a table here when 0011 rejected a dictionary.** 0011 rejected one for proper nouns,
because the supply of unfamiliar names never runs out. Acronyms said as words are a small set
that keeps recurring, especially in the finance reading, so a table fits this job.

## Consequences

- **The phonemes are unverified.** They are written in misaki's alphabet by hand. Each one
  should be checked against the deployment's `/dev/phonemize` and by listening, and 0011
  warns that bad IPA sounds worse than the default guess.
- The table covers only terms someone has already heard read wrong. New ones need a code
  change and a redeploy.
- Mixed-case output from the prompt rule, like "Opec", still shows in the displayed summary.
  That is milder than 0011's respellings, but it is the same display/narration tension.
