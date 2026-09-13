package com.smartmerchant.saas.merchant.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmerchant.saas.merchant.domain.EmployeeProfile;
import com.smartmerchant.saas.merchant.infrastructure.EmployeeProfileMapper;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Component
public class StoreAccessGuard {
    private final EmployeeProfileMapper employees; private final JdbcTemplate jdbc;
    public StoreAccessGuard(EmployeeProfileMapper employees, JdbcTemplate jdbc) { this.employees = employees; this.jdbc = jdbc; }

    public boolean canAccess(long storeId) { return accessibleStoreIds().contains(storeId); }
    public boolean canOperateAtStore(long storeId) {
        if (!canAccess(storeId)) return false;
        var context=TenantContextHolder.require();
        return employee(context.userId()).map(e -> Objects.equals(e.getPrimaryStoreId(),storeId) || jdbc.queryForObject(
                "SELECT COUNT(*) FROM employee_store WHERE tenant_id=? AND employee_id=? AND store_id=?",
                Long.class,context.tenantId(),e.getId(),storeId)>0).orElse(false);
    }
    public void requireAccess(long storeId) { if (!canAccess(storeId)) throw new ResponseStatusException(FORBIDDEN, "Store is outside the trusted data scope"); }
    public List<Long> accessibleStoreIds() {
        var context = TenantContextHolder.require();
        return switch (context.dataScope()) {
            case "TENANT_ALL" -> jdbc.queryForList("SELECT id FROM store WHERE tenant_id=?", Long.class, context.tenantId());
            case "STORE_SET" -> employee(context.userId()).map(e -> jdbc.queryForList(
                    "SELECT store_id FROM employee_store WHERE tenant_id=? AND employee_id=?", Long.class, context.tenantId(), e.getId())).orElse(List.of());
            case "STORE_SELF" -> employee(context.userId()).map(EmployeeProfile::getPrimaryStoreId).stream().filter(java.util.Objects::nonNull).toList();
            default -> List.of();
        };
    }
    private java.util.Optional<EmployeeProfile> employee(long userId) {
        return java.util.Optional.ofNullable(employees.selectOne(Wrappers.<EmployeeProfile>lambdaQuery().eq(EmployeeProfile::getUserId, userId).eq(EmployeeProfile::getStatus, "ACTIVE")));
    }
}
