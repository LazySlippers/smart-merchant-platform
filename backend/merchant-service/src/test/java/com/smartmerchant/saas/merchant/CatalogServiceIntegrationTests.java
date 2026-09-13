package com.smartmerchant.saas.merchant;

import com.smartmerchant.saas.merchant.application.CatalogService;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
class CatalogServiceIntegrationTests {
    @Autowired JdbcTemplate jdbc;@Autowired CatalogService service;
    @BeforeEach void stores(){jdbc.update("INSERT INTO store(id,tenant_id,store_code,store_name,status,created_by) VALUES (1101,1,'S1','一店','ACTIVE',1),(1102,1,'S2','二店','ACTIVE',1),(2101,2,'S3','他租户店','ACTIVE',2)");jdbc.update("INSERT INTO employee_profile(id,tenant_id,user_id,employee_no,employee_name,primary_store_id,status,created_by) VALUES (3101,1,51,'E1','店员',1101,'ACTIVE',1)");jdbc.update("INSERT INTO employee_store(tenant_id,employee_id,store_id,created_by) VALUES (1,3101,1101,1)");}
    @AfterEach void clear(){TenantContextHolder.clear();jdbc.update("DELETE FROM store_price");jdbc.update("DELETE FROM store_product");jdbc.update("DELETE FROM product_sku");jdbc.update("DELETE FROM product_spu");jdbc.update("DELETE FROM category");jdbc.update("DELETE FROM employee_store");jdbc.update("DELETE FROM employee_profile");jdbc.update("DELETE FROM store");}

    @Test void storeSelfCanConfigureOnlyItsRealStoreAndEffectivePrice(){
        TenantContextHolder.set(context(1,1,"TENANT_ALL"));var category=service.createCategory(null,"DRINK","饮品",1);var spu=service.createSpu(category.getId(),"COFFEE","咖啡",null,null);spu=service.updateSpu(spu.getId(),category.getId(),spu.getProductName(),null,null,"ACTIVE",0);var sku=service.createSku(spu.getId(),"LATTE","拿铁","{\"size\":\"M\"}","6900001",1800,true);sku=service.updateSku(sku.getId(),sku.getSkuName(),sku.getSpecJson(),sku.getBarcode(),sku.getBasePriceCents(),true,"ACTIVE",0);
        long skuId=sku.getId();TenantContextHolder.set(new TenantContext(1,51,false,"STORE_SELF",Set.of("merchant:store-product:manage","merchant:price:manage")));var view=service.configureStore(1101,skuId,true,2000L);assertThat(view.sellable()).isTrue();assertThat(view.effectivePriceCents()).isEqualTo(2000L);assertThatThrownBy(()->service.configureStore(1102,skuId,true,null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
    @Test void catalogQueriesCannotLeakAnotherTenant(){jdbc.update("INSERT INTO category(id,tenant_id,category_code,category_name,sort_order,status,created_by) VALUES (9001,2,'SECRET','其他租户分类',0,'ACTIVE',2)");TenantContextHolder.set(context(1,1,"TENANT_ALL"));assertThat(service.categories()).extracting("categoryCode").doesNotContain("SECRET");}
    @Test void storePriceRequiresSkuOptIn(){TenantContextHolder.set(context(1,1,"TENANT_ALL"));var c=service.createCategory(null,"FOOD","食品",1);var p=service.createSpu(c.getId(),"BREAD","面包",null,null);p=service.updateSpu(p.getId(),c.getId(),p.getProductName(),null,null,"ACTIVE",0);var s=service.createSku(p.getId(),"BREAD-1","面包",null,null,500,false);s=service.updateSku(s.getId(),s.getSkuName(),null,null,500,false,"ACTIVE",0);long skuId=s.getId();TenantContextHolder.set(new TenantContext(1,1,false,"TENANT_ALL",Set.of("merchant:store-product:manage","merchant:price:manage")));assertThatThrownBy(()->service.configureStore(1101,skuId,true,600L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("does not allow");}
    private static TenantContext context(long tenant,long user,String scope){return new TenantContext(tenant,user,false,scope,Set.of());}
}
