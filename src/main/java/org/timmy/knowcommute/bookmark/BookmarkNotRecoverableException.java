package org.timmy.knowcommute.bookmark;

public class BookmarkNotRecoverableException extends RuntimeException {

    public BookmarkNotRecoverableException(BookmarkStatus status) {
        super("Content can only be pasted for a BLOCKED or FAILED bookmark; this one is " + status);
    }
}
