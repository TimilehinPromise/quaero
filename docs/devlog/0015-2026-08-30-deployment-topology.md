# 0015 — Deployment: Coolify, a prebuilt image, and a host that was already occupied

Date: 2026-08-30
Stage: 7 — Deployment
Status: accepted

## Context

Stage 7 specifies a `Dockerfile`, a `docker-compose.yml` carrying app + Postgres 17, an Nginx
config for SSL termination, and GitHub Actions running `./mvnw verify`. It assumes an empty
Contabo VPS and a deployment assembled by hand.

The host is not empty. Kokoro already runs there from devlog 0010, Postgres already runs there,
an Nginx reverse proxy already fronts everything, and deployments are managed by Coolify with a
GitHub App connection. Coolify's own proxy is disabled — it could not issue the wildcard
certificate another application on the box needs — so TLS is Nginx's job and stays that way.

That reduces Stage 7 to two artifacts nobody else provides: an image, and something to build it.

The build is where the real constraint sits. Coolify builds on the deployment server by default,
and that server is the one running Kokoro. Synthesis is CPU-bound at 0.75x realtime, and devlog
0014's open question — why a streamed speech response gets cut mid-body — has "something starved
the origin for long enough that an intermediary gave up" as a live hypothesis. Running a Maven
build on those same cores during a synthesis is a way to cause the bug we are trying to fix.

## Decision

**CI builds the image; the host only pulls it.** GitHub Actions runs `./mvnw verify`, and on a
green build on `main` builds the Docker image *from the jar that run produced* and pushes it to
`ghcr.io/timilehinpromise/know-commute` tagged `latest` and the commit SHA. Nothing is compiled
twice, and the pushed artifact is the one the tests ran against.

**`compose.deploy.yaml` declares one service.** Postgres, Kokoro and Nginx are pre-existing
neighbours reached by container name over the shared Coolify network, so none of them appear in
it. `compose.yaml` stays exactly as it is — local development still wants its own throwaway
Postgres and Kokoro.

**The workflow triggers the deploy, not Coolify's push webhook.** Coolify's auto-deploy fires on
the push event, which happens *before* CI has built and pushed the image; it would deploy the
previous one. The workflow calls Coolify's deploy API after the push succeeds, so a deploy is
always behind a green build. This requires auto-deploy to be turned **off** on the Coolify
resource — with both enabled, every merge deploys twice, once with a stale image.

**`KOKORO_BASE_URL` defaults to `http://kokoro:8880/v1`.** Reaching Kokoro by container name
keeps synthesis off the public path, which devlog 0014 identified as the most likely place those
11-minute response bodies are being cut. Basic auth deactivates on its own: the credentials
default to blank and `TtsProperties.hasBasicAuth()` is false on blank, which is the production
path devlog 0010 designed for.

Actuator now exposes `health` and nothing else, explicitly. The app sits on a shared Docker
network where every neighbouring container can reach it directly, so an open `/actuator/env`
would be readable by all of them.

## Alternatives

**Let Coolify build from the Dockerfile on the VPS** — the path of least resistance, one fewer
moving part, and the GitHub App connection does everything. Rejected because of what shares the
box. A multi-minute Maven and Docker build stealing cores from Kokoro is not a background cost
here; it plausibly *is* the intermittent TTS failure.

**Put Postgres in the deploy compose as Stage 7 says** — self-contained, and the plan's own
words. Rejected because an instance already runs on the host with its own volume and backups;
a second one would be a silent second source of truth.

**Deploy the `:latest` tag by name only** — simpler than passing a tag. Kept as the default via
`READCAST_IMAGE_TAG`, but every build also pushes a SHA tag, because rolling back to "the commit
before the bad one" is otherwise impossible once `latest` has moved.

## Consequences

The image is 466 MB. Most of that is one dependency layer — Spring AI, the AWS SDK, Netty and
PDFBox — which is stable across builds, so a code-only change pushes a few megabytes. It is
still a large first pull on a small VPS.

`.dockerignore` excludes everything except `target/know-commute-*.jar`. That makes the build
fast and hermetic, and it means **`docker build` fails on a clean checkout** until
`./mvnw package` has run. That is correct for CI and surprising for a human; anyone building
locally has to package first.

Coolify settings this depends on, which live outside the repo and will not fail loudly if wrong:
auto-deploy **off**, "Connect To Predefined Network" **on**, and three repository secrets —
`COOLIFY_URL`, `COOLIFY_TOKEN`, `COOLIFY_RESOURCE_UUID`. The workflow skips the deploy step with
a log line rather than failing when they are absent, so a missing secret shows up as "the deploy
silently stopped happening" rather than a red build.

The GitHub Actions runner needs Docker for Testcontainers. It has it, but this makes the test
suite's Docker dependency a CI requirement rather than a local convenience.
