package com.moonevue.gateway.service;

import com.moonevue.core.entity.Client;
import com.moonevue.core.entity.ClientBankIntegration;
import com.moonevue.core.enums.Environment;
import com.moonevue.core.repository.ClientBankIntegrationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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

    private static final String METADATA_CUSTOMERS_BY_ENV = "customerIdsByEnvironment";

    private final ClientBankIntegrationRepository repository;
    private final ObjectMapper objectMapper;

    public ClientBankIntegrationService(ClientBankIntegrationRepository repository,
                                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
        return ensureIntegration(client, bankProvider, externalCustomerId, null);
    }

    @Transactional
    public ClientBankIntegration ensureIntegration(Client client,
                                                   String bankProvider,
                                                   String externalCustomerId,
                                                   Environment environment) {
        if (client == null || client.getId() == null) {
            throw new IllegalArgumentException("Cliente inválido para integração bancária");
        }
        if (bankProvider == null || bankProvider.isBlank()) {
            throw new IllegalArgumentException("Provedor bancário é obrigatório");
        }
        String provider = bankProvider.trim().toUpperCase();

        return repository.findByClientIdAndBankProvider(client.getId(), provider)
                .map(existing -> updateExternalIdIfNeeded(existing, externalCustomerId, environment))
                .orElseGet(() -> create(client, provider, externalCustomerId, environment));
    }

    @Transactional(readOnly = true)
    public Optional<String> findExternalCustomerId(Long clientId, String bankProvider) {
        return findExternalCustomerId(clientId, bankProvider, null);
    }

    @Transactional(readOnly = true)
    public Optional<String> findExternalCustomerId(Long clientId, String bankProvider, Environment environment) {
        if (clientId == null || bankProvider == null || bankProvider.isBlank()) {
            return Optional.empty();
        }
        String provider = bankProvider.trim().toUpperCase();
        return repository.findByClientIdAndBankProvider(clientId, provider)
                .flatMap(integration -> {
                    if (environment != null) {
                        String envCustomer = getCustomerIdFromMetadata(integration, environment);
                        if (isExternalCustomerId(envCustomer)) {
                            return Optional.of(envCustomer);
                        }
                        return Optional.empty();
                    }
                    return Optional.ofNullable(integration.getBankCustomerId())
                            .map(this::trimToNull)
                            .filter(this::isExternalCustomerId);
                });
    }

    private ClientBankIntegration create(Client client,
                                         String provider,
                                         String externalCustomerId,
                                         Environment environment) {
        ClientBankIntegration integration = new ClientBankIntegration();
        integration.setClient(client);
        integration.setBankProvider(provider);
        integration.setBankCustomerId(
                externalCustomerId != null && !externalCustomerId.isBlank()
                        ? externalCustomerId.trim()
                        : syntheticInternalId(provider, client.getId()));
        mergeEnvironmentCustomerId(integration, externalCustomerId, environment);
        ClientBankIntegration saved = repository.save(integration);
        log.debug("[ClientBankIntegration] criada clientId={} provider={} customerId={}",
                client.getId(), provider, saved.getBankCustomerId());
        return saved;
    }

    private ClientBankIntegration updateExternalIdIfNeeded(ClientBankIntegration existing,
                                                           String externalCustomerId,
                                                           Environment environment) {
        if (externalCustomerId != null && !externalCustomerId.isBlank()) {
            String current = existing.getBankCustomerId();
            boolean currentIsSynthetic = current == null || current.startsWith("internal:");
            String normalizedExternalId = externalCustomerId.trim();
            boolean metadataChanged = mergeEnvironmentCustomerId(existing, normalizedExternalId, environment);
            if (currentIsSynthetic && !normalizedExternalId.equals(current)) {
                existing.setBankCustomerId(normalizedExternalId);
                return repository.save(existing);
            }
            if (metadataChanged) {
                existing.setBankCustomerId(externalCustomerId.trim());
                return repository.save(existing);
            }
        }
        return existing;
    }

    private boolean mergeEnvironmentCustomerId(ClientBankIntegration integration,
                                               String externalCustomerId,
                                               Environment environment) {
        if (integration == null || environment == null || !isExternalCustomerId(externalCustomerId)) {
            return false;
        }

        Map<String, Object> metadata = parseMetadata(integration.getMetadata());
        Object mapObject = metadata.get(METADATA_CUSTOMERS_BY_ENV);

        Map<String, String> byEnvironment = new LinkedHashMap<>();
        if (mapObject instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    byEnvironment.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
        }

        String envKey = environment.name();
        String current = byEnvironment.get(envKey);
        if (externalCustomerId.equals(current)) {
            return false;
        }

        byEnvironment.put(envKey, externalCustomerId);
        metadata.put(METADATA_CUSTOMERS_BY_ENV, byEnvironment);
        integration.setMetadata(toMetadataJson(metadata));
        return true;
    }

    private String getCustomerIdFromMetadata(ClientBankIntegration integration, Environment environment) {
        if (integration == null || environment == null) {
            return null;
        }
        Map<String, Object> metadata = parseMetadata(integration.getMetadata());
        Object mapObject = metadata.get(METADATA_CUSTOMERS_BY_ENV);
        if (!(mapObject instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Object value = rawMap.get(environment.name());
        return value == null ? null : trimToNull(value.toString());
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[ClientBankIntegration] metadata inválida, recriando estrutura. idempotency-safe");
            return new LinkedHashMap<>();
        }
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar metadata de integração bancária", e);
        }
    }

    /**
     * Identificador interno sintético para provedores sem customer id externo.
     * Formato: {@code internal:<provider>:<clientId>}.
     */
    private String syntheticInternalId(String provider, Long clientId) {
        return "internal:" + provider.toLowerCase() + ":" + clientId;
    }

    private boolean isExternalCustomerId(String value) {
        return value != null && !value.startsWith("internal:");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
