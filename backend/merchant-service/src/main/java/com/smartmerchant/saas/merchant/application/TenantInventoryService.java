package com.smartmerchant.saas.merchant.application;

import com.smartmerchant.saas.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class TenantInventoryService {
 private final JdbcTemplate jdbc;private final InventoryService inventory;private final StoreAccessGuard access;
 public TenantInventoryService(JdbcTemplate jdbc,InventoryService inventory,StoreAccessGuard access){this.jdbc=jdbc;this.inventory=inventory;this.access=access;}

 // Include tenant-supplied goods that have not yet received inventory, so zero stock is not missed.
 private static final String STOCK="""
  SELECT a.store_id,st.store_name,st.status store_status,a.sku_id,s.sku_code,s.sku_name,p.product_name,
   s.status sku_status,p.status product_status,
   COALESCE(i.actual_quantity,0) actual_quantity,COALESCE(i.available_quantity,0) available_quantity,
   COALESCE(i.reserved_quantity,0) reserved_quantity,COALESCE(i.low_stock_threshold,5) low_stock_threshold
  FROM (SELECT tenant_id,store_id,sku_id FROM inventory UNION SELECT tenant_id,store_id,sku_id FROM store_product WHERE sellable=1) a
  JOIN store st ON st.tenant_id=a.tenant_id AND st.id=a.store_id
  JOIN product_sku s ON s.tenant_id=a.tenant_id AND s.id=a.sku_id
  JOIN product_spu p ON p.tenant_id=s.tenant_id AND p.id=s.spu_id
  LEFT JOIN inventory i ON i.tenant_id=a.tenant_id AND i.store_id=a.store_id AND i.sku_id=a.sku_id
  WHERE a.tenant_id=?
  """;

 public List<Map<String,Object>> stock(Long storeId){requireTenant();if(storeId!=null)access.requireAccess(storeId);return storeId==null?jdbc.queryForList(STOCK+" ORDER BY st.store_name,s.sku_code",tenant()):jdbc.queryForList(STOCK+" AND a.store_id=? ORDER BY s.sku_code",tenant(),storeId);}
 public List<Map<String,Object>> alerts(){
  var ids=access.accessibleStoreIds();if(ids.isEmpty())return List.of();
  var args=new ArrayList<Object>();args.add(tenant());args.addAll(ids);
  return jdbc.queryForList(STOCK+" AND a.store_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") AND st.status='ACTIVE' AND s.status='ACTIVE' AND p.status='ACTIVE' AND COALESCE(i.available_quantity,0)<=COALESCE(i.low_stock_threshold,5) ORDER BY COALESCE(i.available_quantity,0),st.store_name,s.sku_code",args.toArray());
 }

 @Transactional public Map<String,Object> allocate(String requestId,String reason,List<Line> requested){
  requireTenant();
  if(requestId==null||requestId.isBlank()||reason==null||reason.isBlank()||requested==null||requested.isEmpty())bad("请填写分配原因与各店入库数量");
  var lines=requested.stream().sorted(Comparator.comparingLong(Line::storeId).thenComparingLong(Line::skuId)).toList();
  var seen=new HashSet<String>();for(var l:lines)if(l.storeId()<=0||l.skuId()<=0||l.quantity()<=0||!seen.add(l.storeId()+":"+l.skuId()))bad("门店商品不能重复，数量必须为正整数");
  String hash=hash(reason,lines);
  var existing=jdbc.queryForList("SELECT id,payload_hash FROM inventory_allocation WHERE tenant_id=? AND request_id=?",tenant(),requestId);
  if(!existing.isEmpty()){
   if(!hash.equals(text(existing.getFirst(),"PAYLOAD_HASH")))throw new ResponseStatusException(CONFLICT,"此分配请求已提交不同内容，请重新创建");
   return Map.of("id",number(existing.getFirst(),"ID"),"replayed",true);
  }
  long id=Ids.next(),operator=TenantContextHolder.require().userId();
  jdbc.update("INSERT INTO inventory_allocation(id,tenant_id,request_id,payload_hash,reason,created_by) VALUES (?,?,?,?,?,?)",id,tenant(),requestId,hash,reason,operator);
  for(var l:lines){
   access.requireAccess(l.storeId());
   if(jdbc.queryForList("SELECT id FROM store WHERE tenant_id=? AND id=? AND status='ACTIVE' FOR UPDATE",tenant(),l.storeId()).isEmpty())bad("只能分配给当前租户的营业门店");
   if(jdbc.queryForObject("SELECT COUNT(*) FROM product_sku s JOIN product_spu p ON p.tenant_id=s.tenant_id AND p.id=s.spu_id WHERE s.tenant_id=? AND s.id=? AND s.status='ACTIVE' AND p.status='ACTIVE'",Long.class,tenant(),l.skuId())!=1)bad("只能分配租户提供的已启用商品");
   inventory.allocate(l.storeId(),l.skuId(),l.quantity(),id,reason);
   jdbc.update("INSERT INTO inventory_allocation_item(tenant_id,allocation_id,store_id,sku_id,quantity) VALUES (?,?,?,?,?)",tenant(),id,l.storeId(),l.skuId(),l.quantity());
   // First supply publishes this SKU to the store; existing sale/price settings are preserved.
   jdbc.update("INSERT INTO store_product(tenant_id,store_id,sku_id,sellable,updated_by) SELECT ?,?,?,TRUE,? WHERE NOT EXISTS (SELECT 1 FROM store_product WHERE tenant_id=? AND store_id=? AND sku_id=?)",tenant(),l.storeId(),l.skuId(),operator,tenant(),l.storeId(),l.skuId());
  }
  return Map.of("id",id,"replayed",false);
 }
 public List<Map<String,Object>> allocations(){requireTenant();return jdbc.queryForList("""
  SELECT h.id,h.reason,h.created_at,h.created_by,l.store_id,st.store_name,l.sku_id,s.sku_code,s.sku_name,p.product_name,l.quantity
  FROM inventory_allocation h JOIN inventory_allocation_item l ON l.tenant_id=h.tenant_id AND l.allocation_id=h.id
  JOIN store st ON st.tenant_id=l.tenant_id AND st.id=l.store_id
  JOIN product_sku s ON s.tenant_id=l.tenant_id AND s.id=l.sku_id
  JOIN product_spu p ON p.tenant_id=s.tenant_id AND p.id=s.spu_id
  WHERE h.tenant_id=? ORDER BY h.created_at DESC,h.id DESC,l.store_id
  """,tenant());}
 private static void requireTenant(){var c=TenantContextHolder.require();if(c.platform()||!"TENANT_ALL".equals(c.dataScope()))throw new ResponseStatusException(FORBIDDEN,"仅租户总部可查看全部库存与分配入库");}
 private static long tenant(){return TenantContextHolder.requireTenantId();}
 private static long number(Map<String,Object> r,String key){return ((Number)(r.containsKey(key)?r.get(key):r.get(key.toLowerCase()))).longValue();}
 private static String text(Map<String,Object> r,String key){return String.valueOf(r.containsKey(key)?r.get(key):r.get(key.toLowerCase()));}
 private static String hash(String reason,List<Line> lines){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((reason+"\n"+lines).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static void bad(String message){throw new ResponseStatusException(BAD_REQUEST,message);}
 public record Line(long storeId,long skuId,long quantity){}
}
