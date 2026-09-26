package org.timmy.knowcommute.extraction;

/** What the fetched HTML can support, decided before extraction runs. */
public enum PageKind {

    /** Enough prose to narrate at the normal minimum word count. */
    ARTICLE,

    /** A genuinely short page — a release note, an announcement — worth reading at a lower floor. */
    THIN,

    /** No readable text in the markup: a client-rendered app shell, or a near-empty page. */
    EMPTY
}
