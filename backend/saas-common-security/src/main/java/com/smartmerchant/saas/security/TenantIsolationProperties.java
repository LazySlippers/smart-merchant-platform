package com.smartmerchant.saas.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("saas.security.tenant-isolation")
public record TenantIsolationProperties(boolean enabled, Set<String> ignoredTables) {
    public TenantIsolationProperties {
        ignoredTables = ignoredTables == null ? Set.of() : ignoredTables.stream()
                .map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
