# 0022 — Opening the API to a browser frontend on another origin

Date: 2026-09-26
Stage: post-8 — first frontend
Status: accepted

## Context

A PWA is being built in its own repo, on its own origin. Two things stopped a browser using the
API as it stood. There was no CORS configuration. And audio was only reachable through
`GET /audio/{id}`, which needs the `X-Device-Id` header and answers with a redirect, while an
`<audio>` element cannot send headers.

## Decision

**`GET /api/v1/audio/{id}/url` returns the presigned URL as JSON** (`{url, expiresAt}`). The
redirect endpoint stays for curl and native clients. The browser fetches the URL with the
header, then hands a plain URL to `<audio>`. Bytes still go straight from R2 and never cross
the VPS.

**CORS on `/api/**` for named origins only**, from `READCAST_ALLOWED_ORIGINS`. Empty means
same-origin only. Only GET, POST and DELETE, and only the `X-Device-Id` and `Content-Type`
headers.

## Alternatives

**Serving the frontend on the API's origin behind the proxy.** No CORS needed, but it ties
the two deploys together through the proxy config. The owner chose a separate origin.

**Device ID as a query parameter on the audio endpoint**, so `<audio src>` could hit the
redirect directly. It puts the only credential in URLs, where it ends up in logs, history and
Referer headers.

**Proxying the MP3 through the backend.** It keeps R2 private to the server, but it sends
every byte of playback through the VPS, which the redirect design exists to avoid.

**A wildcard origin.** The device ID is the entire identity, so any page allowed to call the
API could read and delete a library whose ID it learned.

## Consequences

- The presigned URL expires after `presign-ttl` (15 minutes). The frontend has to fetch it at
  play time and re-fetch it on a playback error.
- Offline caching (fetching the MP3 into Cache Storage) will also need a CORS rule on the R2
  bucket. Not done yet.
- The frontend's origin has to be added to the deployment's env before it works in production.
  Until it is, every browser call fails the preflight.
- The frontend contract is written up in `docs/FRONTEND_API.md`.
