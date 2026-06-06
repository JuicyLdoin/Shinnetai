package net.ldoin.shinnetai.security;

import java.util.Collections;
import java.util.Set;

public record AuthenticationContext(
        boolean authenticated,
        String principal,
        Set<String> roles
) {

    public AuthenticationContext {
        roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
    }

    public static AuthenticationContext anonymous() {
        return new AuthenticationContext(false, "anonymous", Collections.emptySet());
    }

    public static AuthenticationContext authenticated(String principal, Set<String> roles) {
        return new AuthenticationContext(true, principal, roles);
    }
}