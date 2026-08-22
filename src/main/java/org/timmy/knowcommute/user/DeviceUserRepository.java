package org.timmy.knowcommute.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceUserRepository extends JpaRepository<DeviceUser, UUID> {

    Optional<DeviceUser> findByDeviceId(String deviceId);
}
