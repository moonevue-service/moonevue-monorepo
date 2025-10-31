package com.moonevue.core.repository;

import com.moonevue.core.entity.Session;
import com.moonevue.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByIdAndRevokedFalse(UUID id);

    Optional<Session> findFirstByUserAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(
            User user, OffsetDateTime now);

    long deleteByExpiresAtBefore(OffsetDateTime time);
}