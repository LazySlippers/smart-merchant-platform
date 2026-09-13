package com.smartmerchant.saas.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntitlementGuardTests {
    @SuppressWarnings("unchecked")
    @Test
    void permitsOnlyFeaturesEnabledForTheCurrentTenant() {
        var redis = mock(StringRedisTemplate.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        when(redis.opsForHash()).thenReturn(hashes);
        when(sets.isMember(TenantRuntimeKeys.entitlements(101), "marketing.coupon")).thenReturn(true);
        when(sets.isMember(TenantRuntimeKeys.entitlements(101), "marketing.stored_value")).thenReturn(false);
        when(hashes.get(TenantRuntimeKeys.quotas(101), "marketing.coupon")).thenReturn("20");
        var guard = new EntitlementGuard(redis);
        var tenant = new TenantContext(101, 7, false, "TENANT_ALL", Set.of());

        assertThatCode(() -> TenantContextHolder.callAs(tenant, () -> { guard.check("marketing.coupon"); return null; }))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TenantContextHolder.callAs(tenant,
                () -> { guard.check("marketing.stored_value"); return null; }))
                .isInstanceOf(AccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThat(TenantContextHolder.callAs(tenant, () -> guard.quota("marketing.coupon"))).isEqualTo(20L);
    }
}
