package com.smartmerchant.saas.merchant.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartmerchant.saas.merchant.domain.*;
import com.smartmerchant.saas.merchant.infrastructure.*;
import com.smartmerchant.saas.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class MerchantOrganizationService {
    private final StoreMapper stores; private final EmployeeProfileMapper employees; private final JdbcTemplate jdbc; private final StoreAccessGuard access; private final EntitlementGuard entitlements;
    public MerchantOrganizationService(StoreMapper stores, EmployeeProfileMapper employees, JdbcTemplate jdbc, StoreAccessGuard access,ObjectProvider<EntitlementGuard> entitlements) { this.stores=stores;this.employees=employees;this.jdbc=jdbc;this.access=access;this.entitlements=entitlements.getIfAvailable(); }

    public List<Store> stores() { var ids=access.accessibleStoreIds(); return ids.isEmpty()?List.of():stores.selectList(Wrappers.<Store>lambdaQuery().in(Store::getId,ids).orderByAsc(Store::getStoreCode)); }
    public Store store(long id) { access.requireAccess(id); return requiredStore(id); }
    @Transactional public Store createStore(String code,String name,String address,String hours) {
        requireTenantAll(); var c=TenantContextHolder.require(); var quota=storeQuota();var current=stores.selectCount(Wrappers.<Store>lambdaQuery());if(quota!=null&&current>=quota){String message=quota==2?"标准版最多可创建 2 个门店，请升级到高级版套餐后继续添加":"当前套餐最多可创建 "+quota+" 个门店";throw new ResponseStatusException(CONFLICT,message);} var now=LocalDateTime.now(); var s=new Store(); s.setId(Ids.next());s.setTenantId(c.tenantId());s.setStoreCode(code);s.setStoreName(name);s.setAddress(address);s.setBusinessHours(hours);s.setStatus("ACTIVE");s.setVersion(0);s.setCreatedBy(c.userId());s.setCreatedAt(now);s.setUpdatedAt(now);stores.insert(s);jdbc.update("UPDATE store SET marketplace_listed=TRUE,pickup_enabled=TRUE WHERE tenant_id=? AND id=?",c.tenantId(),s.getId());return s;
    }
    public StoreQuota storeQuotaView(){requireTenantAll();Long quota=storeQuota();long used=stores.selectCount(Wrappers.<Store>lambdaQuery());return new StoreQuota(used,quota,quota==null||used<quota);}
    @Transactional public Store updateStore(long id,String name,String address,String hours,String status,Integer version) {
        access.requireAccess(id); var current=requiredStore(id); int changed=stores.update(null,Wrappers.<Store>lambdaUpdate().eq(Store::getId,id).eq(Store::getVersion,version).set(Store::getStoreName,name).set(Store::getAddress,address).set(Store::getBusinessHours,hours).set(Store::getStatus,status).set(Store::getVersion,version+1).set(Store::getUpdatedAt,LocalDateTime.now())); if(changed!=1)throw new ResponseStatusException(CONFLICT,"Store was changed by another request"); return stores.selectById(id);
    }
    public List<EmployeeView> employees() {
        if("TENANT_ALL".equals(TenantContextHolder.require().dataScope())) return employees.selectList(Wrappers.<EmployeeProfile>lambdaQuery().orderByAsc(EmployeeProfile::getEmployeeNo)).stream().map(e->employeeView(e.getId())).toList();
        var visible=access.accessibleStoreIds(); if(visible.isEmpty())return List.of();
        var ids=jdbc.queryForList("SELECT DISTINCT employee_id FROM employee_store WHERE tenant_id=? AND store_id IN ("+placeholders(visible.size())+")",Long.class,args(TenantContextHolder.requireTenantId(),visible));
        return ids.stream().map(this::employeeView).toList();
    }
    @Transactional public EmployeeView createEmployee(long userId,String no,String name,String mobile,Long primaryStoreId,String jobTitle,List<Long> storeIds) {
        requireTenantAll(); validateStores(primaryStoreId,storeIds); var c=TenantContextHolder.require();var now=LocalDateTime.now();var e=new EmployeeProfile();e.setId(Ids.next());e.setTenantId(c.tenantId());e.setUserId(userId);e.setEmployeeNo(no);e.setEmployeeName(name);e.setMobile(mobile);e.setPrimaryStoreId(primaryStoreId);e.setJobTitle(jobTitle);e.setStatus("ACTIVE");e.setVersion(0);e.setCreatedBy(c.userId());e.setCreatedAt(now);e.setUpdatedAt(now);employees.insert(e);replaceAssignments(e.getId(),storeIds);return employeeView(e.getId());
    }
    @Transactional public EmployeeView assignStores(long employeeId,Long primaryStoreId,List<Long> storeIds) {
        requireTenantAll(); requiredEmployee(employeeId);validateStores(primaryStoreId,storeIds);employees.update(null,Wrappers.<EmployeeProfile>lambdaUpdate().eq(EmployeeProfile::getId,employeeId).set(EmployeeProfile::getPrimaryStoreId,primaryStoreId).set(EmployeeProfile::getUpdatedAt,LocalDateTime.now()));replaceAssignments(employeeId,storeIds);return employeeView(employeeId);
    }
    @Transactional public EmployeeView updateEmployee(long id,String name,String mobile,String jobTitle,String status,Integer version) {
        requireTenantAll(); requiredEmployee(id); int changed=employees.update(null,Wrappers.<EmployeeProfile>lambdaUpdate().eq(EmployeeProfile::getId,id).eq(EmployeeProfile::getVersion,version).set(EmployeeProfile::getEmployeeName,name).set(EmployeeProfile::getMobile,mobile).set(EmployeeProfile::getJobTitle,jobTitle).set(EmployeeProfile::getStatus,status).set(EmployeeProfile::getVersion,version+1).set(EmployeeProfile::getUpdatedAt,LocalDateTime.now()));if(changed!=1)throw new ResponseStatusException(CONFLICT,"Employee profile was changed by another request");return employeeView(id);
    }
    private void replaceAssignments(long employeeId,List<Long> ids){var c=TenantContextHolder.require();jdbc.update("DELETE FROM employee_store WHERE tenant_id=? AND employee_id=?",c.tenantId(),employeeId);for(long id:new LinkedHashSet<>(ids))jdbc.update("INSERT INTO employee_store(tenant_id,employee_id,store_id,created_by) VALUES (?,?,?,?)",c.tenantId(),employeeId,id,c.userId());}
    private void validateStores(Long primary,List<Long> ids){var all=new LinkedHashSet<>(ids);if(primary!=null)all.add(primary);for(long id:all)requiredStore(id);if(primary!=null&&!ids.contains(primary))throw new ResponseStatusException(BAD_REQUEST,"Primary store must be included in assigned stores");}
    private Store requiredStore(long id){var s=stores.selectById(id);if(s==null)throw new ResponseStatusException(NOT_FOUND,"Store not found");return s;}
    private EmployeeProfile requiredEmployee(long id){var e=employees.selectById(id);if(e==null)throw new ResponseStatusException(NOT_FOUND,"Employee profile not found");return e;}
    private EmployeeView employeeView(long id){var e=requiredEmployee(id);var ids=jdbc.queryForList("SELECT store_id FROM employee_store WHERE tenant_id=? AND employee_id=? ORDER BY store_id",Long.class,TenantContextHolder.requireTenantId(),id);return new EmployeeView(e,ids);}
    private void requireTenantAll(){if(!"TENANT_ALL".equals(TenantContextHolder.require().dataScope()))throw new ResponseStatusException(FORBIDDEN,"TENANT_ALL data scope is required");}
    private Long storeQuota(){return entitlements==null?null:entitlements.quota("merchant.store");}
    private static String placeholders(int n){return String.join(",",Collections.nCopies(n,"?"));}
    private static Object[] args(long tenant,List<Long> ids){var a=new ArrayList<Object>();a.add(tenant);a.addAll(ids);return a.toArray();}
    public record EmployeeView(EmployeeProfile profile,List<Long> storeIds){}
    public record StoreQuota(long used,Long limit,boolean canCreate){}
}
