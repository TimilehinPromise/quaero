# 0017 — Narration is synthesised in chunks, and the parts are joined without re-encoding

Date: 2026-08-31
Stage: 6 — TTS and audio storage (revisited)
Status: accepted

## Context

Every LONG bookmark was failing at `GENERATING_AUDIO`. The step sent the whole narration in
one request and blocked on the response, so a ~1,700-word summary — roughly twelve minutes of
speech — asked Kokoro for twelve minutes of audio in a single call. Kokoro runs on CPU on the
speech host at a real-time factor around 1.2, and two concurrent slots split the same cores,
so the call ran past the 15-minute client timeout. okhttp's call timeout closed the socket
mid-body-read, which reached the pipeline as `InterruptedIOException: timeout` wrapping
`SocketException: Socket closed` and looked like a network fault rather than a capacity one.

The cost was the shape of the failure, not just the failure. Each attempt burned about fifteen
minutes and produced nothing, and `max-attempts=3` meant roughly forty-five minutes to reach a
terminal `FAILED`. There was no partial progress to keep.

## Decision

The narration is cut into pieces of at most `readcast.tts.chunk-chars` (1200), synthesised one
request per piece, and the returned MP3s are concatenated.

Cuts land on paragraph breaks first and sentence breaks second. A speech model already renders
a falling contour and a gap at a full stop, so a seam placed there is a pause the listener
expects; a cut mid-sentence is audible because the model closes the first fragment as a
finished thought and opens the second as a new one. Pieces are packed up toward the limit
rather than split as small as possible — each request is synthesised without its surrounding
text, and a model handed one short sentence stresses it as a standalone utterance.

`readcast.tts.timeout` is now a per-chunk timeout and drops to 3m. Joining strips each part's
ID3v2 header, ID3v1 trailer, and leading Xing/Info frame: copied verbatim those tags land
mid-stream where a decoder reads them as audio, and the Xing frame counts describe one part.

`readcast.pipeline.lease` rises to 45m. Chunking bounds each request, not the total, and a
long narration is still a dozen sequential chunks.

## Alternatives

**Raise the timeout and keep one request.** Cheapest change, and it would work — until a
longer summary or a busier speech host moved the line again. It also keeps the property that
made this expensive: a failure anywhere costs the whole narration.

**Request PCM, trim the silence Kokoro pads each generation with, and encode once at the end.**
Strictly better audio: no encoder delay at the seams and no accumulated padding. Rejected for
now because it needs an MP3 encoder dependency to buy a difference measured in tens of
milliseconds at points where the narration already pauses. Worth revisiting if the joins turn
out to be audible in practice.

**Cache completed chunks so a retry resumes.** The real fix for a host this slow — each
attempt would make progress instead of restarting. Deliberately deferred: it needs somewhere
to put partial audio and a way to invalidate it, and it is worth doing only if the speech host
stays CPU-bound.

**Move TTS to a hosted per-character API, or a serverless GPU.** Both solve the speed problem
that chunking does not. `SpeechModelConfig` speaks the OpenAI speech protocol, so the first is
a base-URL change. Neither was chosen here because the failure being fixed is reliability, and
a faster host with a one-shot request has the same all-or-nothing shape.

## Consequences

Chunking does not make synthesis faster. A dozen sequential chunks on the same CPU still take
roughly as long as the one request they replace; what changes is that a failure costs one
chunk instead of everything, and no single request is near any timeout.

The 45-minute lease is the honest price. A worker that dies mid-narration now holds its row for
45 minutes before another picks it up. That number is sized for a CPU speech host and should
come back down if the host gets a GPU.

Joined files carry no Xing header, so a player computes duration by scanning frames rather than
reading it. Kokoro's output is constant-bitrate, so this is correct, but a variable-bitrate
provider would report length wrongly and would need the PCM path instead.

Chunk boundaries are invisible to the model, so it cannot carry emphasis or pacing across one.
For narration read a paragraph at a time this does not show; for anything needing a long
crescendo it would.
