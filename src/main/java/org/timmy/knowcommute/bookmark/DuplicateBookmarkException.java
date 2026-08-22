package org.timmy.knowcommute.bookmark;

public class DuplicateBookmarkException extends RuntimeException {

    public DuplicateBookmarkException(String url) {
        super("This article is already in your library: " + url);
    }
}
