package com.moonevue.core.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashMap;
import java.util.List;

public class IntrospectedAuthToken extends AbstractAuthenticationToken {

    private final String email;

    public IntrospectedAuthToken(String email, List<SimpleGrantedAuthority> authorities,
                                 Long tenantId, Long userId) {
        super(authorities);
        this.email = email;
        var details = new HashMap<String, Object>();
        details.put("tenantId", tenantId);
        details.put("userId", userId);
        setDetails(details);
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return ""; }
    @Override public Object getPrincipal() { return email; }
}
