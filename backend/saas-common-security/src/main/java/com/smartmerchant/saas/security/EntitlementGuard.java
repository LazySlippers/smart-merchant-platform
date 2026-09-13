package com.smartmerchant.saas.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.redis.core.StringRedisTemplate;

@Aspect
public class EntitlementGuard {
    private final StringRedisTemplate redis;

    public EntitlementGuard(StringRedisTemplate redis) { this.redis = redis; }

    @Around("@annotation(required)")
    public Object enforce(ProceedingJoinPoint invocation, RequiresEntitlement required) throws Throwable {
        check(required.value());
        return invocation.proceed();
    }

    public void check(String featureCode) {
        var context = TenantContextHolder.require();
        if (context.platform()) throw new AccessDeniedException("Platform identity cannot use merchant entitlements");
        Boolean enabled = redis.opsForSet().isMember(TenantRuntimeKeys.entitlements(context.tenantId()), featureCode);
        if (!Boolean.TRUE.equals(enabled)) throw new AccessDeniedException("Tenant entitlement is not enabled: " + featureCode);
    }

    public Long quota(String featureCode) {
        check(featureCode);
        Object value = redis.opsForHash().get(TenantRuntimeKeys.quotas(TenantContextHolder.requireTenantId()), featureCode);
        return value == null ? null : Long.valueOf(value.toString());
    }
}
