package com.smartmerchant.saas.security;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIsolationTests {
    private final TenantLineHandler handler = new TenantIsolationAutoConfiguration()
            .tenantLineHandler(new TenantIsolationProperties(true, Set.of("platform_plan")));

    @Test
    void rewritesQueriesWithTrustedTenantInsteadOfRequestParameters() {
        var interceptor = new ExposedTenantInterceptor(handler);
        String sql = TenantContextHolder.callAs(new TenantContext(101, 7, false, "TENANT_ALL", Set.of()),
                () -> interceptor.rewrite("SELECT * FROM orders o JOIN order_item i ON i.order_id=o.id WHERE o.status='PAID'"));

        assertThat(sql).contains("o.tenant_id = 101").contains("i.tenant_id = 101");
        assertThat(sql).doesNotContain("tenant_id = 202");
    }

    @Test
    void ignoresOnlyExplicitPlatformTablesAndRejectsPlatformBypass() {
        var interceptor = new ExposedTenantInterceptor(handler);
        String ignored = TenantContextHolder.callAs(new TenantContext(101, 7, false, "TENANT_ALL", Set.of()),
                () -> interceptor.rewrite("SELECT * FROM platform_plan"));
        assertThat(ignored).doesNotContain("tenant_id");

        assertThatThrownBy(() -> TenantContextHolder.callAs(
                new TenantContext(0, 1, true, "PLATFORM_ALL", Set.of()),
                () -> interceptor.rewrite("SELECT * FROM orders")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot bypass");
    }

    static final class ExposedTenantInterceptor extends TenantLineInnerInterceptor {
        ExposedTenantInterceptor(TenantLineHandler handler) { super(handler); }
        String rewrite(String sql) { return parserSingle(sql, null); }
    }
}
