package com.smartmerchant.saas.merchant.application;

import com.smartmerchant.saas.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class StockTransferService {
 private final JdbcTemplate jdbc;
 private final StoreAccessGuard access;
 private final InventoryService inventory;
 private final DirectOrderGateway orders;
 private final TransactionTemplate transactions;
 public StockTransferService(JdbcTemplate jdbc,StoreAccessGuard access,InventoryService inventory,DirectOrderGateway orders,PlatformTransactionManager manager){this.jdbc=jdbc;this.access=access;this.inventory=inventory;this.orders=orders;this.transactions=new TransactionTemplate(manager);}

 // Only this read model exposes other stores' available stock; their inventory mutations remain private.
 public List<Map<String,Object>> sharedStock(long requestingStoreId,String query){
  access.requireAccess(requestingStoreId);activeStore(requestingStoreId);
  String term="%"+(query==null?"":query.trim())+"%";
  return jdbc.queryForList("""
   SELECT i.store_id,st.store_name,st.address,i.sku_id,s.sku_code,s.sku_name,p.product_name,i.available_quantity
   FROM inventory i JOIN store st ON st.tenant_id=i.tenant_id AND st.id=i.store_id
   JOIN product_sku s ON s.tenant_id=i.tenant_id AND s.id=i.sku_id
   JOIN product_spu p ON p.tenant_id=s.tenant_id AND p.id=s.spu_id
   WHERE i.tenant_id=? AND i.store_id<>? AND st.status='ACTIVE' AND s.status='ACTIVE' AND p.status='ACTIVE'
    AND (s.sku_code LIKE ? OR s.sku_name LIKE ? OR p.product_name LIKE ?)
   ORDER BY st.store_name,s.sku_code
   """,tenant(),requestingStoreId,term,term,term);
 }

 @Transactional public Map<String,Object> create(String no,long source,long target,String remark,List<Line> lines){
  access.requireAccess(source);access.requireAccess(target);validate(source,target,lines);
  return insert(Ids.next(),no,source,target,remark,lines,"DRAFT","STORE",null,null,null,null);
 }

 public Map<String,Object> request(String no,long source,long target,String remark,List<Line> lines,String mode,String recipient,String phone,String address,Long orderId){
  access.requireAccess(target);
  var prior=jdbc.queryForList("SELECT id FROM stock_transfer WHERE tenant_id=? AND transfer_no=?",tenant(),no);
  if(!prior.isEmpty()){
   var existing=get(num(prior.getFirst(),"ID"));
   if(num(existing,"SOURCE_STORE_ID")!=source||num(existing,"TARGET_STORE_ID")!=target)bad("调拨单号已用于其它门店，请重新申请");
   return existing;
  }
  validate(source,target,lines);
  if(!Set.of("STORE","OTHER","CUSTOMER").contains(mode))bad("请选择正确的配送方式");
  String destination="STORE".equals(mode)?val(activeStore(target),"ADDRESS"):address;
  if(destination==null||destination.isBlank()||destination.equals("null"))bad("请填写完整收货地址，或先完善门店地址");
  if(!"STORE".equals(mode)&&destination.trim().length()<5)bad("请填写省市区、街道及门牌等详细地址");
  if(!"STORE".equals(mode)&&(recipient==null||recipient.isBlank()||phone==null||phone.isBlank()))bad("其它地址和直寄客户必须填写收件人、电话及详细地址");
  if("CUSTOMER".equals(mode)!=(orderId!=null))bad("直寄客户必须关联本店已收款、待履约的完整销售订单");
  DirectOrderGateway.Binding binding=null;
  if(orderId!=null){
   if(!TenantContextHolder.require().permissions().contains("merchant:order:view"))throw new ResponseStatusException(FORBIDDEN,"需要销售订单查看权限");
   binding=orders.bind(orderId,Ids.next(),source,target,lines);
   var existing=jdbc.queryForList("SELECT id FROM stock_transfer WHERE tenant_id=? AND id=?",tenant(),binding.transferId());
   if(!existing.isEmpty())return get(binding.transferId());
  }
  final var linked=binding;
  try{return transactions.execute(status->insert(linked==null?Ids.next():linked.transferId(),no,source,target,remark,lines,"REQUESTED",mode,recipient,phone,destination,linked));}
  catch(RuntimeException failure){
   if(linked!=null){
    // Another retry may already have materialized the same order binding.
    if(!jdbc.queryForList("SELECT id FROM stock_transfer WHERE tenant_id=? AND id=?",tenant(),linked.transferId()).isEmpty())return get(linked.transferId());
    try{orders.cancel(linked.orderId(),linked.transferId());}catch(RuntimeException compensation){failure.addSuppressed(compensation);}
   }
   throw failure;
  }
 }

 private Map<String,Object> insert(long id,String no,long source,long target,String remark,List<Line> lines,String state,String mode,String recipient,String phone,String address,DirectOrderGateway.Binding order){
  var c=TenantContextHolder.require();
  jdbc.update("""
   INSERT INTO stock_transfer(id,tenant_id,transfer_no,source_store_id,target_store_id,status,remark,created_by,delivery_mode,recipient_name,recipient_phone,delivery_address,order_id,order_no,order_paid_amount_cents)
   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
   """,id,c.tenantId(),no,source,target,state,remark,c.userId(),mode,recipient,phone,address,order==null?null:order.orderId(),order==null?null:order.orderNo(),order==null?0:order.paidAmountCents());
  for(var line:lines)jdbc.update("INSERT INTO stock_transfer_item(tenant_id,transfer_id,sku_id,quantity) VALUES (?,?,?,?)",tenant(),id,line.skuId(),line.quantity());
  return get(id);
 }

 private void validate(long source,long target,List<Line> lines){
  activeStore(source);activeStore(target);
  if(source==target||lines==null||lines.isEmpty())bad("请选择不同门店及调拨商品");
  var ids=new HashSet<Long>();
  for(var line:lines){
   if(line.skuId()<=0||line.quantity()<=0||!ids.add(line.skuId()))bad("商品不能重复，数量必须为正整数");
   var stock=jdbc.queryForList("SELECT i.available_quantity FROM inventory i JOIN product_sku s ON s.tenant_id=i.tenant_id AND s.id=i.sku_id WHERE i.tenant_id=? AND i.store_id=? AND i.sku_id=? AND s.status='ACTIVE'",tenant(),source,line.skuId());
   if(stock.isEmpty()||num(stock.getFirst(),"AVAILABLE_QUANTITY")<line.quantity())throw new ResponseStatusException(CONFLICT,"供货店可用库存不足，请刷新库存");
  }
 }
 private Map<String,Object> activeStore(long id){var rows=jdbc.queryForList("SELECT id,store_name,address FROM store WHERE tenant_id=? AND id=? AND status='ACTIVE'",tenant(),id);if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"门店不存在、已停用或不属于当前租户");return rows.getFirst();}
 public Map<String,Object> get(long id){var h=header(id,false);requireParticipant(h);h.put("items",jdbc.queryForList("SELECT ti.*,s.sku_code,s.sku_name,p.product_name FROM stock_transfer_item ti JOIN product_sku s ON s.tenant_id=ti.tenant_id AND s.id=ti.sku_id JOIN product_spu p ON p.tenant_id=s.tenant_id AND p.id=s.spu_id WHERE ti.tenant_id=? AND ti.transfer_id=? ORDER BY ti.sku_id",tenant(),id));return h;}
 public List<Map<String,Object>> list(){var ids=access.accessibleStoreIds();if(ids.isEmpty())return List.of();var marks=String.join(",",Collections.nCopies(ids.size(),"?"));var args=new ArrayList<Object>();args.add(tenant());args.addAll(ids);args.addAll(ids);return jdbc.queryForList(selectHeader()+" WHERE t.tenant_id=? AND (t.source_store_id IN ("+marks+") OR t.target_store_id IN ("+marks+")) ORDER BY t.created_at DESC,t.id DESC",args.toArray());}
 @Transactional public Map<String,Object> approve(long id){var h=header(id,true);access.requireAccess(num(h,"SOURCE_STORE_ID"));transition(id,"DRAFT","APPROVED","approved_by","approved_at");return get(id);}

 // Direct fulfilment is coordinated by trade-service. Do not hold a merchant transaction across that callback.
 public Map<String,Object> ship(long id){
  var h=get(id);access.requireAccess(num(h,"SOURCE_STORE_ID"));
  if("CUSTOMER".equals(val(h,"DELIVERY_MODE"))){orders.complete(num(h,"ORDER_ID"),id);markOrderSynced(id);return get(id);}
  return transactions.execute(status->{var locked=header(id,true);access.requireAccess(num(locked,"SOURCE_STORE_ID"));shipStock(id,locked,"IN_TRANSIT");return get(id);});
 }
 private void shipStock(long id,Map<String,Object> h,String next){
  String state=val(h,"STATUS");if(!Set.of("REQUESTED","APPROVED").contains(state))throw new ResponseStatusException(CONFLICT,"仅待供货确认或已审批的调拨可出库");
  activeStore(num(h,"SOURCE_STORE_ID"));
  for(var line:items(get(id)))inventory.transferOut(num(h,"SOURCE_STORE_ID"),num(line,"SKU_ID"),num(line,"QUANTITY"),id);
  transition(id,state,next,"shipped_by","shipped_at");
 }
 @Transactional public Map<String,Object> fulfillDirect(long id,long orderId,long target,long source,List<Line> orderLines){
  var h=header(id,true);access.requireAccess(source);
  if(!"CUSTOMER".equals(val(h,"DELIVERY_MODE"))||num(h,"ORDER_ID")!=orderId||num(h,"SOURCE_STORE_ID")!=source||num(h,"TARGET_STORE_ID")!=target)bad("订单与调拨单不匹配");
  var lines=items(get(id)).stream().map(r->new Line(num(r,"SKU_ID"),num(r,"QUANTITY"))).toList();
  if(!new HashSet<>(lines).equals(new HashSet<>(orderLines))||lines.size()!=orderLines.size())bad("调拨必须匹配订单全部商品和数量");
  if("COMPLETED".equals(val(h,"STATUS")))return get(id);
  shipStock(id,h,"COMPLETED");
  // A paid order has already deducted the selling store. Undo that deduction, never book a transfer-in.
  var c=TenantContextHolder.require();
  TenantContextHolder.callAs(new TenantContext(c.tenantId(),c.userId(),false,"TENANT_ALL",c.permissions()),()->{
   for(var line:lines)inventory.undoOrderDeduction(target,line.skuId(),line.quantity(),orderId,id);
   return null;
  });
  jdbc.update("UPDATE stock_transfer SET order_sync_pending=TRUE,shipped_data_scope=? WHERE tenant_id=? AND id=?",c.dataScope(),tenant(),id);
  return get(id);
 }
 public void markOrderSynced(long id){jdbc.update("UPDATE stock_transfer SET order_sync_pending=FALSE,order_sync_error=NULL WHERE tenant_id=? AND id=?",tenant(),id);}
 @Transactional public Map<String,Object> receive(long id){var h=header(id,true);long target=num(h,"TARGET_STORE_ID");access.requireAccess(target);if("CUSTOMER".equals(val(h,"DELIVERY_MODE"))||!"IN_TRANSIT".equals(val(h,"STATUS")))throw new ResponseStatusException(CONFLICT,"仅运输中的门店调拨可入库");for(var row:items(get(id)))inventory.transferIn(target,num(row,"SKU_ID"),num(row,"QUANTITY"),id);transition(id,"IN_TRANSIT","COMPLETED","received_by","received_at");return get(id);}
 public Map<String,Object> cancel(long id){
  var h=get(id);String state=val(h,"STATUS");
  if(!Set.of("REQUESTED","DRAFT","APPROVED").contains(state))throw new ResponseStatusException(CONFLICT,"出库后不能取消调拨");
  if(!"REQUESTED".equals(state))access.requireAccess(num(h,"SOURCE_STORE_ID"));
  // Cancelling the bound order first makes any concurrent direct shipment fail before it can deduct stock.
  if("CUSTOMER".equals(val(h,"DELIVERY_MODE")))orders.cancel(num(h,"ORDER_ID"),id);
  return transactions.execute(status->{var locked=header(id,true);requireParticipant(locked);int n=jdbc.update("UPDATE stock_transfer SET status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status=?",tenant(),id,state);if(n!=1)conflict();return get(id);});
 }
 private void requireParticipant(Map<String,Object> h){if(!access.canAccess(num(h,"SOURCE_STORE_ID"))&&!access.canAccess(num(h,"TARGET_STORE_ID")))throw new ResponseStatusException(FORBIDDEN,"调拨不属于本店");}
 private void transition(long id,String from,String to,String userColumn,String timeColumn){var c=TenantContextHolder.require();int n=jdbc.update("UPDATE stock_transfer SET status=?,"+userColumn+"=?,"+timeColumn+"=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status=?",to,c.userId(),c.tenantId(),id,from);if(n!=1)conflict();}
 private String selectHeader(){return "SELECT t.*,s.store_name source_store_name,d.store_name target_store_name FROM stock_transfer t JOIN store s ON s.tenant_id=t.tenant_id AND s.id=t.source_store_id JOIN store d ON d.tenant_id=t.tenant_id AND d.id=t.target_store_id";}
 private Map<String,Object> header(long id,boolean lock){var rows=jdbc.queryForList(selectHeader()+" WHERE t.tenant_id=? AND t.id=?"+(lock?" FOR UPDATE":""),tenant(),id);if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"调拨单不存在");return new LinkedHashMap<>(rows.getFirst());}
 @SuppressWarnings("unchecked")private static List<Map<String,Object>> items(Map<String,Object>h){return(List<Map<String,Object>>)h.get("items");}
 private static long num(Map<String,Object>r,String k){Object v=r.get(k);if(v==null)v=r.get(k.toLowerCase());return((Number)v).longValue();}
 private static String val(Map<String,Object>r,String k){Object v=r.get(k);if(v==null)v=r.get(k.toLowerCase());return v==null?null:String.valueOf(v);}
 private static long tenant(){return TenantContextHolder.requireTenantId();}
 private static void conflict(){throw new ResponseStatusException(CONFLICT,"调拨状态已变化，请刷新");}
 private static void bad(String message){throw new ResponseStatusException(BAD_REQUEST,message);}
 public record Line(long skuId,long quantity){}
}
