package com.smartmerchant.saas.merchant.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/** Internal producers authenticate with the shared internal key in the controller. */
@Configuration
public class AnalyticsInternalSecurityConfiguration {
    @Bean
    WebSecurityCustomizer analyticsInternalEndpoint() {
        return web -> web.ignoring().requestMatchers("/internal/v1/analytics/**");
    }
}
