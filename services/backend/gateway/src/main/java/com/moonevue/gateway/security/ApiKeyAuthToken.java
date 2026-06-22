package com.moonevue.gateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashMap;
import java.util.List;

/**
 * Autenticação derivada de uma API Key válida.
 *
 * Expõe {@code tenantId} no mesmo formato de {@link com.moonevue.core.security.IntrospectedAuthToken}
 * (via {@code getDetails()} -> Map) para que os controllers permaneçam agnósticos à origem da
 * autenticação. As authorities carregam os escopos da chave (ex.: {@code charges:write}).
 */
public class ApiKeyAuthToken extends AbstractAuthenticationToken {

    private final String keyId;

    public ApiKeyAuthToken(String keyId, Long tenantId, Long apiKeyId, String environment, List<String> scopes) {
        super(scopes.stream().map(SimpleGrantedAuthority::new).map(a -> (org.springframework.security.core.GrantedAuthority) a).toList());
        this.keyId = keyId;
        var details = new HashMap<String, Object>();
        details.put("tenantId", tenantId);
        details.put("apiKeyId", apiKeyId);
        details.put("environment", environment);
        setDetails(details);
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return ""; }

    @Override public Object getPrincipal() { return keyId; }
}
