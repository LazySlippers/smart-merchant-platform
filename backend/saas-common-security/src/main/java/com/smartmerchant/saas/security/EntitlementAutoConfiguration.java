package com.smartmerchant.saas.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnProperty(prefix = "saas.security.entitlements", name = "enabled", havingValue = "true")
public class EntitlementAutoConfiguration {
    @Bean EntitlementGuard entitlementGuard(StringRedisTemplate redis) { return new EntitlementGuard(redis); }
}
