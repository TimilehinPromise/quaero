package org.timmy.knowcommute.security;

import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.timmy.knowcommute.user.DeviceUser;
import org.timmy.knowcommute.user.DeviceUserRepository;

@Service
@RequiredArgsConstructor
public class DeviceIdentityService {

    private final DeviceUserRepository users;

    @Transactional
    public DeviceUser resolve(String rawDeviceId) {
        String deviceId = normalise(rawDeviceId);
        return users.findByDeviceId(deviceId)
                .orElseGet(() -> provision(deviceId));
    }

    private String normalise(String rawDeviceId) {
        if (rawDeviceId == null || rawDeviceId.isBlank()) {
            throw new UnauthenticatedException("X-Device-Id header is required");
        }
        try {
            return UUID.fromString(rawDeviceId.trim()).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException("X-Device-Id must be a UUID");
        }
    }

    private DeviceUser provision(String deviceId) {
        try {
            return users.saveAndFlush(DeviceUser.withDeviceId(deviceId));
        } catch (DataIntegrityViolationException e) {
            // Concurrent first requests from the same device race on the unique index.
            return users.findByDeviceId(deviceId)
                    .orElseThrow(() -> new UnauthenticatedException("Device could not be registered"));
        }
    }
}
