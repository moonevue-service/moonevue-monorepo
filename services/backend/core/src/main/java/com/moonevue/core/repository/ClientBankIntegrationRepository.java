package com.moonevue.core.repository;

import com.moonevue.core.entity.ClientBankIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientBankIntegrationRepository extends JpaRepository<ClientBankIntegration, Long> {

    Optional<ClientBankIntegration> findByClientIdAndBankProvider(Long clientId, String bankProvider);

    Optional<ClientBankIntegration> findByBankProviderAndBankCustomerId(String bankProvider, String bankCustomerId);
}
