package org.timmy.knowcommute.digest;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DigestScheduleRepository extends JpaRepository<DigestSchedule, UUID> {

    Optional<DigestSchedule> findByUserId(UUID userId);
}
