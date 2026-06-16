package com.moonevue.gateway.service;

import com.moonevue.core.entity.Client;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.enums.ClientStatus;
import com.moonevue.core.repository.ClientRepository;
import com.moonevue.core.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste o cliente do checkout em uma transação independente para que o
 * cadastro sobreviva mesmo quando o processamento do pagamento falhar
 * (ex.: timeout na integração bancária) e a transação do pagamento sofrer rollback.
 */
@Service
public class CheckoutClientUpsertService {

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;

    public CheckoutClientUpsertService(ClientRepository clientRepository,
                                       TenantRepository tenantRepository) {
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long upsertActiveClient(Long tenantId, String document, String name, String email, String phone) {
        if (tenantId == null || document == null || name == null) {
            return null;
        }

        String normalizedDoc = document.replaceAll("[^0-9]", "");
        if (normalizedDoc.isBlank() || name.isBlank()) {
            return null;
        }

        Client client = clientRepository
                .findByTenantIdAndCpfCnpjAndStatus(tenantId, normalizedDoc, ClientStatus.ACTIVE)
                .orElseGet(() -> {
                    Client newClient = new Client();
                    Tenant tenant = tenantRepository.getReferenceById(tenantId);
                    newClient.setTenant(tenant);
                    newClient.setCpfCnpj(normalizedDoc);
                    newClient.setStatus(ClientStatus.ACTIVE);
                    return newClient;
                });

        client.setName(name);
        if (email != null && !email.isBlank()) {
            client.setEmail(email);
        } else if (client.getEmail() == null) {
            client.setEmail("");
        }
        if (phone != null && !phone.isBlank()) {
            client.setPhone(phone);
        }

        client = clientRepository.save(client);
        return client.getId();
    }
}
