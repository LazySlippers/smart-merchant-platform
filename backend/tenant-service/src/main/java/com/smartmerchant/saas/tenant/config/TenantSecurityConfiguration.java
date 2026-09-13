package com.smartmerchant.saas.tenant.config;
import com.smartmerchant.saas.security.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class TenantSecurityConfiguration {
 @Bean SecurityFilterChain tenantSecurity(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception { return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/info","/api/public/v1/**","/api/consumer/v1/catalog/**").permitAll().requestMatchers(HttpMethod.POST,"/api/platform/v1/tenant-applications").permitAll().anyRequest().authenticated()).oauth2ResourceServer(r->r.jwt(j->j.jwtAuthenticationConverter(converter))).addFilterAfter(new TenantContextFilter(),BearerTokenAuthenticationFilter.class).build(); }
}
