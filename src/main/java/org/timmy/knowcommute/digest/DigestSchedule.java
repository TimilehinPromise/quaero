package org.timmy.knowcommute.digest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.timmy.knowcommute.support.Auditable;
import org.timmy.knowcommute.user.DeviceUser;

@Entity
@Getter
@Table(name = "digest_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DigestSchedule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private DeviceUser user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "cron_expression", nullable = false, length = 64)
    private String cronExpression;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    private DigestSchedule(DeviceUser user, String cronExpression, String timezone) {
        this.user = user;
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.active = true;
    }

    public static DigestSchedule forUser(DeviceUser user, String cronExpression, String timezone) {
        return new DigestSchedule(user, cronExpression, timezone);
    }

    public void reschedule(String cronExpression, String timezone, boolean active) {
        this.cronExpression = cronExpression;
        this.timezone = timezone;
        this.active = active;
    }

    public void recordRun(Instant ranAt, Instant nextRunAt) {
        this.lastRunAt = ranAt;
        this.nextRunAt = nextRunAt;
    }
}
