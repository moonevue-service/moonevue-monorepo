package com.moonevue.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String hashRequest(Object body) {
        try {
            String serialized = objectMapper.writeValueAsString(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar hash de idempotência", e);
        }
    }

    public Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key) {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT scope, idem_key, request_hash, response_status, response_body::text
                    FROM idempotency_keys
                    WHERE scope = ? AND idem_key = ?
                    """,
                    rs -> {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(new IdempotencyRecord(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                (Integer) rs.getObject(4),
                                rs.getString(5)
                        ));
                    },
                    scope,
                    key
            );
        } catch (DataAccessException e) {
            log.warn("[IdempotencyService] Tabela de idempotência indisponível; seguindo sem replay. motivo={}", e.getMessage());
            return Optional.empty();
        }
    }

    public void reserve(String scope, String key, String requestHash) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO idempotency_keys(scope, idem_key, request_hash)
                    VALUES (?, ?, ?)
                    ON CONFLICT (scope, idem_key) DO NOTHING
                    """,
                    scope,
                    key,
                    requestHash
            );
        } catch (DataAccessException e) {
            log.warn("[IdempotencyService] Falha ao reservar idempotência; seguindo sem persistência. motivo={}", e.getMessage());
        }
    }

    public void storeResponse(String scope, String key, int status, Object responseBody) {
        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar resposta idempotente", e);
        }

        try {
            jdbcTemplate.update(
                    """
                    UPDATE idempotency_keys
                    SET response_status = ?, response_body = CAST(? AS jsonb)
                    WHERE scope = ? AND idem_key = ?
                    """,
                    status,
                    responseJson,
                    scope,
                    key
            );
        } catch (DataAccessException e) {
            log.warn("[IdempotencyService] Falha ao persistir resposta idempotente. motivo={}", e.getMessage());
        }
    }

    public Map<String, Object> parseResponseBody(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta idempotente corrompida", e);
        }
    }

    public <T> T parseResponseBody(String responseBody, Class<T> responseType) {
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta idempotente corrompida", e);
        }
    }

    public record IdempotencyRecord(
            String scope,
            String key,
            String requestHash,
            Integer responseStatus,
            String responseBody
    ) {}
}
