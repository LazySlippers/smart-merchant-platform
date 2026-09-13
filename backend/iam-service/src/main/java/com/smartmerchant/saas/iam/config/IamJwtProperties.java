package com.smartmerchant.saas.iam.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@ConfigurationProperties("saas.security.jwt") public record IamJwtProperties(String secret,String issuer,Duration accessTtl,Duration refreshTtl){ public IamJwtProperties { if(accessTtl==null)accessTtl=Duration.ofMinutes(30); if(refreshTtl==null)refreshTtl=Duration.ofDays(7); } }
