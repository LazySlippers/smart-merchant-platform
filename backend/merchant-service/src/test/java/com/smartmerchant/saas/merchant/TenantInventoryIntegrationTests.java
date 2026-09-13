package com.smartmerchant.saas.merchant;

import com.smartmerchant.saas.merchant.application.*;
import com.smartmerchant.saas.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","saas.merchant.direct-sync-enabled=false"})
class TenantInventoryIntegrationTests {
 @Autowired com.smartmerchant.saas.merchant.analytics.application.AnalyticsService analytics;@Autowired JdbcTemplate jdbc;@Autowired TenantInventoryService tenantInventory;@Autowired InventoryService inventory;@Autowired CatalogService catalog;
 @BeforeEach void seed(){
  jdbc.update("INSERT INTO store(id,tenant_id,store_code,store_name,status,created_by) VALUES (6101,61,'A','一店','ACTIVE',1),(6102,61,'B','二店','ACTIVE',1),(6201,62,'C','其它租户店','ACTIVE',1)");
  jdbc.update("INSERT INTO employee_profile(id,tenant_id,user_id,employee_no,employee_name,primary_store_id,status,created_by) VALUES (6301,61,631,'E1','一店员工',6101,'ACTIVE',1),(6302,61,632,'E2','二店员工',6102,'ACTIVE',1)");
  jdbc.update("INSERT INTO category(id,tenant_id,category_code,category_name,sort_order,status,created_by) VALUES (6401,61,'C','分类',0,'ACTIVE',1),(6402,62,'C','其它分类',0,'ACTIVE',1)");
  jdbc.update("INSERT INTO product_spu(id,tenant_id,category_id,spu_code,product_name,status,created_by) VALUES (6501,61,6401,'P','租户商品','ACTIVE',1),(6502,62,6402,'P','其它商品','ACTIVE',1)");
  jdbc.update("INSERT INTO product_sku(id,tenant_id,spu_id,sku_code,sku_name,base_price_cents,allow_store_price,status,created_by) VALUES (6601,61,6501,'SKU','标准款',100,0,'ACTIVE',1),(6602,62,6502,'SKU','其它规格',100,0,'ACTIVE',1)");
  TenantContextHolder.set(hq());
 }
 @AfterEach void clear(){TenantContextHolder.clear();for(String table:List.of("inventory_allocation_item","inventory_allocation","inventory_ledger","stock_count_item","stock_count","store_price","store_product","inventory","product_sku","product_spu","category","employee_profile","store"))jdbc.update("DELETE FROM "+table);}
 @Test void tenantAllocatesDifferentQuantitiesAndReplayCannotDoubleReceive(){
  var lines=List.of(new TenantInventoryService.Line(6101,6601,3),new TenantInventoryService.Line(6102,6601,12));
  tenantInventory.allocate("batch-1","首批铺货",lines);tenantInventory.allocate("batch-1","首批铺货",lines.reversed());
  assertThat(actual(6101)).isEqualTo(3);assertThat(actual(6102)).isEqualTo(12);
  assertThat(tenantInventory.stock(null)).hasSize(2);assertThat(tenantInventory.allocations()).hasSize(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='TENANT_ALLOCATION'",Long.class)).isEqualTo(2);
  assertThatThrownBy(()->tenantInventory.allocate("batch-1","首批铺货",List.of(new TenantInventoryService.Line(6101,6601,99)))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
  TenantContextHolder.set(store(631));assertThat(catalog.products(6101L)).singleElement().satisfies(p->assertThat(p.skus()).singleElement().satisfies(s->assertThat(s.sellable()).isTrue()));
 }
 @Test void storeCannotSupplyGoodsAllocateOrAddManualStock(){
  TenantContextHolder.set(store(631));
  assertThatThrownBy(()->tenantInventory.stock(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  assertThatThrownBy(()->tenantInventory.allocate("blocked","门店自填",List.of(new TenantInventoryService.Line(6101,6601,10)))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  assertThatThrownBy(()->inventory.adjust(6101,6601,10,"blocked-adjust","增加")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  assertThatThrownBy(()->inventory.threshold(6101,6601,99)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  assertThatThrownBy(()->catalog.createWithSku(6401,"NO","私自商品","NO","规格",null,100)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  assertThat(catalog.products(6101L)).isEmpty();
 }
 @Test void badBatchRollsBackAllStoresAndCannotCrossTenant(){
  assertThatThrownBy(()->tenantInventory.allocate("bad-store","测试",List.of(new TenantInventoryService.Line(6101,6601,10),new TenantInventoryService.Line(6201,6601,10)))).isInstanceOf(ResponseStatusException.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory",Long.class)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_allocation",Long.class)).isZero();
  assertThatThrownBy(()->tenantInventory.allocate("bad-sku","测试",List.of(new TenantInventoryService.Line(6101,6602,10)))).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->tenantInventory.allocate("duplicate","测试",List.of(new TenantInventoryService.Line(6101,6601,1),new TenantInventoryService.Line(6101,6601,2)))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
 }
 @Test void lowStockUsesAvailableQuantityAndBothAudiencesShareSameThreshold(){
  tenantInventory.allocate("opening","首批",List.of(new TenantInventoryService.Line(6101,6601,10),new TenantInventoryService.Line(6102,6601,4)));
  inventory.reserve(6101,6601,7,"reserve-alert",1L);
  var hqAlerts=tenantInventory.alerts();assertThat(hqAlerts).hasSize(2);
  TenantContextHolder.set(store(631));assertThat(tenantInventory.alerts()).singleElement().satisfies(r->{assertThat(number(r,"STORE_ID")).isEqualTo(6101);assertThat(number(r,"AVAILABLE_QUANTITY")).isEqualTo(3);assertThat(number(r,"LOW_STOCK_THRESHOLD")).isEqualTo(5);});
  TenantContextHolder.set(store(632));assertThat(tenantInventory.alerts()).singleElement().satisfies(r->assertThat(number(r,"STORE_ID")).isEqualTo(6102));
  TenantContextHolder.set(hq());inventory.threshold(6101,6601,2);assertThat(tenantInventory.alerts()).singleElement();
  inventory.release(6101,6601,7,"release-alert",1L);
  tenantInventory.allocate("refill","补货",List.of(new TenantInventoryService.Line(6102,6601,10)));assertThat(tenantInventory.alerts()).isEmpty();
  TenantContextHolder.set(store(632));assertThat(tenantInventory.alerts()).isEmpty();
 }
 @Test void suppliedGoodsWithoutInventoryRowWarnButOtherTenantAndInactiveGoodsDoNot(){
  jdbc.update("INSERT INTO store_product(tenant_id,store_id,sku_id,sellable,updated_by) VALUES (61,6101,6601,TRUE,1),(62,6201,6602,TRUE,1)");
  assertThat(tenantInventory.alerts()).singleElement().satisfies(r->assertThat(number(r,"AVAILABLE_QUANTITY")).isZero());
  jdbc.update("UPDATE product_sku SET status='INACTIVE' WHERE id=6601");assertThat(tenantInventory.alerts()).isEmpty();
 }
 @Test void tenantProductAndInitialSkuAreCreatedAtomically(){
  var created=catalog.createWithSku(6401,"NEW","新商品","NEW-SKU","规格",null,1888);
  assertThat(created.spu().getStatus()).isEqualTo("ACTIVE");assertThat(created.skus()).singleElement().satisfies(s->assertThat(s.sku().getStatus()).isEqualTo("ACTIVE"));
  assertThatThrownBy(()->catalog.createWithSku(6401,"ROLLBACK","不能留草稿","NEW-SKU","重复规格",null,100)).isInstanceOf(RuntimeException.class);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_spu WHERE spu_code='ROLLBACK'",Long.class)).isZero();
 }
 @Test void historicSalesResolveCurrentProductCategory(){
  var at=java.time.Instant.parse("2026-09-10T01:00:00Z");
  analytics.ingest(new com.smartmerchant.saas.merchant.analytics.application.AnalyticsService.AnalyticsEvent("category-regression","ORDER_PAID",1,61,"99001",at,Map.of("orderId",99001L,"storeId",6101L,"paidAmountCents",100L,"items",List.of(Map.of("lineNo",1L,"skuId",6601L,"productName","租户商品","quantity",1L,"amountCents",100L)))));
  try {var result=analytics.dashboard(java.time.LocalDate.of(2026,9,10),java.time.LocalDate.of(2026,9,10),Set.of(6101L));assertThat(result.products()).singleElement().satisfies(p->assertThat(p.categoryName()).isEqualTo("分类"));}
  finally {jdbc.update("DELETE FROM analytics_order_item_fact WHERE tenant_id=61");jdbc.update("DELETE FROM analytics_order_fact WHERE tenant_id=61");jdbc.update("DELETE FROM analytics_inbox WHERE tenant_id=61");}
 }
 @Test void storeStockCountCannotBypassTenantApprovalForIncreasingStock(){
  tenantInventory.allocate("count-opening","首批",List.of(new TenantInventoryService.Line(6101,6601,3)));
  TenantContextHolder.set(store(631));var count=inventory.createCount(6101,"PC-TENANT",null,List.of(new InventoryService.CountLine(6601,8)));long id=number(count,"ID");
  assertThatThrownBy(()->inventory.completeCount(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");assertThat(actual(6101)).isEqualTo(3);
  TenantContextHolder.set(hq());inventory.completeCount(id);assertThat(actual(6101)).isEqualTo(8);
 }
 private long actual(long store){return jdbc.queryForObject("SELECT actual_quantity FROM inventory WHERE tenant_id=61 AND store_id=? AND sku_id=6601",Long.class,store);}
 private static long number(Map<String,Object> r,String k){return ((Number)(r.containsKey(k)?r.get(k):r.get(k.toLowerCase()))).longValue();}
 private static TenantContext hq(){return new TenantContext(61,1,false,"TENANT_ALL",Set.of());}
 private static TenantContext store(long user){return new TenantContext(61,user,false,"STORE_SELF",Set.of("merchant:inventory:manage","merchant:product:manage"));}
}
