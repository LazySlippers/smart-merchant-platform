package com.smartmerchant.saas.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@AutoConfiguration
@EnableConfigurationProperties(SaasSecurityProperties.class)
@EnableMethodSecurity
public class SaasSecurityAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(SaasSecurityProperties properties, ConsumerSessionVerifier sessions) {
        var key = new SecretKeySpec(properties.secret().getBytes(), "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), jwt -> {
                    String sid=jwt.getClaimAsString("consumer_session");
                    if(sid==null && !"CONSUMER_ACCOUNT".equals(jwt.getClaimAsString("actor_type")))
                        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
                    Number owner=jwt.getClaim("user_id");
                    if(sid!=null && owner!=null && sessions.verify(sid,owner.longValue()))
                        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
                    return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token","Consumer session requires verification",null));
                }));
        return decoder;
    }

    @Bean @ConditionalOnMissingBean
    ConsumerSessionVerifier consumerSessionVerifier(
            @org.springframework.beans.factory.annotation.Value("${saas.security.member-service-url:${saas.trade.member-service-url:${MEMBER_SERVICE_URL:http://localhost:8084}}}") String url,
            @org.springframework.beans.factory.annotation.Value("${saas.security.internal-signing-key}") String key) {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);factory.setReadTimeout(3000);
        var client=org.springframework.web.client.RestClient.builder().baseUrl(url).requestFactory(factory).build();
        return (sid,owner)->{
            try {
                return Boolean.TRUE.equals(client.post().uri("/internal/v1/member/consumer-session/verify")
                        .header("X-Internal-Key",key).body(java.util.Map.of("sessionId",sid,"accountId",owner))
                        .retrieve().body(Boolean.class));
            } catch(org.springframework.web.client.RestClientException e) { return false; }
        };
    }

    @Bean @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var granted = new JwtGrantedAuthoritiesConverter();
        granted.setAuthoritiesClaimName(JwtClaimNames.PERMISSIONS);
        granted.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(granted);
        return converter;
    }

    @Bean @ConditionalOnMissingBean
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Preserve the business HTTP status during the servlet's internal error dispatch.
                        // A direct request to /error remains authenticated below.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/consumer/v1/catalog/**").permitAll()
                        .requestMatchers("/api/consumer/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payment/v1/callback/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/payment/v1/callback/**").permitAll()
                        .requestMatchers("/internal/v1/trade/**").permitAll()
                        .requestMatchers("/internal/v1/member/**").permitAll()
                        .requestMatchers("/internal/v1/analytics/**").permitAll()
                        .requestMatchers("/internal/v1/store-accounts/**").permitAll()
                        .requestMatchers("/api/consumer/v1/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
