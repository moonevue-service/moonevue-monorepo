package com.moonevue.core.repository;

import com.moonevue.core.entity.ApiKey;
import com.moonevue.core.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyId(String keyId);

    Optional<ApiKey> findByKeyIdAndStatus(String keyId, ApiKeyStatus status);

    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<ApiKey> findByIdAndTenantId(Long id, Long tenantId);
}
