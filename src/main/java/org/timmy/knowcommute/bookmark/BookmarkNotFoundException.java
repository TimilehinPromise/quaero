package org.timmy.knowcommute.bookmark;

import java.util.UUID;

public class BookmarkNotFoundException extends RuntimeException {

    public BookmarkNotFoundException(UUID id) {
        super("Bookmark " + id + " was not found");
    }
}
