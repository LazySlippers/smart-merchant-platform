package com.smartmerchant.saas.merchant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = "spring.cloud.nacos.discovery.enabled=false")
@AutoConfigureMockMvc
class ConsumerCatalogIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired com.smartmerchant.saas.merchant.application.StorefrontService storefront;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO store(id,tenant_id,store_code,store_name,address,business_hours,status,created_by) VALUES (30101,301,'OPEN','营业门店','中山路1号','09:00-21:00','ACTIVE',1),(30102,301,'CLOSED','停业门店',NULL,NULL,'DISABLED',1),(30201,302,'OTHER','其他租户门店',NULL,NULL,'ACTIVE',2)");
        jdbc.update("INSERT INTO category(id,tenant_id,category_code,category_name,sort_order,status,created_by) VALUES (30111,301,'DRINK','饮品',1,'ACTIVE',1),(30112,301,'HIDDEN','隐藏分类',2,'DISABLED',1),(30211,302,'OTHER','其他租户分类',1,'ACTIVE',2)");
        jdbc.update("INSERT INTO product_spu(id,tenant_id,category_id,spu_code,product_name,description,image_url,status,created_by) VALUES (30121,301,30111,'COFFEE','咖啡','现磨咖啡','/coffee.png','ACTIVE',1),(30122,301,30111,'DRAFT','草稿商品',NULL,NULL,'DRAFT',1),(30221,302,30211,'OTHER','其他租户商品',NULL,NULL,'ACTIVE',2)");
        jdbc.update("INSERT INTO product_sku(id,tenant_id,spu_id,sku_code,sku_name,spec_json,barcode,base_price_cents,allow_store_price,status,created_by) VALUES (30131,301,30121,'LATTE','拿铁','{\"size\":\"M\"}','30131',1800,1,'ACTIVE',1),(30132,301,30121,'AMERICANO','美式',NULL,'30132',1200,0,'ACTIVE',1),(30133,301,30122,'DRAFT-SKU','草稿SKU',NULL,'30133',900,0,'ACTIVE',1),(30231,302,30221,'OTHER-SKU','其他租户SKU',NULL,'30231',100,0,'ACTIVE',2)");
        jdbc.update("INSERT INTO store_product(tenant_id,store_id,sku_id,sellable,updated_by) VALUES (301,30101,30131,1,1),(301,30101,30132,1,1),(301,30101,30133,1,1),(302,30201,30231,1,2)");
        jdbc.update("INSERT INTO store_price(tenant_id,store_id,sku_id,price_cents,approved_by) VALUES (301,30101,30131,2000,1)");
        jdbc.update("INSERT INTO inventory(id,tenant_id,store_id,sku_id,actual_quantity,available_quantity,reserved_quantity) VALUES (30141,301,30101,30131,8,8,0)");
    }

    @AfterEach
    void clear() {
        jdbc.update("DELETE FROM inventory");
        jdbc.update("DELETE FROM store_price");
        jdbc.update("DELETE FROM store_product");
        jdbc.update("DELETE FROM product_sku");
        jdbc.update("DELETE FROM product_spu");
        jdbc.update("DELETE FROM category");
        jdbc.update("DELETE FROM store");
    }

    @Test
    void anonymousCustomerCanChooseOnlyActiveStoresInTenant() throws Exception {
        mvc.perform(get("/api/consumer/v1/catalog/tenants/301/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(30101))
                .andExpect(jsonPath("$[0].storeName").value("营业门店"));
    }

    @Test void platformDiscoverySortsDistanceFiltersAreaAndHidesUnlistedStores() throws Exception {
        jdbc.update("UPDATE store SET latitude=30.25,longitude=120.15,city='杭州市',district='西湖区' WHERE id=30101");
        jdbc.update("UPDATE store SET latitude=31.23,longitude=121.47,city='上海市' WHERE id=30201");
        mvc.perform(get("/api/consumer/v1/catalog/stores").param("latitude","30.25").param("longitude","120.15"))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(2))).andExpect(jsonPath("$[0].id").value(30101)).andExpect(jsonPath("$[0].distanceKm").value(0)).andExpect(jsonPath("$[1].tenantId").value(302));
        mvc.perform(get("/api/consumer/v1/catalog/stores").param("area","西湖"))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1)));
        jdbc.update("UPDATE store SET marketplace_listed=FALSE WHERE id=30101");
        mvc.perform(get("/api/consumer/v1/catalog/stores").param("area","杭州"))
            .andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
        mvc.perform(get("/api/consumer/v1/catalog/stores").param("latitude","91").param("longitude","120"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/consumer/v1/catalog/stores").param("latitude","30"))
            .andExpect(status().isBadRequest());
    }

    @Test void discoveryFailsClosedForDisabledAndMissingTenantRuntimeState(){
        var redis=org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked") var values=(org.springframework.data.redis.core.ValueOperations<String,String>)org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redis.opsForValue()).thenReturn(values);
        org.mockito.Mockito.when(values.get("saas:tenant:status:301")).thenReturn("ACTIVE:1");
        org.mockito.Mockito.when(values.get("saas:tenant:status:302")).thenReturn("DISABLED:2");
        var catalog=new com.smartmerchant.saas.merchant.application.ConsumerCatalogService(jdbc,redis,true);
        var service=new com.smartmerchant.saas.merchant.application.StorefrontService(jdbc,null,catalog);
        org.assertj.core.api.Assertions.assertThat(service.discover(null,null,"","",0)).extracting(com.smartmerchant.saas.merchant.application.StorefrontService.NearbyStore::tenantId).containsExactly(301L);
        org.mockito.Mockito.when(values.get("saas:tenant:status:301")).thenReturn(null);
        org.assertj.core.api.Assertions.assertThat(service.discover(null,null,"","",0)).isEmpty();
    }

    @Test void freightUsesStoreRulesAndRejectsUnavailableRegionsAndPickupOnlyItems() throws Exception {
        jdbc.update("UPDATE store SET shipping_enabled=TRUE,first_shipping_cents=800,extra_shipping_cents=200,excluded_provinces='西藏自治区',pickup_only_skus='30132' WHERE id=30101");
        String prefix="{\"tenantId\":301,\"storeId\":30101,\"method\":\"SHIPPING\",\"province\":\"浙江省\",\"items\":[{\"skuId\":30131,\"quantity\":3}]}";
        mvc.perform(post("/internal/v1/trade/shipping/quote").header("X-Internal-Key","dev-only-internal-context-signing-key-32-bytes").contentType(APPLICATION_JSON).content(prefix))
            .andExpect(status().isOk()).andExpect(jsonPath("$.shippingFeeCents").value(1200));
        for(String invalid:java.util.List.of(prefix.replace("浙江省","西藏自治区"),prefix.replace("浙江省","浙江"),prefix.replace("30131","30132"),prefix.replace("\"storeId\":30101","\"storeId\":30201")))
            mvc.perform(post("/internal/v1/trade/shipping/quote").header("X-Internal-Key","dev-only-internal-context-signing-key-32-bytes").contentType(APPLICATION_JSON).content(invalid)).andExpect(status().is4xxClientError());
        mvc.perform(post("/internal/v1/trade/shipping/quote").header("X-Internal-Key","wrong").contentType(APPLICATION_JSON).content(prefix)).andExpect(status().isForbidden());
    }

    @Test void storefrontSettingsCannotBeEditedAcrossTenantsAndRejectStaleVersions(){
        com.smartmerchant.saas.security.TenantContextHolder.set(new com.smartmerchant.saas.security.TenantContext(301,1,false,"TENANT_ALL",java.util.Set.of()));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(()->storefront.settings(30201)).hasMessageContaining("403");
            var s=storefront.settings(30101);
            org.assertj.core.api.Assertions.assertThat(storefront.update(30101,s).version()).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->storefront.update(30101,s)).hasMessageContaining("409");
        } finally {com.smartmerchant.saas.security.TenantContextHolder.clear();}
    }

    @Test
    void customerSeesRealStorePriceAndAvailableStock() throws Exception {
        mvc.perform(get("/api/consumer/v1/catalog/tenants/301/stores/30101/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.id").value(30101))
                .andExpect(jsonPath("$.products", hasSize(2)))
                .andExpect(jsonPath("$.products[0].skuName").value("拿铁"))
                .andExpect(jsonPath("$.products[0].effectivePriceCents").value(2000))
                .andExpect(jsonPath("$.products[0].availableQuantity").value(8))
                .andExpect(jsonPath("$.products[0].selectable").value(true))
                .andExpect(jsonPath("$.products[1].skuName").value("美式"))
                .andExpect(jsonPath("$.products[1].effectivePriceCents").value(1200))
                .andExpect(jsonPath("$.products[1].availableQuantity").value(0))
                .andExpect(jsonPath("$.products[1].selectable").value(false));
    }

    @Test
    void tenantAndStoreMustBelongTogether() throws Exception {
        mvc.perform(get("/api/consumer/v1/catalog/tenants/301/stores/30201/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tradeInternalApiQuotesTrustedPriceAndReservesRealInventory() throws Exception {
        mvc.perform(post("/internal/v1/trade/catalog/quote")
                        .header("X-Internal-Key","dev-only-internal-context-signing-key-32-bytes")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tenantId\":301,\"storeId\":30101,\"items\":[{\"skuId\":30131,\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("咖啡"))
                .andExpect(jsonPath("$[0].priceCents").value(2000));
        mvc.perform(post("/internal/v1/trade/inventory/30131/reserve")
                        .header("X-Internal-Key","dev-only-internal-context-signing-key-32-bytes")
                        .contentType(APPLICATION_JSON)
                        .content("{\"tenantId\":301,\"storeId\":30101,\"quantity\":2,\"orderId\":8801,\"idempotencyKey\":\"ORDER:RESERVE:8801:30131\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(6))
                .andExpect(jsonPath("$.reservedQuantity").value(2));
    }
}
