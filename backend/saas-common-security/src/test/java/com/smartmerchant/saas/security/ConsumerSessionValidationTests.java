package com.smartmerchant.saas.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumerSessionValidationTests {
    @Test void exchangedMerchantTokensRequireLiveOwnerSessionAndFailClosed() {
        var properties=mock(SaasSecurityProperties.class);
        String secret="consumer-session-test-secret-at-least-32-bytes";
        when(properties.secret()).thenReturn(secret);when(properties.issuer()).thenReturn("test");
        var sessions=mock(ConsumerSessionVerifier.class);
        var decoder=new SaasSecurityAutoConfiguration().jwtDecoder(properties,sessions);
        var claims=JwtClaimsSet.builder().issuer("test").subject("consumer:12").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(7200))
                .claims(values->values.putAll(Map.of("actor_type","CONSUMER","tenant_id",61,"member_id",101,"user_id",12,"consumer_session","session-one"))).build();
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var signed=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        when(sessions.verify("session-one",12)).thenReturn(true);
        assertThat(decoder.decode(signed).getClaimAsString("actor_type")).isEqualTo("CONSUMER");
        when(sessions.verify("session-one",12)).thenReturn(false);
        assertThatThrownBy(()->decoder.decode(signed)).isInstanceOf(JwtValidationException.class);
        verify(sessions,times(2)).verify("session-one",12);
    }
}
