package com.smartmerchant.saas.merchant;
import com.smartmerchant.saas.merchant.interfaces.*;
import com.smartmerchant.saas.merchant.application.StorefrontService;
import com.smartmerchant.saas.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")

class StoreConfigurationTests {
 @Autowired JdbcTemplate jdbc;@Autowired StoreConfigurationController controller;
 @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test","",List.of(new SimpleGrantedAuthority("merchant:store:manage"))));TenantContextHolder.set(new TenantContext(993,1,false,"TENANT_ALL",Set.of()));jdbc.update("INSERT INTO store(id,tenant_id,store_code,store_name,address,status,created_by) VALUES (9931,993,'A','一店','旧地址','ACTIVE',1),(9932,993,'B','二店','二店地址','ACTIVE',1)");}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();TenantContextHolder.clear();jdbc.update("DELETE FROM store WHERE tenant_id=993");}
 StoreConfigurationController.Configuration request(int version){return new StoreConfigurationController.Configuration(new MerchantOrganizationController.StoreUpdate("一店","独立新地址","09:00–20:00","ACTIVE",0),new StorefrontService.Settings(true,null,null,"杭州市","西湖区","",true,false,0,0,List.of(),List.of(),version));}
 @Test void updatesOneStoreOnly(){controller.update(9931,request(0));assertThat(jdbc.queryForObject("SELECT address FROM store WHERE id=9931",String.class)).isEqualTo("独立新地址");assertThat(jdbc.queryForObject("SELECT address FROM store WHERE id=9932",String.class)).isEqualTo("二店地址");}
 @Test void staleDeliveryVersionRollsBackAddress(){assertThatThrownBy(()->controller.update(9931,request(99))).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("SELECT address FROM store WHERE id=9931",String.class)).isEqualTo("旧地址");assertThat(jdbc.queryForObject("SELECT version FROM store WHERE id=9931",Integer.class)).isZero();}
}
