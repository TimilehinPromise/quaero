# 0006 — Extraction, and the sites that refuse to be extracted

Date: 2026-08-22
Stage: 4 — Article extraction
Status: accepted

## Context

The owner supplied four URLs from his actual reading. Fetched from a datacentre IP, only two
returned an article:

| Source | HTTP | What came back |
|---|---|---|
| Substack post | 200 | 2,244 words. Also redirected `open.substack.com` → `codingchallenges.substack.com`. |
| X long-form Article | 200 | 1,551 words in `div.x-article-body`, server-rendered, not in a script blob. |
| Nigerian news site | **403** | 6KB Cloudflare interstitial titled "Just a moment…". The `/amp/` URL did not help. |
| Facebook group post | **400** | 1.5KB error page. Auth-walled. |

**Half the sample was unreachable, and not because extraction is weak — no HTML ever arrived.**
This reframes the risk in PRD §13, which rates "Article extraction fails" as *Medium* and
mitigates it with "fallback to raw HTML text extraction". There is no raw HTML to fall back
to. Falling back would mean summarising the words *Just a moment*.

An earlier assessment in this session was wrong and is corrected here: X was dismissed as
"not articles". X long-form Articles extract cleanly and are exactly the target content.

## Decision

**`BLOCKED` is a distinct terminal status**, added in changeset `005`. A refused fetch will be
refused again, so it skips the retry ladder entirely — no 3 attempts, no 90s of backoff.
`BookmarkPipeline` routes `SiteBlockedException` straight to `Bookmark.block(...)`.

**Blocks are detected two ways.** By status — 401, 402, 403, 407, 429, 451 — and by content,
because a Cloudflare challenge returns **HTTP 200** carrying a page that extracts to a handful
of words. `ArticleExtractor.rejectIfChallengePage` matches known challenge markers against a
short body and refuses before anything reaches the summariser. Without it we would pay an LLM
to summarise "Just a moment".

**Manual paste is the recovery path**, per PRD §13's own mitigation:
`POST /api/v1/bookmarks/{id}/content` accepts title and text for a `BLOCKED` or `FAILED`
bookmark and re-enters it at `SUMMARIZING`.

**DNS rebinding is now closed.** Devlog 0003 left it open. `ArticleFetcher` checks the address
it *actually connected to* via Reactor Netty's `doOnConnected`, before any request bytes are
written. A hostile resolver can no longer answer the validator and the client differently.

**Redirects are followed by hand**, `followRedirect(false)`, re-validating every hop. Auto-
following would let a public URL redirect into the private network on hop two.

**No unblocker vendor was bought.** Verified pricing: ScrapingBee Hobby is $19.99/mo for
75,000 credits, with a premium-proxy + JS request costing 25 credits. At ~125 blocked articles
a month that is 3,125 credits — **4% of the tier, at 100% of the price**, and $19.99 against a
<$30 total budget leaves nothing for TTS. ScraperAPI's free tier (1,000 credits/mo) plausibly
covers the whole blocked subset at $0. `ArticleFetcher` is therefore a seam an unblocker can
slot behind later; nothing is subscribed to now.

## Alternatives

**Treating blocks as ordinary failures.** Simpler, and wastes three fetches plus two minutes of
backoff on something that cannot succeed, then leaves the user with a dead library entry and no
recovery.

**Buying an unblocker now.** The sample size for "how much of my reading is blocked" is four.
The subscription floor, not the per-request cost, is what breaks the budget — so this is a
decision worth making on real data.

**A headless browser on the VPS.** Passes some challenges, but Playwright or similar on a
Contabo box is heavy, and it still loses to a determined bot-protection vendor.

## Consequences

- **Extraction quality is now testable and pinned.** Eight tests run against the four real
  captures in `src/test/resources/fixtures/`, offline. The two failures are as valuable as the
  successes — one test exists purely to prove a challenge page is refused.
- **`ArticleFetcher` is now covered** by nine tests against a real loopback HTTP server
  (`TestHttpServer`, built on the JDK's `com.sun.net.httpserver` — no mock-server dependency;
  redirect and status handling only mean anything against a socket that actually speaks HTTP).
  They cover 403 and 429 becoming `SiteBlockedException`, 503 staying retryable, redirect
  chains, the redirect limit, and a redirect into the private range being refused after
  exactly one request.

  Testing a fetcher whose job is refusing private addresses, against a server that can only
  live on loopback, needs care. The tests inject a `PrivateAddressPolicy` that is the real
  policy in every respect *except* loopback — `10.0.0.1` and `169.254.x` are still rejected,
  so the logic under test is genuine rather than stubbed out. No production switch was added;
  had one been, it would have been an SSRF footgun one misconfiguration away from live.

  The rebinding guard's test was then verified by disabling the guard and confirming that
  exactly that test fails. It also asserts the server received **zero** requests, proving the
  guard fires before request bytes are written rather than after a response comes back.
- **Site-specific selectors have entered the codebase.** `div.x-article-body` is first in
  `CONTENT_SELECTORS`. It earns its place, but this list is a maintenance liability that will
  rot silently when a site changes its markup — the fixtures are what will catch that.
- **The 150-word minimum will reject legitimate short posts.** Deliberate: a 40-word note does
  not make a listenable podcast. It is configurable via `readcast.extraction.minimum-words`.
  *(Superseded by 0020: genuinely short pages are read at a lowered floor, and EMPTY pages
  degrade to a metadata brief rather than failing.)*
- **`PENDING` now has a real handler**, so the scheduler does actual work for the first time.
  Bookmarks move to `SUMMARIZING` and stop there until stage 5.
