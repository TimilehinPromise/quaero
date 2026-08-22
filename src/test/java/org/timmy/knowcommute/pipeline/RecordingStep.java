package org.timmy.knowcommute.pipeline;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;

/** Test stand-in for the stage 4/5/6 steps, so the machine can be driven with no network. */
class RecordingStep implements PipelineStep {

    private final BookmarkStatus handles;
    private final Consumer<Bookmark> behaviour;
    private final AtomicInteger invocations = new AtomicInteger();

    RecordingStep(BookmarkStatus handles, Consumer<Bookmark> behaviour) {
        this.handles = handles;
        this.behaviour = behaviour;
    }

    static RecordingStep alwaysFailing(BookmarkStatus handles, String message) {
        return new RecordingStep(handles, bookmark -> {
            throw new IllegalStateException(message);
        });
    }

    @Override
    public BookmarkStatus handles() {
        return handles;
    }

    @Override
    public void execute(Bookmark bookmark) {
        invocations.incrementAndGet();
        behaviour.accept(bookmark);
    }

    int invocations() {
        return invocations.get();
    }
}
