package org.timmy.knowcommute.lens;

import java.time.Instant;

public record ListenerProfileView(String profile, Instant updatedAt) {

    static ListenerProfileView from(ListenerProfile profile) {
        return new ListenerProfileView(profile.getProfileText(), profile.getUpdatedAt());
    }
}
