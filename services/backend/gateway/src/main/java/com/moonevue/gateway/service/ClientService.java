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
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }

        String document = normalizeDocument(request.cpfCnpj());
        if (!isValidCpfOrCnpj(document)) {
            throw new IllegalArgumentException("CPF/CNPJ inválido");
        }

        String email = request.email() == null ? null : request.email().trim().toLowerCase();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório");
        }

        String phone = normalizePhone(request.phone());
        if (phone != null && !isValidBrazilianPhone(phone)) {
            throw new IllegalArgumentException("Telefone inválido. Use DDD + número (10 ou 11 dígitos)");
        }

        client.setName(name);
        client.setCpfCnpj(document);
        client.setEmail(email);
        client.setPhone(phone);
    }

    private String normalizeDocument(String document) {
        return document == null ? null : document.replaceAll("[^0-9]", "");
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    private boolean isValidCpfOrCnpj(String document) {
        if (document == null) {
            return false;
        }
        if (document.length() == 11) {
            return isValidCpf(document);
        }
        if (document.length() == 14) {
            return isValidCnpj(document);
        }
        return false;
    }

    private boolean isValidCpf(String cpf) {
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }

        int d1 = cpfDigit(cpf.substring(0, 9), 10);
        int d2 = cpfDigit(cpf.substring(0, 9) + d1, 11);
        return cpf.equals(cpf.substring(0, 9) + d1 + d2);
    }

    private int cpfDigit(String base, int weightStart) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += (base.charAt(i) - '0') * (weightStart - i);
        }
        int mod = 11 - (sum % 11);
        return mod > 9 ? 0 : mod;
    }

    private boolean isValidCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14 || cnpj.chars().distinct().count() == 1) {
            return false;
        }

        int d1 = cnpjDigit(cnpj.substring(0, 12), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = cnpjDigit(cnpj.substring(0, 12) + d1, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return cnpj.equals(cnpj.substring(0, 12) + d1 + d2);
    }

    private int cnpjDigit(String base, int[] weights) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += (base.charAt(i) - '0') * weights[i];
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }

    private boolean isValidBrazilianPhone(String phone) {
        if (phone == null || (phone.length() != 10 && phone.length() != 11)) {
            return false;
        }
        if (phone.chars().distinct().count() == 1) {
            return false;
        }

        int ddd = Integer.parseInt(phone.substring(0, 2));
        if (ddd < 11 || ddd > 99) {
            return false;
        }

        if (phone.length() == 11) {
            // Celular no padrão brasileiro: nono dígito obrigatório.
            return phone.charAt(2) == '9';
        }
        return true;
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
                t.getCreatedAt(),
                null,
                null
        );
    }
}
