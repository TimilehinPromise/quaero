package org.timmy.knowcommute.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.timmy.knowcommute.RepositoryTest;

@RepositoryTest
class DeviceUserRepositoryTest {

    @Autowired
    private DeviceUserRepository repository;

    @Test
    void findsUserByDeviceId() {
        repository.saveAndFlush(DeviceUser.withDeviceId("device-a"));

        assertThat(repository.findByDeviceId("device-a")).isPresent();
        assertThat(repository.findByDeviceId("device-b")).isEmpty();
    }

    @Test
    void defaultsTimezoneToUtc() {
        DeviceUser saved = repository.saveAndFlush(DeviceUser.withDeviceId("device-c"));

        assertThat(saved.getTimezone()).isEqualTo("UTC");
    }

    @Test
    void stampsAuditTimestampsOnInsert() {
        DeviceUser saved = repository.saveAndFlush(DeviceUser.withDeviceId("device-d"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }
}
