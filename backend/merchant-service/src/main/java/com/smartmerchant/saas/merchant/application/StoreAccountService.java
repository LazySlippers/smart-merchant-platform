package com.smartmerchant.saas.merchant.application;

import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class StoreAccountService {
    private final JdbcTemplate jdbc;
    private final MerchantOrganizationService organization;
    private final RestClient iam;
    private final String internalKey;

    public StoreAccountService(JdbcTemplate jdbc, MerchantOrganizationService organization,
                               @Value("${iam.service.url:http://localhost:8082}") String iamUrl,
                               @Value("${saas.security.internal-signing-key}") String internalKey) {
        this.jdbc=jdbc;this.organization=organization;this.iam=RestClient.builder().baseUrl(iamUrl).build();this.internalKey=internalKey;
    }

    public List<AccountView> accounts(){requireTenantAll();return jdbc.query("""
            SELECT a.store_id,s.store_code,s.store_name,a.user_id,a.username,a.display_name,a.created_at,a.updated_at
              FROM store_login_account a JOIN store s ON s.tenant_id=a.tenant_id AND s.id=a.store_id
             WHERE a.tenant_id=? ORDER BY s.store_code
            """,(rs,n)->new AccountView(rs.getLong("store_id"),rs.getString("store_code"),rs.getString("store_name"),rs.getLong("user_id"),rs.getString("username"),rs.getString("display_name"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime()),tenant());}

    @Transactional
    public AccountView create(long storeId,String username,String password,String displayName,List<String> permissions){requireTenantAll();organization.store(storeId);if(jdbc.queryForObject("SELECT COUNT(*) FROM store_login_account WHERE tenant_id=? AND store_id=?",Long.class,tenant(),storeId)>0)throw new ResponseStatusException(CONFLICT,"This store already has a login account");if(jdbc.queryForObject("SELECT COUNT(*) FROM store_login_account WHERE tenant_id=? AND username=?",Long.class,tenant(),username)>0)throw new ResponseStatusException(CONFLICT,"This username is already bound to another store");var user=iam.post().uri("/internal/v1/store-accounts").header("X-Internal-Key",internalKey).body(new ProvisionRequest(tenant(),username,password,displayName,permissions)).retrieve().body(UserResponse.class);if(user==null)throw new IllegalStateException("IAM did not return a store account");var employeeIds=jdbc.queryForList("SELECT id FROM employee_profile WHERE tenant_id=? AND user_id=?",Long.class,tenant(),user.id());if(employeeIds.isEmpty())organization.createEmployee(user.id(),"SA-"+storeId,displayName,null,storeId,"门店管理员",List.of(storeId));else organization.assignStores(employeeIds.getFirst(),storeId,List.of(storeId));jdbc.update("INSERT INTO store_login_account(tenant_id,store_id,user_id,username,display_name,created_by) VALUES (?,?,?,?,?,?)",tenant(),storeId,user.id(),user.username(),displayName,TenantContextHolder.require().userId());return account(storeId);}

    @Transactional
    public AccountView resetPassword(long storeId,String password){requireTenantAll();var account=account(storeId);iam.put().uri("/internal/v1/store-accounts/{userId}/password",account.userId()).header("X-Internal-Key",internalKey).body(new PasswordRequest(tenant(),password)).retrieve().toBodilessEntity();jdbc.update("UPDATE store_login_account SET updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND store_id=?",tenant(),storeId);return account(storeId);}


    public List<AccessAccount> accessAccounts(long storeId){
        requireTenantAll();organization.store(storeId);
        var ids=jdbc.queryForList("SELECT DISTINCT user_id FROM employee_profile WHERE tenant_id=? AND primary_store_id=? AND status='ACTIVE'",Long.class,tenant(),storeId);
        if(ids.isEmpty())return List.of();
        var result=iam.post().uri("/internal/v1/store-accounts/access-query").header("X-Internal-Key",internalKey).body(new AccessQuery(tenant(),ids)).retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<AccessAccount>>(){});
        return result==null?List.of():result;
    }
    public void updatePermissions(long storeId,long userId,List<String> permissions){
        requireTenantAll();organization.store(storeId);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM employee_profile WHERE tenant_id=? AND primary_store_id=? AND user_id=? AND status='ACTIVE'",Long.class,tenant(),storeId,userId)==0)throw new ResponseStatusException(FORBIDDEN,"账号不属于当前门店");
        iam.put().uri("/internal/v1/store-accounts/{userId}/permissions",userId).header("X-Internal-Key",internalKey).body(new PermissionsRequest(tenant(),permissions)).retrieve().toBodilessEntity();
    }
    public record AccessAccount(long userId,String username,String displayName,List<String> permissions){}
    @Transactional public AccountView edit(long storeId,String username,String displayName,String password){
        requireTenantAll();organization.store(storeId);var account=account(storeId);
        iam.put().uri("/internal/v1/store-accounts/{id}",account.userId()).header("X-Internal-Key",internalKey).body(new EditRequest(tenant(),username,displayName,password,TenantContextHolder.require().userId())).retrieve().toBodilessEntity();
        jdbc.update("UPDATE store_login_account SET username=?,display_name=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND store_id=?",username,displayName,tenant(),storeId);
        jdbc.update("UPDATE employee_profile SET employee_name=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=?",displayName,tenant(),account.userId());
        return account(storeId);
    }
    public Object reveal(long storeId){requireTenantAll();organization.store(storeId);var account=account(storeId);return iam.post().uri("/internal/v1/store-accounts/{id}/reveal",account.userId()).header("X-Internal-Key",internalKey).body(new RevealRequest(tenant(),TenantContextHolder.require().userId())).retrieve().body(java.util.Map.class);}
    private record EditRequest(long tenantId,String username,String displayName,String password,long operatorId){}
    private record RevealRequest(long tenantId,long operatorId){}
    private record AccessQuery(long tenantId,List<Long> userIds){}
    private record PermissionsRequest(long tenantId,List<String> permissions){}
    private AccountView account(long storeId){return accounts().stream().filter(a->a.storeId()==storeId).findFirst().orElseThrow();}
    private static long tenant(){return TenantContextHolder.requireTenantId();}
    private static void requireTenantAll(){if(!"TENANT_ALL".equals(TenantContextHolder.require().dataScope()))throw new ResponseStatusException(FORBIDDEN,"Only the tenant manager can manage store accounts");}
    private record ProvisionRequest(long tenantId,String username,String password,String displayName,List<String> permissions){}
    private record PasswordRequest(long tenantId,String password){}
    private record UserResponse(long id,String username,String displayName){}
    public record AccountView(long storeId,String storeCode,String storeName,long userId,String username,String displayName,LocalDateTime createdAt,LocalDateTime updatedAt){}
}
