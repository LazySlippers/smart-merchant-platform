package com.smartmerchant.saas.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(@Value("${saas.security.jwt.secret}") String secret,
                                           @Value("${saas.security.jwt.issuer}") String issuer) {
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256")).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
    CorsWebFilter corsWebFilter() {
        var config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("WWW-Authenticate"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        return http.cors(cors -> { }).csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/consumer/v1/catalog/**").permitAll()
                        .pathMatchers("/api/consumer/v1/auth/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/payment/v1/callback/**").permitAll()
                        .pathMatchers(HttpMethod.POST,"/api/payment/v1/callback/**").permitAll()
                        .pathMatchers("/api/consumer/v1/**").access((authentication, context) -> authentication
                                .map(value -> new AuthorizationDecision(value instanceof JwtAuthenticationToken jwt && "CONSUMER".equals(jwt.getToken().getClaimAsString("actor_type")))).defaultIfEmpty(new AuthorizationDecision(false)))
                        .pathMatchers("/actuator/health", "/actuator/info", "/api/auth/v1/login",
                                "/api/auth/v1/refresh", "/api/public/v1/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/platform/v1/tenant-applications").permitAll()
                        .pathMatchers("/api/platform/**").access((authentication, context) -> authentication
                                .map(value -> new AuthorizationDecision(isPlatform(value))).defaultIfEmpty(new AuthorizationDecision(false)))
                        .pathMatchers("/api/merchant/**").access((authentication, context) -> authentication
                                .map(value -> new AuthorizationDecision(isMerchant(value))).defaultIfEmpty(new AuthorizationDecision(false)))
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> { }))
                .build();
    }

    private static boolean isPlatform(org.springframework.security.core.Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt && Boolean.TRUE.equals(jwt.getToken().getClaim("platform"));
    }

    private static boolean isMerchant(org.springframework.security.core.Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt && !Boolean.TRUE.equals(jwt.getToken().getClaim("platform")) && !"CONSUMER".equals(jwt.getToken().getClaimAsString("actor_type")) && !"CONSUMER_ACCOUNT".equals(jwt.getToken().getClaimAsString("actor_type"));
    }
}
