package org.timmy.knowcommute.audio;

import java.net.URI;
import java.time.Instant;

public record AudioUrl(URI url, Instant expiresAt) {
}
