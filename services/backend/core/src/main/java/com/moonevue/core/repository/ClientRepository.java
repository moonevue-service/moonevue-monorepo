package com.moonevue.core.repository;

import com.moonevue.core.entity.Client;
import com.moonevue.core.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Page<Client> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    Optional<Client> findByTenantIdAndId(Long tenantId, Long id);

    Optional<Client> findByTenantIdAndCpfCnpjAndStatus(Long tenantId, String cpfCnpj, ClientStatus status);
}
