package com.smartmerchant.saas.iam.config;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartmerchant.saas.security.TenantContextFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder; import org.springframework.security.oauth2.jwt.NimbusJwtEncoder; import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter; import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter; import org.springframework.security.web.SecurityFilterChain;
@Configuration @EnableConfigurationProperties(IamJwtProperties.class) public class IamSecurityConfiguration {
 @Bean JwtEncoder jwtEncoder(IamJwtProperties p){ return new NimbusJwtEncoder(new ImmutableSecret<>(p.secret().getBytes())); }
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain iamSecurity(HttpSecurity http,JwtAuthenticationConverter converter)throws Exception{return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health","/actuator/info","/api/auth/v1/login","/api/auth/v1/refresh","/internal/v1/tenants/*/owner","/internal/v1/store-accounts/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(r->r.jwt(j->j.jwtAuthenticationConverter(converter))).addFilterAfter(new TenantContextFilter(),BearerTokenAuthenticationFilter.class).build();}
}

