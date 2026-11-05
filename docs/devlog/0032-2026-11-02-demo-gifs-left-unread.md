# 0032 — Animated demo GIFs are left unread

Date: 2026-11-02
Stage: 9 — Figures
Status: accepted

## Context

Medium articles carry product demos as animated GIFs, and the figure pipeline met two
distinct failure modes on them in one batch. A 13MB GIF exceeded the 5MB
`max-response-bytes` figure-fetch cap and never reached vision at all. A smaller animated
GIF reached vision and got `NONE` — vision models see the first frame of an animation, and
demos structure themselves like videos: the content is the motion, and frame one is often
an empty or neutral state. The question was whether recovering that motion was worth
building for.

## Decision

Accept the loss. No frame extraction, no size-cap raise, no multi-frame sampling. The
fetch cap stays; small animated GIFs keep their fair shot and vision keeps deciding; the
system treats an informed `NONE` as a complete answer.

The deciding evidence was a direct inspection of a real sample (the 13MB GIF from the
video-resizing article, extracted with ffmpeg): its first and middle frames were
near-identical annotated video frames — a subject-detection box on a cinematic wide shot.
The meaning lived in the box's movement across the sequence, so even a perfectly chosen
single frame would have recovered almost nothing the prose did not already say.

## Alternatives

**Rasterize a middle frame server-side and vision the PNG.** About forty lines at the
`FigureEnricher` fetch seam, no new dependency. Rejected on the evidence above: single
frames of real demo GIFs carry little information, and the cache-key and upload-size
benefits do not matter when the recovered content is empty.

**Sample start/middle/end frames into one multi-image vision call.** The only variant
that captures motion — the model can be asked what changes across the frames. Deferred,
not rejected: it costs one call and real frame-extraction work, for a yield observed at
roughly one marginal figure per demo-heavy article. Revisit only if articles keep
appearing whose prose depends on an animation the listener cannot see.

**Raise the figure size cap.** Pays 13MB downloads and uploads so the model can look at
frame one. Worst cost-to-recovery ratio of the three by a wide margin.

## Consequences

Figure recovery on demo-heavy articles caps at what static frames justify — one of four
on the observed video-resizing post, with the other three lost to the cap, to `NONE`, and
to the cut respectively. The losses are cheap: a capped fetch costs nothing downstream,
and a `NONE` costs about 1,700 tokens and is cached, so reprocessing never pays twice.
The gap is logged and greppable (`outcome=NONE` on `image/gif`), which is what makes
"revisit if it becomes common" a real option instead of a shrug.
