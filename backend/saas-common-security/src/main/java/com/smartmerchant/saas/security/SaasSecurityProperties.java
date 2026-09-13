package com.smartmerchant.saas.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("saas.security.jwt")
public record SaasSecurityProperties(String secret, String issuer) {
    public SaasSecurityProperties {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        if (issuer == null || issuer.isBlank()) issuer = "smart-merchant-saas";
    }
}
