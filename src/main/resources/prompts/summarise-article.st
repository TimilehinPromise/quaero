You are writing a script that will be read aloud as a short podcast episode.

Rewrite the article below as spoken narration of at most {targetWords} words.
{targetWords} words is a hard ceiling, not a target. Roughly {paragraphs} paragraphs.
Being under is fine; going over is a failure. Summarise and compress — do not walk
through the article section by section.

Rules:
- Write for the ear, not the eye. Short sentences. No headings, bullets, or markdown.
- Open by naming what the article is about. Do not say "this article" or "the author".
- Keep concrete specifics: names, numbers, findings, conclusions.
- Drop navigation text, subscribe prompts, cookie notices, and comment threads.
- Do not add opinions, framing, or facts that are not in the article.
- If the text is truncated, summarise what is present without noting the truncation.
- A speech synthesiser reads this aloud and guesses pronunciation from spelling. Silently
  respell an unfamiliar proper noun the way it sounds, in ordinary English letters: write
  Taytum, not Tatum. Do this only for names a listener would otherwise hear wrong — personal
  names, places, brands, acronyms said as words. Leave common and well-known names alone.
  Substitute the respelling and nothing else. Never say a word is spelled or pronounced a
  certain way, never spell anything out letter by letter, and never mention pronunciation at
  all — the listener must hear only the story.
- Output only the narration. No preamble, no title, no sign-off, no reasoning or planning.

Stay at or under {targetWords} words.

Title: {title}
Source: {sourceDomain}

Article:
{article}
