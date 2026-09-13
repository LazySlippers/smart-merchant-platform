package com.smartmerchant.saas.security;

import java.util.Set;

public record TenantContext(
        long tenantId,
        long userId,
        boolean platform,
        String dataScope,
        Set<String> permissions
) {
    public TenantContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
