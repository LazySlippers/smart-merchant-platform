package com.smartmerchant.saas.merchant;

import com.smartmerchant.saas.merchant.application.MerchantOrganizationService;
import com.smartmerchant.saas.merchant.application.StoreAccessGuard;
import com.smartmerchant.saas.merchant.infrastructure.EmployeeProfileMapper;
import com.smartmerchant.saas.merchant.infrastructure.StoreMapper;
import com.smartmerchant.saas.security.EntitlementGuard;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreQuotaTests {
    @Test
    @SuppressWarnings("unchecked")
    void standardPlanCannotCreateAThirdStore() {
        var stores=mock(StoreMapper.class);var guard=mock(EntitlementGuard.class);
        ObjectProvider<EntitlementGuard> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);when(guard.quota("merchant.store")).thenReturn(2L);
        when(stores.selectCount(any())).thenReturn(2L);
        var service=new MerchantOrganizationService(stores,mock(EmployeeProfileMapper.class),mock(JdbcTemplate.class),mock(StoreAccessGuard.class),provider);
        var tenant=new TenantContext(101,7,false,"TENANT_ALL", Set.of("merchant:store:manage"));

        assertThatThrownBy(() -> TenantContextHolder.callAs(tenant,
                () -> service.createStore("S-003","第三门店","测试地址","09:00-22:00")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("标准版最多可创建 2 个门店");
    }

    @Test
    @SuppressWarnings("unchecked")
    void newStoreIsExplicitlyPublishedWithPickupEnabled(){
        var stores=mock(StoreMapper.class);var jdbc=mock(JdbcTemplate.class);var guard=mock(EntitlementGuard.class);
        ObjectProvider<EntitlementGuard> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);when(guard.quota("merchant.store")).thenReturn(null);when(stores.selectCount(any())).thenReturn(0L);
        var service=new MerchantOrganizationService(stores,mock(EmployeeProfileMapper.class),jdbc,mock(StoreAccessGuard.class),provider);
        TenantContextHolder.callAs(new TenantContext(101,7,false,"TENANT_ALL",Set.of("merchant:store:manage")),()->service.createStore("S-001","新门店","测试地址","09:00-22:00"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("UPDATE store SET marketplace_listed=TRUE"),org.mockito.ArgumentMatchers.eq(101L),org.mockito.ArgumentMatchers.anyLong());
    }
}
