package com.smartmerchant.saas.tenant;

import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import com.smartmerchant.saas.tenant.application.TenantManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false","iam.provisioning.enabled=false"})
class TenantServiceApplicationTests {
    @Autowired TenantManagementService service;

    @Test void selfUpgradeIsTenantScopedAndCannotDowngrade(){
        var request=service.apply(new TenantManagementService.CreateApplication("升级测试","管理员","13900009991","STANDARD","test-password"));
        var tenant=service.approve(request.getId());
        var owner=new TenantContext(tenant.getId(),991,false,"TENANT_ALL",Set.of("merchant:store:manage"));
        TenantContextHolder.callAs(owner,()->{
            assertThat(service.currentPlan().planCode()).isEqualTo("STANDARD");
            assertThat(service.upgradePlans()).extracting(TenantManagementService.PlanView::planCode).contains("ADVANCED");
            service.upgradePlan("ADVANCED");
            assertThat(service.currentPlan().planCode()).isEqualTo("ADVANCED");
            assertThat(service.entitlements(tenant.getId())).anySatisfy(e->{assertThat(e.featureCode()).isEqualTo("merchant.store");assertThat(e.quota()).isEqualTo(50);});
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.upgradePlan("STANDARD")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            return null;
        });
        TenantContextHolder.callAs(new TenantContext(tenant.getId(),992,false,"STORE_SELF",Set.of("merchant:store:manage")),()->{
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.upgradePlan("ADVANCED")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);return null;
        });
    }

    @Test
    void selfServiceApplicationRejectsDuplicatePendingMobile() {
        service.apply(new TenantManagementService.CreateApplication("自助入驻甲", "负责人", "13900009992", "STANDARD", "test-password"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.apply(new TenantManagementService.CreateApplication("自助入驻乙", "另一负责人", "13900009992", "ADVANCED", "other-password")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void platformCanRejectPendingApplicationAndCannotApproveItAfterwards(){
        var application=service.apply(new TenantManagementService.CreateApplication("拒绝测试", "负责人", "13900009993", "STANDARD", "test-password"));
        var platform=new TenantContext(0,9002,true,"PLATFORM_ALL",Set.of("tenant:application:review"));
        var rejected=TenantContextHolder.callAs(platform,()->service.reject(application.getId()));
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->TenantContextHolder.callAs(platform,()->service.approve(application.getId())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tenantLifecycleAndEntitlementsArePersisted() {
        var application = service.apply(new TenantManagementService.CreateApplication(
                "星河咖啡", "张经理", "13800000000", "STANDARD", "test-password"));

        var platform = new TenantContext(0, 9001, true, "PLATFORM_ALL", Set.of(
                "tenant:application:review", "tenant:tenant:manage"));
        var tenant = TenantContextHolder.callAs(platform, () -> service.approve(application.getId()));

        assertThat(tenant.getStatus()).isEqualTo("ACTIVE");
        assertThat(service.entitlements(tenant.getId()))
                .extracting(TenantManagementService.Entitlement::featureCode)
                .contains("merchant.store", "merchant.employee", "marketing.coupon");

        TenantContextHolder.callAs(platform, () -> {
            service.changeStatus(tenant.getId(), "ACTIVE", "SUSPENDED", "TENANT_SUSPENDED");
            service.changeStatus(tenant.getId(), "SUSPENDED", "ACTIVE", "TENANT_RESUMED");
            return null;
        });

        assertThat(service.tenants()).anySatisfy(item -> {
            assertThat(item.getId()).isEqualTo(tenant.getId());
            assertThat(item.getStatus()).isEqualTo("ACTIVE");
            assertThat(item.getVersion()).isEqualTo(2);
        });
    }
}
