package com.smartmerchant.saas.iam.application;

import com.smartmerchant.saas.security.TenantRuntimeKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TenantRuntimeStateReader {
    private final StringRedisTemplate redis;
    private final boolean enabled;

    public TenantRuntimeStateReader(StringRedisTemplate redis,
                                    @Value("${saas.security.runtime-state.enabled:false}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public long requireActive(long tenantId) {
        if (tenantId == 0 || !enabled) return 0;
        String value = redis.opsForValue().get(TenantRuntimeKeys.status(tenantId));
        if (value == null) throw new AccessDeniedException("Tenant runtime state is unavailable");
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || !"ACTIVE".equals(parts[0])) throw new AccessDeniedException("Tenant is suspended");
        try { return Long.parseLong(parts[1]); }
        catch (NumberFormatException error) { throw new AccessDeniedException("Tenant runtime state is invalid"); }
    }
}
