package com.moonevue.core.repository;

import com.moonevue.core.entity.Client;
import com.moonevue.core.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByTenantIdAndCpfCnpj(Long tenantId, String cpfCnpj);

    boolean existsByTenantIdAndCpfCnpj(Long tenantId, String cpfCnpj);

    Optional<Client> findByIdAndTenantId(Long id, Long tenantId);

    Page<Client> findByTenantId(Long tenantId, Pageable pageable);

    Page<Client> findByTenantIdAndStatus(Long tenantId, ClientStatus status, Pageable pageable);

    List<Client> findByTenantIdAndEmailIn(Long tenantId, Collection<String> emails);
}