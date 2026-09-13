package com.smartmerchant.saas.tenant.interfaces;

import com.smartmerchant.saas.tenant.application.TenantManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class TenantController {
    private final TenantManagementService service;
    public TenantController(TenantManagementService service){this.service=service;}
    @GetMapping("/api/merchant/v1/subscription") public Object subscription(){return service.currentPlan();}
    @GetMapping("/api/merchant/v1/subscription/upgrades") public Object upgrades(){return service.upgradePlans();}
    @PostMapping("/api/merchant/v1/subscription/upgrade") public Object upgrade(@Valid @RequestBody PlanRequest r){return service.upgradePlan(r.planCode());}
    @PostMapping({"/api/public/v1/tenant-applications","/api/platform/v1/tenant-applications"}) @ResponseStatus(HttpStatus.CREATED)
    public Object apply(@Valid @RequestBody ApplicationRequest r){ return service.apply(new TenantManagementService.CreateApplication(r.merchantName(),r.contactName(),r.contactMobile(),r.planCode(),r.password())); }
    @GetMapping("/api/public/v1/plans") public Object publicPlans(){return service.activePlans();}
    @GetMapping("/api/platform/v1/tenant-applications") @PreAuthorize("hasAuthority('tenant:application:review')") public Object applications(){return service.applications();}
    @PostMapping("/api/platform/v1/tenant-applications/{id}:approve") @PreAuthorize("hasAuthority('tenant:application:review')") public Object approve(@PathVariable long id){return service.approve(id);}
    @PostMapping("/api/platform/v1/tenant-applications/{id}:reject") @PreAuthorize("hasAuthority('tenant:application:review')") public Object reject(@PathVariable long id){return service.reject(id);}
    @GetMapping("/api/platform/v1/tenants") @PreAuthorize("hasAuthority('tenant:tenant:manage')") public Object tenants(){return service.tenantViews();}
    @PostMapping("/api/platform/v1/tenants/{id}:suspend") @PreAuthorize("hasAuthority('tenant:tenant:manage')") public Map<String,String> suspend(@PathVariable long id){service.changeStatus(id,"ACTIVE","SUSPENDED","TENANT_SUSPENDED");return Map.of("status","SUSPENDED");}
    @PostMapping("/api/platform/v1/tenants/{id}:resume") @PreAuthorize("hasAuthority('tenant:tenant:manage')") public Map<String,String> resume(@PathVariable long id){service.changeStatus(id,"SUSPENDED","ACTIVE","TENANT_RESUMED");return Map.of("status","ACTIVE");}
    @PutMapping("/api/platform/v1/tenants/{id}/plan") @PreAuthorize("hasAuthority('tenant:plan:manage')") public Object changePlan(@PathVariable long id,@Valid @RequestBody PlanRequest r){return service.changePlan(id,r.planCode());}
    @GetMapping("/api/platform/v1/plans") @PreAuthorize("hasAuthority('tenant:plan:manage')") public Object plans(){return service.planViews();}
    @PostMapping("/api/platform/v1/plans/{id}:enable") @PreAuthorize("hasAuthority('tenant:plan:manage')") public Map<String,String> enablePlan(@PathVariable long id){service.changePlanStatus(id,"ACTIVE");return Map.of("status","ACTIVE");}
    @PostMapping("/api/platform/v1/plans/{id}:disable") @PreAuthorize("hasAuthority('tenant:plan:manage')") public Map<String,String> disablePlan(@PathVariable long id){service.changePlanStatus(id,"DISABLED");return Map.of("status","DISABLED");}
    @GetMapping("/internal/v1/tenants/{id}/entitlements") @PreAuthorize("hasAuthority('tenant:tenant:manage') or #id == authentication.token.claims['tenant_id']") public Object entitlements(@PathVariable long id){return service.entitlements(id);}
    public record ApplicationRequest(@NotBlank @Size(max=128) String merchantName,@NotBlank @Size(max=64) String contactName,@NotBlank @Pattern(regexp="1[3-9]\\d{9}",message="请输入有效的手机号") String contactMobile,@NotBlank String planCode,@NotBlank @Size(min=8,max=64) String password){}
    public record PlanRequest(@NotBlank String planCode){}
}
