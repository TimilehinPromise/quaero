package org.timmy.knowcommute.lens;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListenerProfileRepository extends JpaRepository<ListenerProfile, UUID> {

    Optional<ListenerProfile> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
