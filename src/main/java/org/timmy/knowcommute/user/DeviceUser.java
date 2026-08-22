package org.timmy.knowcommute.user;

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
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 64)
    private String timezone;

    private DeviceUser(String deviceId, String timezone) {
        this.deviceId = deviceId;
        this.timezone = timezone;
    }

    public static DeviceUser withDeviceId(String deviceId) {
        return new DeviceUser(deviceId, "UTC");
    }

    public void changeTimezone(String timezone) {
        this.timezone = timezone;
    }
}
