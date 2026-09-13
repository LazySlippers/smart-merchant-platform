package com.smartmerchant.saas.iam;

import com.smartmerchant.saas.iam.application.AuthService;
import com.smartmerchant.saas.iam.application.IamManagementService;
import com.smartmerchant.saas.iam.infrastructure.LoginAuditMapper;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.cloud.nacos.discovery.enabled=false")
class IamServiceApplicationTests {
    @Autowired IamManagementService management;
    @Autowired AuthService auth;
    @Autowired LoginAuditMapper audits;
    @Autowired JwtDecoder jwtDecoder;

    @Test
    void ownerProvisioningReportsCrossTenantUsernameAsConflict(){
        management.provisionOwnerHash(71001,"13900007101","hash-one","甲租户负责人","13900007101");
        assertThatThrownBy(()->management.provisionOwnerHash(71002,"13900007101","hash-two","乙租户负责人","13900007101"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("其他租户");
    }


    @Test
    void platformIdentityEndpointRejectsMerchantContext(){
        TenantContextHolder.callAs(new TenantContext(0,1,true,"PLATFORM_ALL",Set.of()),()->{
            assertThat(((java.util.Map<?,?>)management.platformMe()).get("platform")).isEqualTo(true);return null;
        });
        TenantContextHolder.callAs(new TenantContext(70001,1,false,"TENANT_ALL",Set.of()),()->{
            assertThatThrownBy(()->management.platformMe()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");return null;
        });
    }

    @Test
    void newStoreAccountUsesSelectedPermissionsFromItsFirstLogin(){
        management.provisionStoreAccount(70003,"store-selected-70003","StrongPass!123","新门店",java.util.List.of("merchant:order:verify"));
        assertThat(auth.login("store-selected-70003","StrongPass!123","127.0.0.1").permissions()).containsExactlyInAnyOrder("merchant:store:view","merchant:order:view","merchant:order:verify");
        assertThatThrownBy(()->management.provisionStoreAccount(70003,"invalid-store-70003","StrongPass!123","无效授权",java.util.List.of("iam:user:manage"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->auth.login("invalid-store-70003","StrongPass!123","127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyStoreAuthorizationIsPerAccountAndRefreshLoadsNewPermissions(){
        long tenant=70002L;
        var owner=management.provisionOwner(tenant,"owner-70002","StrongPass!123","老板",null);
        long[] ids=new long[2];
        TenantContextHolder.callAs(new TenantContext(tenant,owner.getId(),false,"TENANT_ALL",Set.of("iam:user:manage","iam:role:manage")),()->{
            var first=management.createUser("legacy-70002","StrongPass!123","旧核销员",null,"STORE_SELF");
            var second=management.createUser("second-70002","StrongPass!123","另一个店员",null,"STORE_SELF");
            ids[0]=first.getId();ids[1]=second.getId();
            var shared=management.createRole("OLD_VERIFY","旧核销权限");
            var permissionIds=management.permissions().stream().filter(p->Set.of("merchant:store:view","merchant:order:verify").contains(p.getPermissionCode())).map(p->p.getId()).toList();
            management.assignPermissions(shared.getId(),permissionIds);
            management.assignRoles(first.getId(),java.util.List.of(shared.getId()));management.assignRoles(second.getId(),java.util.List.of(shared.getId()));
            return null;
        });
        var before=auth.login("legacy-70002","StrongPass!123","127.0.0.1");
        assertThat(before.permissions()).doesNotContain("merchant:inventory:view");
        management.updateStorePermissions(tenant,ids[0],IamManagementService.STORE_PERMISSIONS);
        assertThat(auth.refresh(before.refreshToken()).permissions()).containsExactlyInAnyOrderElementsOf(IamManagementService.STORE_PERMISSIONS);
        assertThat(auth.login("second-70002","StrongPass!123","127.0.0.1").permissions()).containsExactlyInAnyOrder("merchant:store:view","merchant:order:verify");
        management.updateStorePermissions(tenant,ids[0],java.util.List.of("merchant:inventory:manage"));
        management.provisionStoreAccount(tenant,"new-store-70002","StrongPass!123","新门店");
        assertThat(auth.login("legacy-70002","StrongPass!123","127.0.0.1").permissions()).containsExactlyInAnyOrder("merchant:store:view","merchant:product:view","merchant:inventory:view","merchant:inventory:manage");
        assertThat(management.storeAccessAccounts(tenant,java.util.List.of(ids[0],ids[1],owner.getId()))).hasSize(2);
        assertThatThrownBy(()->management.updateStorePermissions(99999,ids[0],java.util.List.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->management.updateStorePermissions(tenant,owner.getId(),java.util.List.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->management.updateStorePermissions(tenant,ids[0],java.util.List.of("iam:user:manage"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void ownerRbacLoginRefreshAndAuditWorkTogether() {
        long tenantId = 70001L;
        var owner = management.provisionOwner(tenantId, "owner-70001", "StrongPass!123", "商户老板", "13900000000");

        var ownerContext = new TenantContext(tenantId, owner.getId(), false, "TENANT_ALL", Set.of(
                "iam:user:manage", "iam:role:manage"));
        TenantContextHolder.callAs(ownerContext, () -> {
            var employee = management.createUser("staff-70001", "StrongPass!456", "店员", "13700000000", "SELF");
            var role = management.createRole("CASHIER", "收银员");
            var permission = management.permissions().stream()
                    .filter(item -> item.getPermissionCode().equals("merchant:dashboard:view"))
                    .findFirst().orElseThrow();
            management.assignPermissions(role.getId(), java.util.List.of(permission.getId()));
            management.assignRoles(employee.getId(), java.util.List.of(role.getId()));
            return null;
        });

        var tokens = auth.login( "staff-70001", "StrongPass!456", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.permissions()).containsExactly("merchant:dashboard:view");
        assertThat(((Number) jwtDecoder.decode(tokens.accessToken()).getClaim("tenant_version")).longValue()).isZero();

        var rotated = auth.refresh(tokens.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(tokens.refreshToken());
        assertThatThrownBy(() -> auth.refresh(tokens.refreshToken()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(audits.selectList(null)).anySatisfy(audit -> {
            assertThat(audit.getUsername()).isEqualTo("staff-70001");
            assertThat(audit.getResult()).isEqualTo("SUCCESS");
        });
    }
}
