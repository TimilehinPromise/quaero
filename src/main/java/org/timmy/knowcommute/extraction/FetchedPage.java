package org.timmy.knowcommute.extraction;

import java.net.URI;

public record FetchedPage(URI finalUrl, String html) {
}
