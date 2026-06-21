package com.moonevue.gateway.service;

import com.moonevue.core.entity.Client;
import com.moonevue.core.entity.ClientBankIntegration;
import com.moonevue.core.repository.ClientBankIntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante que exista um registro de integração bancária para um cliente em um
 * determinado provedor. Centraliza a regra "um cliente pode existir em N bancos",
 * eliminando tratamentos específicos da EFI espalhados pela aplicação.
 *
 * <p>Para provedores sem customer id externo real (ex.: EFI), gera-se um
 * identificador interno sintético, mantendo a mesma estrutura dos demais bancos.
 * Para provedores com customer id próprio (ASAAS, Inter, ...), basta informar o
 * id retornado pelo banco em {@link #ensureIntegration(Client, String, String)}.
 */
@Service
public class ClientBankIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ClientBankIntegrationService.class);

    private final ClientBankIntegrationRepository repository;

    public ClientBankIntegrationService(ClientBankIntegrationRepository repository) {
        this.repository = repository;
    }

    /**
     * Garante a integração de um cliente com um provedor sem customer id externo
     * (ex.: EFI). Cria um identificador interno sintético quando o registro não existe.
     */
    @Transactional
    public ClientBankIntegration ensureIntegration(Client client, String bankProvider) {
        return ensureIntegration(client, bankProvider, null);
    }

    /**
     * Garante a integração de um cliente com um provedor.
     *
     * @param client          cliente interno (obrigatório)
     * @param bankProvider    provedor de pagamento (ex.: {@code EFI}, {@code ASAAS})
     * @param externalCustomerId customer id retornado pelo banco; quando {@code null},
     *                           gera-se um identificador interno sintético.
     */
    @Transactional
    public ClientBankIntegration ensureIntegration(Client client, String bankProvider, String externalCustomerId) {
        if (client == null || client.getId() == null) {
            throw new IllegalArgumentException("Cliente inválido para integração bancária");
        }
        if (bankProvider == null || bankProvider.isBlank()) {
            throw new IllegalArgumentException("Provedor bancário é obrigatório");
        }
        String provider = bankProvider.trim().toUpperCase();

        return repository.findByClientIdAndBankProvider(client.getId(), provider)
                .map(existing -> updateExternalIdIfNeeded(existing, externalCustomerId))
                .orElseGet(() -> create(client, provider, externalCustomerId));
    }

    private ClientBankIntegration create(Client client, String provider, String externalCustomerId) {
        ClientBankIntegration integration = new ClientBankIntegration();
        integration.setClient(client);
        integration.setBankProvider(provider);
        integration.setBankCustomerId(
                externalCustomerId != null && !externalCustomerId.isBlank()
                        ? externalCustomerId.trim()
                        : syntheticInternalId(provider, client.getId()));
        ClientBankIntegration saved = repository.save(integration);
        log.debug("[ClientBankIntegration] criada clientId={} provider={} customerId={}",
                client.getId(), provider, saved.getBankCustomerId());
        return saved;
    }

    private ClientBankIntegration updateExternalIdIfNeeded(ClientBankIntegration existing, String externalCustomerId) {
        // Atualiza apenas quando recebemos um id externo real e o atual ainda é sintético/ausente.
        if (externalCustomerId != null && !externalCustomerId.isBlank()) {
            String current = existing.getBankCustomerId();
            boolean currentIsSynthetic = current == null || current.startsWith("internal:");
            if (currentIsSynthetic && !externalCustomerId.trim().equals(current)) {
                existing.setBankCustomerId(externalCustomerId.trim());
                return repository.save(existing);
            }
        }
        return existing;
    }

    /**
     * Identificador interno sintético para provedores sem customer id externo.
     * Formato: {@code internal:<provider>:<clientId>}.
     */
    private String syntheticInternalId(String provider, Long clientId) {
        return "internal:" + provider.toLowerCase() + ":" + clientId;
    }
}
