package com.smartmerchant.saas.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class TenantStatusGlobalFilter implements GlobalFilter, Ordered {
    private final ReactiveStringRedisTemplate redis;
    private final boolean enabled;

    public TenantStatusGlobalFilter(ReactiveStringRedisTemplate redis,
                                    @Value("${saas.security.runtime-state.enabled:false}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) return chain.filter(exchange);
        return exchange.getPrincipal().map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(optional -> {
            if (optional.isEmpty() || !(optional.get() instanceof JwtAuthenticationToken authentication)) return chain.filter(exchange);
            if ("CONSUMER_ACCOUNT".equals(authentication.getToken().getClaimAsString("actor_type"))) {
                return exchange.getRequest().getPath().value().startsWith("/api/consumer/v1/auth/account/")
                        ? chain.filter(exchange) : forbidden(exchange);
            }
            long tenantId = number(authentication.getToken().getClaim("tenant_id"), -1);
            if (tenantId == 0) return chain.filter(exchange);
            long tokenVersion = number(authentication.getToken().getClaim("tenant_version"), -1);
            if (tenantId < 0 || tokenVersion < 0) return forbidden(exchange);
            return redis.opsForValue().get("saas:tenant:status:" + tenantId)
                    .map(Optional::of).defaultIfEmpty(Optional.empty())
                    .flatMap(state -> state.isPresent() && state.get().equals("ACTIVE:" + tokenVersion)
                            ? chain.filter(exchange) : forbidden(exchange));
        });
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override public int getOrder() { return -50; }
}
