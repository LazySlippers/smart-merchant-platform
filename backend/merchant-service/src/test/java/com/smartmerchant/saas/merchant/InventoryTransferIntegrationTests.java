package com.smartmerchant.saas.merchant;
import com.smartmerchant.saas.merchant.application.*;import com.smartmerchant.saas.security.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.server.ResponseStatusException;import java.util.*;import java.util.concurrent.*;import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","saas.merchant.direct-sync-delay-ms=3600000"}) class InventoryTransferIntegrationTests {
 @org.springframework.test.context.bean.override.mockito.MockitoBean DirectOrderGateway orderGateway;
 @Autowired DirectTransferReconciler reconciler;@Autowired JdbcTemplate jdbc;@Autowired InventoryService inventory;@Autowired StockTransferService transfers;
 @BeforeEach void data(){jdbc.update("INSERT INTO store(id,tenant_id,store_code,store_name,status,created_by) VALUES (1201,1,'A','调出店','ACTIVE',1),(1202,1,'B','调入店','ACTIVE',1)");jdbc.update("INSERT INTO category(id,tenant_id,category_code,category_name,sort_order,status,created_by) VALUES (2201,1,'C','分类',0,'ACTIVE',1)");jdbc.update("INSERT INTO product_spu(id,tenant_id,category_id,spu_code,product_name,status,created_by) VALUES (3201,1,2201,'P','商品','ACTIVE',1)");jdbc.update("INSERT INTO product_sku(id,tenant_id,spu_id,sku_code,sku_name,base_price_cents,allow_store_price,status,created_by) VALUES (4201,1,3201,'S','规格',100,0,'ACTIVE',1)");jdbc.update("INSERT INTO employee_profile(id,tenant_id,user_id,employee_no,employee_name,primary_store_id,status,created_by) VALUES (9101,1,101,'E1','供货员',1201,'ACTIVE',1),(9102,1,102,'E2','申请员',1202,'ACTIVE',1)");jdbc.update("UPDATE store SET address='上海市测试路 100 号' WHERE tenant_id=1");TenantContextHolder.set(context());}
 @AfterEach void clear(){TenantContextHolder.clear();jdbc.update("DELETE FROM inventory_ledger");jdbc.update("DELETE FROM stock_count_item");jdbc.update("DELETE FROM stock_count");jdbc.update("DELETE FROM stock_transfer_item");jdbc.update("DELETE FROM stock_transfer");jdbc.update("DELETE FROM inventory");jdbc.update("DELETE FROM product_sku");jdbc.update("DELETE FROM product_spu");jdbc.update("DELETE FROM category");jdbc.update("DELETE FROM employee_profile");jdbc.update("DELETE FROM store");}
 @Test void idempotentAdjustmentWritesOneImmutableLedger(){inventory.adjust(1201,4201,10,"ADJ-1","期初");var again=inventory.adjust(1201,4201,10,"ADJ-1","期初");assertThat(again.actualQuantity()).isEqualTo(10);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Long.class)).isEqualTo(1);}
 @Test void concurrentReservationsNeverMakeAvailableStockNegative() throws Exception {inventory.adjust(1201,4201,10,"ADJ-2","期初");try(var executor=Executors.newVirtualThreadPerTaskExecutor()){Callable<Boolean> task=()->{TenantContextHolder.set(context());try{inventory.reserve(1201,4201,7,UUID.randomUUID().toString(),99L);return true;}catch(ResponseStatusException e){return false;}finally{TenantContextHolder.clear();}};var results=executor.invokeAll(List.of(task,task));assertThat(results.stream().filter(f->{try{return f.get();}catch(Exception e){return false;}}).count()).isEqualTo(1);}var row=jdbc.queryForMap("SELECT * FROM inventory WHERE store_id=1201 AND sku_id=4201");assertThat(((Number)row.get("AVAILABLE_QUANTITY")).longValue()).isEqualTo(3);assertThat(((Number)row.get("RESERVED_QUANTITY")).longValue()).isEqualTo(7);}
 @Test void stockCountCompletesWithDifferenceLedger(){inventory.adjust(1201,4201,10,"ADJ-3","期初");var count=inventory.createCount(1201,"PC-1",null,List.of(new InventoryService.CountLine(4201,8)));long id=num(count,"ID");inventory.completeCount(id);assertThat(inventory.inventory(1201)).first().extracting(r->((Number)r.get("ACTUAL_QUANTITY")).longValue()).isEqualTo(8L);assertThatThrownBy(()->inventory.completeCount(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");}
 @Test void transferStateMachineMovesStockAndCannotReplayShipment(){inventory.adjust(1201,4201,10,"ADJ-4","期初");var transfer=transfers.create("DB-1",1201,1202,null,List.of(new StockTransferService.Line(4201,4)));long id=num(transfer,"ID");transfers.approve(id);transfers.ship(id);assertThatThrownBy(()->transfers.ship(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");var done=transfers.receive(id);assertThat(val(done,"STATUS")).isEqualTo("COMPLETED");assertThat(actual(1201)).isEqualTo(6);assertThat(actual(1202)).isEqualTo(4);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE business_type='TRANSFER'",Long.class)).isEqualTo(2);}
 @Test void staleCountCannotOverwriteSalesOrNewReceipts(){inventory.adjust(1201,4201,10,"START-COUNT","期初");var count=inventory.createCount(1201,"STALE-COUNT",null,List.of(new InventoryService.CountLine(4201,8)));inventory.adjust(1201,4201,3,"NEW-RECEIPT","新到货");assertThatThrownBy(()->inventory.completeCount(num(count,"ID"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");assertThat(actual(1201)).isEqualTo(13);assertThat(inventory.counts(1201)).hasSize(1);assertThat(inventory.storeLedger(1201)).hasSize(2);}
 @Test void storeCanRequestOtherStoresStockButOnlySourceCanShip(){
  inventory.adjust(1201,4201,10,"REQUEST-STOCK","期初");TenantContextHolder.set(storeContext(102));
  assertThat(transfers.sharedStock(1202,"S")).singleElement().extracting(r->num(r,"STORE_ID")).isEqualTo(1201L);
  assertThatThrownBy(()->inventory.inventory(1201)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  var requested=transfers.request("SELF-REQUEST",1201,1202,"补货",List.of(new StockTransferService.Line(4201,4)),"STORE",null,null,null,null);long id=num(requested,"ID");
  assertThat(val(requested,"STATUS")).isEqualTo("REQUESTED");
  assertThatThrownBy(()->transfers.ship(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  TenantContextHolder.set(storeContext(101));assertThat(transfers.list()).singleElement();transfers.ship(id);
  assertThatThrownBy(()->transfers.receive(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
  TenantContextHolder.set(storeContext(102));transfers.receive(id);assertThat(actual(1201)).isEqualTo(6);assertThat(actual(1202)).isEqualTo(4);
 }
 @Test void requestRejectsCrossTenantStoreAndMissingAddress(){
  inventory.adjust(1201,4201,10,"ADDRESS-STOCK","期初");TenantContextHolder.set(storeContext(102));
  assertThatThrownBy(()->transfers.request("BAD-STORE",999,1202,null,List.of(new StockTransferService.Line(4201,1)),"STORE",null,null,null,null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
  assertThatThrownBy(()->transfers.request("BAD-ADDRESS",1201,1202,null,List.of(new StockTransferService.Line(4201,1)),"OTHER","收件人","13800000000","",null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
  TenantContextHolder.set(new TenantContext(2,1,false,"TENANT_ALL",Set.of()));assertThat(transfers.list()).isEmpty();
  assertThatThrownBy(()->transfers.sharedStock(1202,"")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void insufficientStockAtConfirmationRollsBackEntireTransfer(){
  inventory.adjust(1201,4201,5,"RACE-START","期初");TenantContextHolder.set(storeContext(102));
  long id=num(transfers.request("RACE",1201,1202,null,List.of(new StockTransferService.Line(4201,4)),"STORE",null,null,null,null),"ID");
  TenantContextHolder.set(storeContext(101));inventory.adjust(1201,4201,-3,"RACE-SALE","销售");
  assertThatThrownBy(()->transfers.ship(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
  assertThat(val(transfers.get(id),"STATUS")).isEqualTo("REQUESTED");assertThat(actual(1201)).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='TRANSFER_OUT'",Long.class)).isZero();
 }
 @Test void directOrderShipsSourceOnlyAndUndoOriginalSaleIsIdempotent(){
  inventory.adjust(1201,4201,10,"DIRECT-SOURCE","期初");inventory.adjust(1202,4201,3,"DIRECT-TARGET","期初");
  inventory.reserve(1202,4201,3,"ORDER:RESERVE:501:4201",501L);inventory.deductReserved(1202,4201,3,"ORDER:DEDUCT:501:4201",501L);
  var lines=List.of(new StockTransferService.Line(4201,3));
  org.mockito.Mockito.when(orderGateway.bind(org.mockito.ArgumentMatchers.eq(501L),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(1201L),org.mockito.ArgumentMatchers.eq(1202L),org.mockito.ArgumentMatchers.eq(lines))).thenReturn(new DirectOrderGateway.Binding(99001,501,"O501",300));
  TenantContextHolder.set(storeContext(102));long id=num(transfers.request("DIRECT",1201,1202,null,lines,"CUSTOMER","客户","13800000000","上海市客户路 12 号",501L),"ID");
  TenantContextHolder.set(storeContext(101));transfers.fulfillDirect(id,501,1202,1201,lines);transfers.fulfillDirect(id,501,1202,1201,lines);
  assertThat(actual(1201)).isEqualTo(7);assertThat(actual(1202)).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='TRANSFER_IN'",Long.class)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE operation_type='ORDER_FULFILLMENT_UNDO'",Long.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT order_sync_pending FROM stock_transfer WHERE id=?",Boolean.class,id)).isTrue();
  org.mockito.Mockito.doThrow(new RuntimeException("temporary order outage")).doNothing().when(orderGateway).complete(501,id);
  reconciler.retry();assertThat(jdbc.queryForObject("SELECT order_sync_pending FROM stock_transfer WHERE id=?",Boolean.class,id)).isTrue();
  reconciler.retry();assertThat(jdbc.queryForObject("SELECT order_sync_pending FROM stock_transfer WHERE id=?",Boolean.class,id)).isFalse();
  assertThat(actual(1201)).isEqualTo(7);assertThat(actual(1202)).isEqualTo(3);
  TenantContextHolder.set(storeContext(102));assertThatThrownBy(()->transfers.receive(id)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
 }
 private static TenantContext storeContext(long user){return new TenantContext(1,user,false,"STORE_SELF",Set.of("merchant:transfer:manage","merchant:order:view"));}
 private long actual(long store){return jdbc.queryForObject("SELECT actual_quantity FROM inventory WHERE store_id=? AND sku_id=4201",Long.class,store);}private static TenantContext context(){return new TenantContext(1,1,false,"TENANT_ALL",Set.of("merchant:inventory:manage","merchant:transfer:manage","merchant:transfer:approve"));}private static long num(Map<String,Object>r,String k){Object v=r.get(k);if(v==null)v=r.get(k.toLowerCase());return((Number)v).longValue();}private static String val(Map<String,Object>r,String k){Object v=r.get(k);if(v==null)v=r.get(k.toLowerCase());return String.valueOf(v);}
}
