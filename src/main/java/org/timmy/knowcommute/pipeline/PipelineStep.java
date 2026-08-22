package org.timmy.knowcommute.pipeline;

import org.timmy.knowcommute.bookmark.Bookmark;
import org.timmy.knowcommute.bookmark.BookmarkStatus;

/**
 * One transition of the bookmark state machine. Implementations run outside any transaction
 * and may block on network calls; they mutate the bookmark through its domain methods and the
 * worker persists the result.
 */
public interface PipelineStep {

    BookmarkStatus handles();

    void execute(Bookmark bookmark);
}
