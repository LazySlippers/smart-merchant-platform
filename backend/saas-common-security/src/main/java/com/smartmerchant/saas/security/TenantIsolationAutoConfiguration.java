package com.smartmerchant.saas.security;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = SaasSecurityAutoConfiguration.class)
@EnableConfigurationProperties(TenantIsolationProperties.class)
@ConditionalOnProperty(prefix = "saas.security.tenant-isolation", name = "enabled", havingValue = "true")
public class TenantIsolationAutoConfiguration {
    @Bean
    TenantLineHandler tenantLineHandler(TenantIsolationProperties properties) {
        return new TenantLineHandler() {
            @Override public Expression getTenantId() {
                var context = TenantContextHolder.require();
                if (context.platform()) throw new IllegalStateException("Platform identity cannot bypass tenant SQL isolation");
                return new LongValue(context.tenantId());
            }
            @Override public String getTenantIdColumn() { return "tenant_id"; }
            @Override public boolean ignoreTable(String tableName) {
                return properties.ignoredTables().contains(tableName.toLowerCase());
            }
        };
    }

    @Bean @ConditionalOnMissingBean
    MybatisPlusInterceptor tenantMybatisPlusInterceptor(TenantLineHandler handler) {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(handler));
        return interceptor;
    }
}
