package com.moonevue.gateway.security;

import com.moonevue.core.entity.ApiKey;
import com.moonevue.gateway.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica requisições da API pública ({@code /api/v1/**}) por API Key.
 *
 * Lê a chave de {@code Authorization: Bearer mvk_...} ou {@code X-API-Key}. Roda antes do
 * {@code SessionValidationFilter}; se a chave for inválida em uma rota da API pública responde
 * 401 imediatamente. Rotas fora de {@code /api/v1/} não são afetadas.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = new UrlPathHelper().getPathWithinApplication(req);
        boolean isPublicApi = path.startsWith(API_PREFIX);

        String presentedKey = extractKey(req);

        if (presentedKey == null) {
            // Sem credencial de API: deixa o fluxo seguir (sessão tratará rotas internas).
            chain.doFilter(req, res);
            return;
        }

        Optional<ApiKey> authenticated = apiKeyService.authenticate(presentedKey);
        if (authenticated.isEmpty()) {
            if (isPublicApi) {
                writeError(res, HttpStatus.UNAUTHORIZED, "invalid_api_key", "API Key inválida, expirada ou revogada");
                return;
            }
            chain.doFilter(req, res);
            return;
        }

        ApiKey apiKey = authenticated.get();
        var scopes = apiKeyService.parseScopes(apiKey.getScopes());
        String environment = apiKey.getEnvironment() != null ? apiKey.getEnvironment().name() : null;
        var token = new ApiKeyAuthToken(apiKey.getKeyId(), apiKey.getTenant().getId(), apiKey.getId(), environment, scopes);
        SecurityContextHolder.getContext().setAuthentication(token);

        chain.doFilter(req, res);
    }

    private String extractKey(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length()).trim();
            if (token.startsWith("mvk_")) {
                return token;
            }
        }
        String apiKeyHeader = req.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKeyHeader) && apiKeyHeader.startsWith("mvk_")) {
            return apiKeyHeader.trim();
        }
        return null;
    }

    private void writeError(HttpServletResponse res, HttpStatus status, String code, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
    }
}
