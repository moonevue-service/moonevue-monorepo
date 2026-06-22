package com.moonevue.gateway.service;

import com.moonevue.core.entity.ApiKey;
import com.moonevue.core.entity.Tenant;
import com.moonevue.core.enums.ApiKeyEnvironment;
import com.moonevue.core.enums.ApiKeyStatus;
import com.moonevue.core.repository.ApiKeyRepository;
import com.moonevue.core.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Criação, validação e revogação de API Keys.
 *
 * O segredo só existe em claro no instante da criação/rotação (retornado uma única vez).
 * Persistimos apenas o HMAC-SHA-256 do segredo com um pepper de servidor.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_ID_LENGTH = 10;
    private static final int SECRET_LENGTH = 40;

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final SecureRandom random = new SecureRandom();
    private final String pepper;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         TenantRepository tenantRepository,
                         @Value("${moonevue.gateway.api-keys.pepper}") String pepper) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
        this.pepper = pepper;
    }

    /** Resultado da criação: contém o segredo em claro, exibido uma única vez. */
    public record CreatedApiKey(ApiKey apiKey, String plaintextKey) {}

    @Transactional
    public CreatedApiKey create(Long tenantId, Long userId, String name,
                                ApiKeyEnvironment environment, List<String> scopes,
                                OffsetDateTime expiresAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da chave é obrigatório");
        }
        ApiKeyEnvironment env = environment != null ? environment : ApiKeyEnvironment.TEST;
        List<String> normalizedScopes = normalizeScopes(scopes);
        if (normalizedScopes.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos um escopo");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant não encontrado"));

        String keyId = generateUniqueKeyId();
        String secret = randomToken(SECRET_LENGTH);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setKeyId(keyId);
        apiKey.setSecretHash(hashSecret(secret));
        apiKey.setName(name.trim());
        apiKey.setEnvironment(env);
        apiKey.setScopes(String.join(",", normalizedScopes));
        apiKey.setStatus(ApiKeyStatus.ACTIVE);
        apiKey.setExpiresAt(expiresAt);
        apiKey.setCreatedBy(userId);

        apiKey = apiKeyRepository.save(apiKey);

        String plaintext = formatPlaintextKey(env, keyId, secret);
        log.info("[ApiKeyService] API Key criada tenant={} keyId={} env={}", tenantId, keyId, env);
        return new CreatedApiKey(apiKey, plaintext);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(Long tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public void revoke(Long tenantId, Long apiKeyId, Long userId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Chave não encontrada"));
        if (apiKey.getStatus() == ApiKeyStatus.REVOKED) {
            return;
        }
        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKey.setRevokedAt(OffsetDateTime.now());
        apiKey.setRevokedBy(userId);
        apiKeyRepository.save(apiKey);
        log.info("[ApiKeyService] API Key revogada tenant={} keyId={}", tenantId, apiKey.getKeyId());
    }

    @Transactional
    public CreatedApiKey rotate(Long tenantId, Long apiKeyId, Long userId) {
        ApiKey current = apiKeyRepository.findByIdAndTenantId(apiKeyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Chave não encontrada"));
        CreatedApiKey created = create(tenantId, userId, current.getName(),
                current.getEnvironment(), parseScopes(current.getScopes()), current.getExpiresAt());
        revoke(tenantId, apiKeyId, userId);
        return created;
    }

    /**
     * Valida a chave apresentada e retorna a entidade ativa correspondente.
     * Atualiza {@code lastUsedAt} de forma best-effort.
     */
    @Transactional
    public Optional<ApiKey> authenticate(String presentedKey) {
        ParsedKey parsed = parsePresentedKey(presentedKey);
        if (parsed == null) {
            return Optional.empty();
        }

        Optional<ApiKey> found = apiKeyRepository.findByKeyIdAndStatus(parsed.keyId(), ApiKeyStatus.ACTIVE);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ApiKey apiKey = found.get();

        if (apiKey.getEnvironment() != parsed.environment()) {
            return Optional.empty();
        }
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return Optional.empty();
        }
        if (!constantTimeEquals(apiKey.getSecretHash(), hashSecret(parsed.secret()))) {
            return Optional.empty();
        }

        apiKey.setLastUsedAt(OffsetDateTime.now());
        try {
            apiKeyRepository.save(apiKey);
        } catch (Exception e) {
            log.debug("[ApiKeyService] Falha não-crítica ao atualizar lastUsedAt: {}", e.getMessage());
        }
        return Optional.of(apiKey);
    }

    public List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ---- helpers ----

    private record ParsedKey(ApiKeyEnvironment environment, String keyId, String secret) {}

    private ParsedKey parsePresentedKey(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return null;
        }
        String value = presentedKey.trim();
        // formato: mvk_<live|test>_<keyId>_<secret>
        String[] parts = value.split("_", 4);
        if (parts.length != 4 || !"mvk".equals(parts[0])) {
            return null;
        }
        ApiKeyEnvironment env;
        switch (parts[1]) {
            case "live" -> env = ApiKeyEnvironment.LIVE;
            case "test" -> env = ApiKeyEnvironment.TEST;
            default -> { return null; }
        }
        if (parts[2].isBlank() || parts[3].isBlank()) {
            return null;
        }
        return new ParsedKey(env, parts[2], parts[3]);
    }

    private String formatPlaintextKey(ApiKeyEnvironment env, String keyId, String secret) {
        String envPart = env == ApiKeyEnvironment.LIVE ? "live" : "test";
        return "mvk_" + envPart + "_" + keyId + "_" + secret;
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null) {
            return List.of();
        }
        return scopes.stream()
                .filter(s -> s != null)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private String generateUniqueKeyId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = randomToken(KEY_ID_LENGTH);
            if (apiKeyRepository.findByKeyId(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um keyId único");
    }

    private String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String hashSecret(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar hash da API Key", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
