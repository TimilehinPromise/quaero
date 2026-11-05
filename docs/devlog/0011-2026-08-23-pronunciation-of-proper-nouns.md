# 0011 — Pronunciation of proper nouns

Date: 2026-08-23
Stage: 5 — Summarization (prompt)
Status: accepted

## Context

Testing the deployed Kokoro's web UI, "Tatum" came out as "TAH-tum" rather than "TAY-tum".

Kokoro phonemises through misaki/espeak, which guesses from spelling and has no lexicon entry
for most proper nouns. `/dev/phonemize` shows exactly what it decided:

```
Tatum     -> tˈɑTəm      "TAH-tum"    wrong
Jayson    -> ʤˈAsən      "JAY-son"    right
Payton    -> pˈAtən      "PAY-ton"    right
Awabah    -> əwˈɑbə
NVIDIA    -> ɛnvˈɪdiə
```

The uppercase letters are misaki's own phoneme codes, not literal letters: **`A` is /eɪ/**
and **`T` is a flapped t**. So the engine says "ay" perfectly well — `Payton` proves it — it
simply guessed the wrong vowel for an unfamiliar name. This is a lexicon gap, not a defect.

It matters here more than it would elsewhere: the owner's reading list is Nigerian fintech and
NBA coverage, both dense with names espeak has never seen.

## Decision

**A prompt rule, not a pronunciation dictionary.** The summariser already writes the narration
script, so it respells unfamiliar proper nouns phonetically in ordinary letters — "Taytum",
"Moany-point" — and only where a reader would plausibly get it wrong on sight.

Two escape hatches remain, verified working against the deployment:

**Inline phoneme override** via misaki's markdown-link syntax, confirmed end to end through
the normal `/v1/audio/speech` endpoint:

```
Jayson [Tatum](/tˈAtəm/) scored thirty points.
  -> ʤˈAsən tˈAtəm skˈɔɹd
```

**Respelling by hand**, which needs no special syntax: `Taytum` -> `tˈATəm`.

`/dev/phonemize` is the tool for checking any word before trusting it.

## Alternatives

**A maintained pronunciation dictionary.** Precise and unbounded work — it only ever covers
names someone has already noticed being wrong, which is the wrong shape for an app that
ingests arbitrary articles.

**Emitting inline phoneme markup from the LLM.** Strippable for display and exact in
principle, but it asks a model to produce correct misaki IPA. Bad IPA sounds worse than the
default guess, and the failure is silent.

**Post-processing between summary and TTS.** Architecturally cleanest — the summary stays
clean and a separate pass produces narration — but it needs either a second model call or the
dictionary rejected above.

## Consequences

- **The stored summary now contains deliberate misspellings.** `summaryText` is returned by
  `GET /api/v1/bookmarks/{id}` and a frontend may display it, where "Taytum" reads as a typo.
  This is accepted for now because the audio is the product and the text is secondary. **The
  proper fix, if display ever matters, is to store narration text separately from display
  text** — one extra column and a second prompt output, not a redesign.
- **The rule is a judgement call handed to the model**, so it will sometimes respell a name
  that did not need it, or miss one that did. There is no assertion that can catch this in
  CI; it needs listening.
- Untested against real output. The rule went in before the first end-to-end run, so whether
  the model applies it sensibly is unknown.
