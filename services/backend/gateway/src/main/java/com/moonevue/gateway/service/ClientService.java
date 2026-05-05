package com.moonevue.gateway.service;

import com.moonevue.core.entity.Client;
import com.moonevue.core.entity.Transaction;
import com.moonevue.core.enums.ClientStatus;
import com.moonevue.core.repository.ClientRepository;
import com.moonevue.core.repository.TransactionRepository;
import com.moonevue.core.repository.TenantRepository;
import com.moonevue.gateway.dto.ClientSummaryDTO;
import com.moonevue.gateway.dto.ClientUpsertRequest;
import com.moonevue.gateway.dto.TransactionSummaryDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;

    public ClientService(ClientRepository clientRepository,
                         TenantRepository tenantRepository,
                         TransactionRepository transactionRepository) {
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<ClientSummaryDTO> list(Long tenantId, int page, int size) {
        return clientRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ClientSummaryDTO get(Long tenantId, Long clientId) {
        Client client = clientRepository.findByTenantIdAndId(tenantId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        return toSummary(client);
    }

    @Transactional
    public ClientSummaryDTO create(Long tenantId, ClientUpsertRequest request) {
        Client client = new Client();
        client.setTenant(tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant não encontrado")));
        applyUpsert(client, request);
        client.setStatus(ClientStatus.ACTIVE);
        client = clientRepository.save(client);
        return toSummary(client);
    }

    @Transactional
    public ClientSummaryDTO update(Long tenantId, Long clientId, ClientUpsertRequest request) {
        Client client = clientRepository.findByTenantIdAndId(tenantId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        applyUpsert(client, request);
        client = clientRepository.save(client);
        return toSummary(client);
    }

    @Transactional(readOnly = true)
    public Page<TransactionSummaryDTO> listTransactions(Long tenantId, Long clientId, int page, int size) {
        return transactionRepository.findByTenantIdAndClientIdOrderByCreatedAtDesc(
                tenantId,
                clientId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toTransactionSummary);
    }

    private void applyUpsert(Client client, ClientUpsertRequest request) {
        client.setName(request.name().trim());
        client.setCpfCnpj(normalizeDocument(request.cpfCnpj()));
        client.setEmail(request.email().trim().toLowerCase());
        client.setPhone(request.phone() == null ? null : request.phone().trim());
    }

    private String normalizeDocument(String document) {
        return document == null ? null : document.replaceAll("[^0-9]", "");
    }

    private ClientSummaryDTO toSummary(Client client) {
        return new ClientSummaryDTO(
                client.getId(),
                client.getName(),
                client.getCpfCnpj(),
                client.getEmail(),
                client.getPhone(),
                client.getStatus().name(),
                client.getCreatedAt()
        );
    }

    private TransactionSummaryDTO toTransactionSummary(Transaction t) {
        return new TransactionSummaryDTO(
                t.getId(),
                t.getAmount(),
                t.getStatus(),
                t.getType(),
                t.getDescription(),
                t.getExternalReference(),
                t.getCheckoutToken(),
                t.getCheckoutToken() != null ? frontendUrl + "/checkout/" + t.getCheckoutToken() : null,
                t.getCheckoutExpiresAt(),
                t.getCheckoutInstrument(),
                t.getClient() != null ? t.getClient().getId() : null,
                t.getClient() != null ? t.getClient().getName() : null,
                t.getCheckoutAccessMode() != null ? t.getCheckoutAccessMode().name() : null,
                t.getBankAccount().getBank() != null ? t.getBankAccount().getBank().name() : null,
                t.getCreatedAt()
        );
    }
}
