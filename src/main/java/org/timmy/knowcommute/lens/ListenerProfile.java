package org.timmy.knowcommute.lens;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.timmy.knowcommute.support.Auditable;

@Entity
@Getter
@Table(name = "listener_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListenerProfile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "profile_text", nullable = false, columnDefinition = "text")
    private String profileText;

    @Column(name = "profile_hash", nullable = false, length = 64)
    private String profileHash;

    private ListenerProfile(UUID userId, String profileText, String profileHash) {
        this.userId = userId;
        this.profileText = profileText;
        this.profileHash = profileHash;
    }

    static ListenerProfile of(UUID userId, String profileText, String profileHash) {
        return new ListenerProfile(userId, profileText, profileHash);
    }

    void replace(String profileText, String profileHash) {
        this.profileText = profileText;
        this.profileHash = profileHash;
    }
}
