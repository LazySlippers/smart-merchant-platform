package com.smartmerchant.saas.merchant;

import com.smartmerchant.saas.merchant.application.StoreAccessGuard;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import com.smartmerchant.saas.merchant.application.InventoryService;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
class StoreAccessIntegrationTests {
    @Autowired com.smartmerchant.saas.merchant.application.StoreAccountService accounts;
    @Autowired InventoryService inventory; @Autowired JdbcTemplate jdbc; @Autowired StoreAccessGuard guard;
    @BeforeEach void data(){
        jdbc.update("INSERT INTO store(id,tenant_id,store_code,store_name,status,created_by) VALUES (101,1,'A','A店','ACTIVE',1),(102,1,'B','B店','ACTIVE',1),(201,2,'C','其他租户店','ACTIVE',2)");
        jdbc.update("INSERT INTO employee_profile(id,tenant_id,user_id,employee_no,employee_name,primary_store_id,status,created_by) VALUES (301,1,51,'E1','员工',101,'ACTIVE',1),(302,2,51,'E2','同号异租户员工',201,'ACTIVE',2)");
        jdbc.update("INSERT INTO employee_store(tenant_id,employee_id,store_id,created_by) VALUES (1,301,101,1),(1,301,102,1),(2,302,201,2)");
    }
    @AfterEach void clear(){TenantContextHolder.clear();jdbc.update("DELETE FROM employee_store");jdbc.update("DELETE FROM employee_profile");jdbc.update("DELETE FROM store");}

    @Test void permissionsCannotBeEditedByStoreAccountsOrForOtherStores(){
        TenantContextHolder.set(new TenantContext(1,51,false,"STORE_SELF",Set.of("iam:user:manage")));
        assertThatThrownBy(()->accounts.accessAccounts(101)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->accounts.reveal(101)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->accounts.edit(101,"new-name","名称",null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->accounts.updatePermissions(101,51,java.util.List.of())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        TenantContextHolder.set(new TenantContext(1,1,false,"TENANT_ALL",Set.of("iam:user:manage")));
        assertThatThrownBy(()->accounts.updatePermissions(102,51,java.util.List.of())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->accounts.accessAccounts(201)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->accounts.reveal(201)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->accounts.edit(201,"new-name","名称",null)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void storeSetCannotLeakSameUserFromAnotherTenant(){TenantContextHolder.set(new TenantContext(1,51,false,"STORE_SET",Set.of()));assertThat(guard.accessibleStoreIds()).containsExactlyInAnyOrder(101L,102L).doesNotContain(201L);}
    @Test void storeSelfOnlyGetsPrimaryStore(){TenantContextHolder.set(new TenantContext(1,51,false,"STORE_SELF",Set.of()));assertThat(guard.accessibleStoreIds()).containsExactly(101L);assertThat(guard.canOperateAtStore(101)).isTrue();assertThat(guard.canOperateAtStore(102)).isFalse();assertThat(guard.canOperateAtStore(201)).isFalse();}
    @Test void storeSelfCannotReadInventoryLedgerOrCountsFromAnotherStore(){
        TenantContextHolder.set(new TenantContext(1,51,false,"STORE_SELF",Set.of("merchant:inventory:view")));
        assertThat(inventory.storeLedger(101)).isEmpty();assertThat(inventory.counts(101)).isEmpty();
        for(long other: new long[]{102,201}){
            assertThatThrownBy(()->inventory.inventory(other)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(()->inventory.storeLedger(other)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(()->inventory.ledger(other,999)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(()->inventory.counts(other)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(()->inventory.adjust(other,999,1,"outside-"+other,"拒绝跨店")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
    }
}
