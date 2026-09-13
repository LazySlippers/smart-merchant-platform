package com.smartmerchant.saas.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false", "saas.security.runtime-state.enabled=false"})
class GatewayApplicationTests {
    private static final String SECRET = "dev-only-smart-merchant-jwt-secret-32-bytes-minimum";
    @LocalServerPort int port;
    @Autowired TrustedContextGlobalFilter trustedContext;

    @Test
    void platformAndMerchantIdentityDomainsCannotBeMixed() {
        var client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        client.get().uri("/api/platform/v1/tenants").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(false, 101))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/merchant/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(true, 0))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/platform/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(false, 101)).exchange().expectStatus().isForbidden();
        client.get().uri("/api/merchant/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(true, 0)).exchange().expectStatus().isForbidden();
        client.get().uri("/api/platform/v1/me").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/merchant/v1/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void forgedInternalHeadersAreReplacedByVerifiedJwtClaims() {
        var request = MockServerHttpRequest.get("/api/merchant/v1/users")
                .header("X-Tenant-Id", "999").header("X-User-Id", "888")
                .header("X-Internal-Caller", "attacker").header("X-Internal-Signature", "forged").build();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "none").claim("tenant_id", 101L).claim("user_id", 7L).claim("jti", "jti-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var exchange = MockServerWebExchange.from(request).mutate()
                .principal(Mono.just(new JwtAuthenticationToken(jwt))).build();
        var captured = new AtomicReference<org.springframework.web.server.ServerWebExchange>();
        GatewayFilterChain chain = value -> { captured.set(value); return Mono.empty(); };

        StepVerifier.create(trustedContext.filter(exchange, chain)).verifyComplete();

        var headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("101");
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(headers.getFirst("X-Internal-Caller")).isEqualTo("saas-gateway");
        assertThat(headers.getFirst("X-Internal-Signature")).isNotBlank().isNotEqualTo("forged");
    }

    @Test void unifiedConsumerIsNotMistakenForAMerchantWhenRuntimeChecksAreDisabled() {
        var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("smart-merchant-saas").subject("consumer-account:7")
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("actor_type","CONSUMER_ACCOUNT")
                .claim("user_id",7L).claim("platform",false).claim("permissions",List.of()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes()));
        var token=encoder.encode(JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        var client=WebTestClient.bindToServer().baseUrl("http://localhost:"+port).build();
        for(var path:List.of("/api/merchant/v1/users","/api/platform/v1/tenants","/api/consumer/v1/orders","/api/consumer/v1/wallet"))
            client.get().uri(path).header(HttpHeaders.AUTHORIZATION,"Bearer "+token).exchange().expectStatus().isForbidden();
    }

    private static String token(boolean platform, long tenantId) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("smart-merchant-saas").subject("7").issuedAt(now)
                .expiresAt(now.plusSeconds(300)).id(UUID.randomUUID().toString())
                .claim("user_id", 7L).claim("tenant_id", tenantId).claim("tenant_version", 0L)
                .claim("platform", platform).claim("data_scope", platform ? "PLATFORM_ALL" : "TENANT_ALL")
                .claim("permissions", List.of()).claim("token_type", "access").build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes()));
        return encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
