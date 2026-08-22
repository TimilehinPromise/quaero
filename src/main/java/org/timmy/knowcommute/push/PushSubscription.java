package org.timmy.knowcommute.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.timmy.knowcommute.support.Auditable;
import org.timmy.knowcommute.user.DeviceUser;

@Entity
@Getter
@Table(name = "push_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private DeviceUser user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    private PushSubscription(DeviceUser user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static PushSubscription of(DeviceUser user, String endpoint, String p256dh, String auth) {
        return new PushSubscription(user, endpoint, p256dh, auth);
    }
}
