package com.smartmerchant.saas.merchant;

import com.smartmerchant.saas.merchant.application.StoreAccessGuard;
import com.smartmerchant.saas.merchant.domain.EmployeeProfile;
import com.smartmerchant.saas.merchant.infrastructure.EmployeeProfileMapper;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoreAccessGuardTests {
    private final EmployeeProfileMapper employees=mock(EmployeeProfileMapper.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final StoreAccessGuard guard=new StoreAccessGuard(employees,jdbc);
    @AfterEach void clear(){TenantContextHolder.clear();}

    @Test void tenantAllUsesTrustedTenantOnly(){TenantContextHolder.set(context("TENANT_ALL"));when(jdbc.queryForList("SELECT id FROM store WHERE tenant_id=?",Long.class,7L)).thenReturn(java.util.List.of(11L,12L));assertThat(guard.accessibleStoreIds()).containsExactly(11L,12L);}
    @Test void storeSelfUsesEmployeePrimaryStore(){TenantContextHolder.set(context("STORE_SELF"));var e=new EmployeeProfile();e.setPrimaryStoreId(11L);when(employees.selectOne(any())).thenReturn(e);assertThat(guard.canAccess(11L)).isTrue();assertThat(guard.canAccess(12L)).isFalse();}
    @Test void ownerSelfCannotAccessStoreData(){TenantContextHolder.set(context("OWNER_SELF"));assertThat(guard.accessibleStoreIds()).isEmpty();verifyNoInteractions(jdbc,employees);}
    private static TenantContext context(String scope){return new TenantContext(7L,9L,false,scope,Set.of());}
}
