package com.smartmerchant.saas.tenant.application;

import com.smartmerchant.saas.security.TenantRuntimeKeys;
import com.smartmerchant.saas.tenant.domain.Tenant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TenantRuntimeStatePublisher {
    private final StringRedisTemplate redis;
    private final boolean enabled;

    public TenantRuntimeStatePublisher(StringRedisTemplate redis,
                                       @Value("${saas.security.runtime-state.enabled:false}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public void publish(Tenant tenant, Collection<TenantManagementService.Entitlement> entitlements) {
        if (!enabled) return;
        redis.opsForValue().set(TenantRuntimeKeys.status(tenant.getId()),
                tenant.getStatus() + ":" + tenant.getVersion());
        String entitlementKey = TenantRuntimeKeys.entitlements(tenant.getId());
        redis.delete(entitlementKey);
        String[] enabledFeatures = entitlements.stream().filter(TenantManagementService.Entitlement::enabled)
                .map(TenantManagementService.Entitlement::featureCode).toArray(String[]::new);
        if (enabledFeatures.length > 0) redis.opsForSet().add(entitlementKey, enabledFeatures);
        String quotaKey = TenantRuntimeKeys.quotas(tenant.getId());
        redis.delete(quotaKey);
        Map<String,String> quotas = entitlements.stream().filter(TenantManagementService.Entitlement::enabled)
                .filter(item -> item.quota() != null)
                .collect(Collectors.toMap(TenantManagementService.Entitlement::featureCode,
                        item -> Long.toString(item.quota())));
        if (!quotas.isEmpty()) redis.opsForHash().putAll(quotaKey, quotas);
    }
}
