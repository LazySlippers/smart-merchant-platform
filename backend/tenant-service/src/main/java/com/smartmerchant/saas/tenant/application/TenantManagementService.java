package com.smartmerchant.saas.tenant.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.security.TenantContextHolder;
import com.smartmerchant.saas.tenant.domain.*;
import com.smartmerchant.saas.tenant.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDateTime;
import java.util.List;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class TenantManagementService {
    private final TenantApplicationMapper applications; private final TenantMapper tenants; private final PlanMapper plans;
    private final PlanFeatureMapper features; private final TenantSubscriptionMapper subscriptions; private final TenantAuditLogMapper audits;
    private final RestClient iam; private final String internalKey; private final boolean provisioningEnabled; private final TenantRuntimeStatePublisher runtimeState; private final BCryptPasswordEncoder passwordEncoder=new BCryptPasswordEncoder(12);
    public TenantManagementService(TenantApplicationMapper applications, TenantMapper tenants, PlanMapper plans, PlanFeatureMapper features, TenantSubscriptionMapper subscriptions, TenantAuditLogMapper audits,TenantRuntimeStatePublisher runtimeState,@Value("${iam.service.url:http://localhost:8082}") String iamUrl,@Value("${saas.security.internal-signing-key}") String internalKey,@Value("${iam.provisioning.enabled:true}") boolean provisioningEnabled) { this.applications=applications; this.tenants=tenants; this.plans=plans; this.features=features; this.subscriptions=subscriptions; this.audits=audits;this.runtimeState=runtimeState;this.iam=RestClient.builder().baseUrl(iamUrl).build();this.internalKey=internalKey;this.provisioningEnabled=provisioningEnabled; }

    @Transactional
    public TenantApplication apply(CreateApplication command) {
        if (plans.selectCount(Wrappers.<Plan>lambdaQuery().eq(Plan::getPlanCode, command.planCode()).eq(Plan::getStatus, "ACTIVE")) == 0) throw new IllegalArgumentException("Unknown active plan");
        var mobile=command.contactMobile().trim();
        if(tenants.selectCount(Wrappers.<Tenant>lambdaQuery().eq(Tenant::getOwnerMobile,mobile))>0||applications.selectCount(Wrappers.<TenantApplication>lambdaQuery().eq(TenantApplication::getContactMobile,mobile).in(TenantApplication::getStatus,"PENDING","APPROVED"))>0)throw new org.springframework.web.server.ResponseStatusException(CONFLICT,"该手机号已有待审核申请或已开通租户，请直接登录或联系平台处理");
        if(provisioningEnabled){var availability=iam.get().uri("/internal/v1/usernames/{username}/availability",mobile).header("X-Internal-Key",internalKey).retrieve().body(UsernameAvailability.class);if(availability==null||!availability.available())throw new ResponseStatusException(CONFLICT,"该手机号已有租户端账号，不能再次申请");}
        var now=LocalDateTime.now(); var item=new TenantApplication(); item.setId(Ids.next()); item.setMerchantName(command.merchantName().trim()); item.setContactName(command.contactName().trim()); item.setContactMobile(mobile); item.setPasswordHash(passwordEncoder.encode(command.password())); item.setRequestedPlanCode(command.planCode()); item.setStatus("PENDING"); item.setCreatedAt(now); item.setUpdatedAt(now); applications.insert(item); return item;
    }

    @Transactional
    public Tenant approve(long applicationId) {
        var application=applications.selectById(applicationId); if (application==null || !"PENDING".equals(application.getStatus())) throw new IllegalStateException("Application is not pending");
        var plan=plans.selectOne(Wrappers.<Plan>lambdaQuery().eq(Plan::getPlanCode, application.getRequestedPlanCode()).eq(Plan::getStatus,"ACTIVE")); if(plan==null) throw new IllegalStateException("Plan is unavailable");
        if(application.getPasswordHash()==null)throw new IllegalStateException("该历史申请未设置登录密码，请租户重新提交申请");var now=LocalDateTime.now(); var tenant=new Tenant(); tenant.setId(applicationId); tenant.setTenantCode(application.getContactMobile()); tenant.setTenantName(application.getMerchantName()); tenant.setStatus("ACTIVE"); tenant.setOwnerName(application.getContactName()); tenant.setOwnerMobile(application.getContactMobile()); tenant.setSourceApplicationId(applicationId); tenant.setVersion(0); tenant.setCreatedAt(now); tenant.setUpdatedAt(now); tenants.insert(tenant);
        var subscription=new TenantSubscription(); subscription.setId(Ids.next()); subscription.setTenantId(tenant.getId()); subscription.setPlanId(plan.getId()); subscription.setStatus("ACTIVE"); subscription.setStartsAt(now); subscription.setVersion(0); subscription.setCreatedAt(now); subscription.setUpdatedAt(now); subscriptions.insert(subscription);
        runtimeState.publish(tenant,entitlements(tenant.getId()));
        if(provisioningEnabled)try{iam.post().uri("/internal/v1/tenants/{id}/owner",tenant.getId()).header("X-Internal-Key",internalKey).body(new OwnerProvision(application.getContactMobile(),application.getPasswordHash(),application.getContactName(),application.getContactMobile())).retrieve().toBodilessEntity();}catch(HttpClientErrorException.Conflict conflict){throw new ResponseStatusException(CONFLICT,"该手机号已是其他租户的登录账号，请更换负责人手机号",conflict);}
        application.setStatus("APPROVED"); application.setReviewedBy(operatorId()); application.setReviewedAt(now); application.setUpdatedAt(now); applications.updateById(application); audit("TENANT_APPROVED","TENANT",tenant.getId(),"application="+applicationId); return tenant;
    }

    @Transactional public TenantApplication reject(long applicationId) {var application=applications.selectById(applicationId);if(application==null)throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"入驻申请不存在");if(!"PENDING".equals(application.getStatus()))throw new ResponseStatusException(CONFLICT,"该入驻申请已处理，请刷新后重试");var now=LocalDateTime.now();application.setStatus("REJECTED");application.setReviewedBy(operatorId());application.setReviewedAt(now);application.setUpdatedAt(now);applications.updateById(application);audit("TENANT_APPLICATION_REJECTED","APPLICATION",applicationId,"mobile="+application.getContactMobile());return application;}

    @Transactional public void changeStatus(long tenantId,String expected,String target,String action) { var updated=tenants.update(null,Wrappers.<Tenant>lambdaUpdate().eq(Tenant::getId,tenantId).eq(Tenant::getStatus,expected).set(Tenant::getStatus,target).setSql("version = version + 1").set(Tenant::getUpdatedAt,LocalDateTime.now())); if(updated!=1) throw new IllegalStateException("Tenant status conflict");var tenant=tenants.selectById(tenantId);runtimeState.publish(tenant,entitlements(tenantId)); audit(action,"TENANT",tenantId,expected+"->"+target); }
    public List<TenantApplication> applications(){ return applications.selectList(Wrappers.<TenantApplication>lambdaQuery().orderByDesc(TenantApplication::getCreatedAt)); }
    public List<Tenant> tenants(){ return tenants.selectList(Wrappers.<Tenant>lambdaQuery().orderByDesc(Tenant::getCreatedAt)); }
    public List<TenantView> tenantViews(){ return tenants().stream().map(t->{var subscription=subscriptions.selectOne(Wrappers.<TenantSubscription>lambdaQuery().eq(TenantSubscription::getTenantId,t.getId()).eq(TenantSubscription::getStatus,"ACTIVE"));var plan=subscription==null?null:plans.selectById(subscription.getPlanId());return new TenantView(t.getId(),t.getTenantCode(),t.getTenantName(),t.getStatus(),t.getOwnerName(),t.getOwnerMobile(),plan==null?null:plan.getPlanCode(),plan==null?null:plan.getPlanName());}).toList(); }
    @Transactional public TenantSubscription changePlan(long tenantId,String planCode){var tenant=tenants.selectById(tenantId);if(tenant==null)throw new IllegalArgumentException("Unknown tenant");var plan=plans.selectOne(Wrappers.<Plan>lambdaQuery().eq(Plan::getPlanCode,planCode).eq(Plan::getStatus,"ACTIVE"));if(plan==null)throw new IllegalArgumentException("Unknown active plan");var subscription=subscriptions.selectOne(Wrappers.<TenantSubscription>lambdaQuery().eq(TenantSubscription::getTenantId,tenantId));if(subscription==null)throw new IllegalStateException("Tenant subscription is missing");subscription.setPlanId(plan.getId());subscription.setStatus("ACTIVE");subscription.setUpdatedAt(LocalDateTime.now());subscription.setVersion(subscription.getVersion()+1);subscriptions.updateById(subscription);runtimeState.publish(tenant,entitlements(tenantId));audit("TENANT_PLAN_CHANGED","TENANT",tenantId,"plan="+planCode);return subscription;}
    public List<Plan> plans(){ return plans.selectList(Wrappers.<Plan>lambdaQuery().orderByAsc(Plan::getId)); }
    private long selfTenant(){var context=TenantContextHolder.require();if(context.platform()||!"TENANT_ALL".equals(context.dataScope())||!context.permissions().contains("merchant:store:manage"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"仅租户管理员可管理套餐");return context.tenantId();}
    public PlanView currentPlan(){long id=selfTenant();var subscription=subscriptions.selectOne(Wrappers.<TenantSubscription>lambdaQuery().eq(TenantSubscription::getTenantId,id).eq(TenantSubscription::getStatus,"ACTIVE"));if(subscription==null)throw new IllegalStateException("当前租户没有有效套餐");return planViews().stream().filter(p->p.id().equals(subscription.getPlanId())).findFirst().orElseThrow();}
    public List<PlanView> upgradePlans(){var current=currentPlan();return planViews().stream().filter(p->"ACTIVE".equals(p.status())&&!p.id().equals(current.id())&&isUpgrade(current.id(),p.id())).toList();}
    private boolean isUpgrade(long current,long target){
        var before=features.selectList(Wrappers.<PlanFeature>lambdaQuery().eq(PlanFeature::getPlanId,current));
        var after=features.selectList(Wrappers.<PlanFeature>lambdaQuery().eq(PlanFeature::getPlanId,target));
        boolean improved=false;
        for(var old:before){if(!Boolean.TRUE.equals(old.getEnabled()))continue;var next=after.stream().filter(f->f.getFeatureCode().equals(old.getFeatureCode())&&Boolean.TRUE.equals(f.getEnabled())).findFirst().orElse(null);if(next==null)return false;if(old.getQuotaValue()==null&&next.getQuotaValue()!=null)return false;if(old.getQuotaValue()!=null){if(next.getQuotaValue()!=null&&next.getQuotaValue()<old.getQuotaValue())return false;if(next.getQuotaValue()==null||next.getQuotaValue()>old.getQuotaValue())improved=true;}}
        return improved||after.stream().anyMatch(f->Boolean.TRUE.equals(f.getEnabled())&&before.stream().noneMatch(o->o.getFeatureCode().equals(f.getFeatureCode())&&Boolean.TRUE.equals(o.getEnabled())));
    }
    @Transactional public TenantSubscription upgradePlan(String code){long id=selfTenant();tenants.selectOne(Wrappers.<Tenant>lambdaQuery().eq(Tenant::getId,id).last("FOR UPDATE"));if(upgradePlans().stream().noneMatch(p->p.planCode().equals(code)))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"套餐不可升级，请刷新后重试");return changePlan(id,code);}
    public List<PlanView> planViews(){return plans().stream().map(plan->{var storeFeature=features.selectOne(Wrappers.<PlanFeature>lambdaQuery().eq(PlanFeature::getPlanId,plan.getId()).eq(PlanFeature::getFeatureCode,"merchant.store"));return new PlanView(plan.getId(),plan.getPlanCode(),plan.getPlanName(),plan.getStatus(),storeFeature==null?null:storeFeature.getQuotaValue());}).toList();}
    public List<Plan> activePlans(){ return plans.selectList(Wrappers.<Plan>lambdaQuery().eq(Plan::getStatus,"ACTIVE").orderByAsc(Plan::getId)); }
    @Transactional public void changePlanStatus(long planId,String target){var plan=plans.selectById(planId);if(plan==null)throw new IllegalArgumentException("Unknown plan");plan.setStatus(target);plan.setUpdatedAt(LocalDateTime.now());plans.updateById(plan);audit("PLAN_STATUS_CHANGED","PLAN",planId,"status="+target);}
    public List<Entitlement> entitlements(long tenantId){ var subscription=subscriptions.selectOne(Wrappers.<TenantSubscription>lambdaQuery().eq(TenantSubscription::getTenantId,tenantId).eq(TenantSubscription::getStatus,"ACTIVE")); if(subscription==null)return List.of(); return features.selectList(Wrappers.<PlanFeature>lambdaQuery().eq(PlanFeature::getPlanId,subscription.getPlanId())).stream().map(f->new Entitlement(f.getFeatureCode(),Boolean.TRUE.equals(f.getEnabled()),f.getQuotaValue())).toList(); }
    @EventListener(ApplicationReadyEvent.class) public void restoreRuntimeState(){tenants().forEach(tenant->runtimeState.publish(tenant,entitlements(tenant.getId())));}
    private long operatorId(){ return TenantContextHolder.current().map(c->c.userId()).orElse(0L); }
    private void audit(String action,String type,long id,String detail){ var log=new TenantAuditLog(); log.setId(Ids.next()); log.setOperatorId(operatorId()); log.setAction(action); log.setResourceType(type); log.setResourceId(Long.toString(id)); log.setDetail(detail); log.setCreatedAt(LocalDateTime.now()); audits.insert(log); }
    public record CreateApplication(String merchantName,String contactName,String contactMobile,String planCode,String password) { }
    public record OwnerProvision(String username,String passwordHash,String displayName,String mobile) { }
    public record UsernameAvailability(boolean available) { }
    public record Entitlement(String featureCode,boolean enabled,Long quota) { }
    public record TenantView(Long id,String tenantCode,String tenantName,String status,String ownerName,String ownerMobile,String planCode,String planName) { }
    public record PlanView(Long id,String planCode,String planName,String status,Long storeQuota) { }
}
