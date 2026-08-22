# Extraction fixtures

Real captures, 2026-08-22, from URLs the project owner actually reads. Tests run against
these, never the network.

| File | Source | HTTP | Expect |
|---|---|---|---|
| `substack-article.html` | Substack post (after a redirect from `open.substack.com`) | 200 | ~2,244 words extracted |
| `x-longform-article.html` | X long-form Article | 200 | ~1,551 words; body in `div.x-article-body` |
| `cloudflare-challenge.html` | Nigerian news site behind Cloudflare | **403** | classified BLOCKED, never summarised |
| `facebook-auth-wall.html` | Facebook group permalink | **400** | classified BLOCKED, never summarised |

The two failures matter as much as the successes. `cloudflare-challenge.html` is a 6KB JS
interstitial titled "Just a moment..." — extracting it "successfully" would send the words
*Just a moment* to the summariser and bill us for it. A test must prove we refuse.
