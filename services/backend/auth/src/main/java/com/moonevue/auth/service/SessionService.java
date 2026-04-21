package com.moonevue.auth.service;

import com.moonevue.core.entity.Session;
import com.moonevue.core.entity.User;
import com.moonevue.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessions;

    @Value("${moonevue.auth.cookie.max-age-seconds}") private long maxAge;
    @Value("${moonevue.auth.cookie.renew-threshold-seconds}") private long renewThreshold;

    public Session create(User user, String ip, String ua) {
        var s = new Session();
        s.setUser(user);
        var now = OffsetDateTime.now();
        s.setCreatedAt(now);
        s.setLastSeenAt(now);
        s.setExpiresAt(now.plusSeconds(maxAge));
        s.setIpAddress(ip);
        s.setUserAgent(ua);
        return sessions.save(s);
    }

    public Optional<Session> findActive(UUID id) {
        return sessions.findByIdAndRevokedFalse(id)
                .filter(s -> s.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    public Optional<Session> findActiveByUser(User user) {
        return sessions.findFirstByUserAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(user, OffsetDateTime.now());
    }

    public void revoke(Session s) {
        s.setRevoked(true);
        sessions.save(s);
    }

    public boolean shouldRenew(Session s) {
        var remaining = java.time.Duration.between(OffsetDateTime.now(), s.getExpiresAt()).getSeconds();
        return remaining <= renewThreshold;
    }

    public Session touch(Session s) {
        s.setLastSeenAt(OffsetDateTime.now());
        s.setExpiresAt(OffsetDateTime.now().plusSeconds(maxAge));
        return sessions.save(s);
    }
}
