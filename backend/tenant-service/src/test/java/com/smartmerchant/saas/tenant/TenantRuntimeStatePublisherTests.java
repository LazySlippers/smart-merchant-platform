package com.smartmerchant.saas.tenant;

import com.smartmerchant.saas.tenant.application.TenantManagementService;
import com.smartmerchant.saas.tenant.application.TenantRuntimeStatePublisher;
import com.smartmerchant.saas.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRuntimeStatePublisherTests {
    @SuppressWarnings("unchecked")
    @Test
    void publishesStatusVersionAndOnlyEnabledEntitlements() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        when(redis.opsForHash()).thenReturn(hashes);
        var publisher = new TenantRuntimeStatePublisher(redis, true);
        var tenant = new Tenant(); tenant.setId(101L); tenant.setStatus("ACTIVE"); tenant.setVersion(3);

        publisher.publish(tenant, List.of(
                new TenantManagementService.Entitlement("marketing.coupon", true, 20L),
                new TenantManagementService.Entitlement("marketing.stored_value", false, 0L)));

        verify(values).set("saas:tenant:status:101", "ACTIVE:3");
        verify(redis).delete("saas:tenant:entitlements:101");
        verify(sets).add("saas:tenant:entitlements:101", "marketing.coupon");
        verify(redis).delete("saas:tenant:quotas:101");
        verify(hashes).putAll("saas:tenant:quotas:101", java.util.Map.of("marketing.coupon", "20"));
    }
}
