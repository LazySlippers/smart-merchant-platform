package com.smartmerchant.saas.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantStatusGlobalFilterTests {
    @Test void globalAccountWithoutTenantIsAllowedOnlyAtAccountEndpoints() {
        var filter=new TenantStatusGlobalFilter(mock(ReactiveStringRedisTemplate.class),true);
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("global").header("alg","none")
                .claim("actor_type","CONSUMER_ACCOUNT").claim("user_id",9L).build();
        for(var path:java.util.List.of("/api/consumer/v1/auth/account/enter","/api/consumer/v1/wallet","/api/merchant/v1/users")) {
            var exchange=MockServerWebExchange.from(MockServerHttpRequest.get(path)).mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
            var called=new AtomicBoolean();
            StepVerifier.create(filter.filter(exchange,e->{called.set(true);return Mono.empty();})).verifyComplete();
            assertThat(called.get()).isEqualTo(path.endsWith("/enter"));
            if(!called.get())assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
    @SuppressWarnings("unchecked")
    @Test
    void invalidatesExistingTokenImmediatelyWhenTenantVersionChanges() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("saas:tenant:status:101")).thenReturn(Mono.just("SUSPENDED:2"));
        var filter = new TenantStatusGlobalFilter(redis, true);
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg", "none")
                .claim("tenant_id", 101L).claim("tenant_version", 1L).claim("user_id", 7L)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/merchant/v1/users").build())
                .mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
        var called = new AtomicBoolean();
        GatewayFilterChain chain = value -> { called.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(called).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void activeMatchingVersionPassesThroughExactlyOnce() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("saas:tenant:status:101")).thenReturn(Mono.just("ACTIVE:3"));
        var filter = new TenantStatusGlobalFilter(redis, true);
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg", "none")
                .claim("tenant_id", 101L).claim("tenant_version", 3L).claim("user_id", 7L)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/merchant/v1/users").build())
                .mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GatewayFilterChain chain = value -> { calls.incrementAndGet(); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(calls).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
