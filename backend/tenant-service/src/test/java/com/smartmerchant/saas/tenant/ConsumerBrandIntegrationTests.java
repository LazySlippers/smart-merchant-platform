package com.smartmerchant.saas.tenant;
import com.smartmerchant.saas.tenant.interfaces.ConsumerBrandController;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
@Transactional
class ConsumerBrandIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsumerBrandController brands;
    @BeforeEach void setup(){jdbc.update("INSERT INTO tenant(id,tenant_code,tenant_name,status,owner_name,owner_mobile) VALUES (99801,'H5-BRAND','测试品牌','ACTIVE','负责人','13800000000')");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner","",List.of(new SimpleGrantedAuthority("merchant:store:manage"))));TenantContextHolder.set(new TenantContext(99801,71,false,"TENANT_ALL",Set.of("merchant:store:manage")));}
    @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void activeBrandDefaultsAndVersionedSettings(){
        assertThat(brands.brand(99801).displayName()).isEqualTo("测试品牌");
        var settings=new ConsumerBrandController.Settings("生活好物","","#245a3e","今天也有好物","4001234567",0);
        assertThat(brands.update(settings).version()).isEqualTo(1);
        assertThat(brands.brand(99801).displayName()).isEqualTo("生活好物");
        assertThatThrownBy(()->brands.update(settings)).hasMessageContaining("409");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_audit_log WHERE action='CONSUMER_BRAND_UPDATED' AND resource_id='99801'",Long.class)).isEqualTo(1);
        jdbc.update("UPDATE tenant SET status='SUSPENDED' WHERE id=99801");
        assertThatThrownBy(()->brands.brand(99801)).hasMessageContaining("404");
    }
    @Test void storeAccountCannotModifyBrandSettings(){TenantContextHolder.set(new TenantContext(99801,71,false,"STORE_SELF",Set.of("merchant:store:manage")));assertThatThrownBy(()->brands.settings()).hasMessageContaining("403");}
}
