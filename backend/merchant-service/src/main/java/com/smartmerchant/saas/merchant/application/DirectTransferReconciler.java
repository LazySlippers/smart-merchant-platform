package com.smartmerchant.saas.merchant.application;

import com.smartmerchant.saas.security.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Durable retry after inventory commits but an order response/transaction fails. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="saas.merchant.direct-sync-enabled",havingValue="true",matchIfMissing=true)
public class DirectTransferReconciler {
 private final JdbcTemplate jdbc;private final DirectOrderGateway orders;private final StockTransferService transfers;
 public DirectTransferReconciler(JdbcTemplate jdbc,DirectOrderGateway orders,StockTransferService transfers){this.jdbc=jdbc;this.orders=orders;this.transfers=transfers;}
 @Scheduled(initialDelayString="${saas.merchant.direct-sync-delay-ms:10000}",fixedDelayString="${saas.merchant.direct-sync-delay-ms:10000}")
 public void retry(){
  var rows=jdbc.query("SELECT tenant_id,id,order_id,shipped_by,shipped_data_scope FROM stock_transfer WHERE order_sync_pending=TRUE AND status='COMPLETED' ORDER BY id LIMIT 100",
   (r,n)->new Pending(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5)));
  for(var row:rows){
   try{TenantContextHolder.callAs(new TenantContext(row.tenant(),row.user(),false,row.scope(),Set.of()),()->{orders.complete(row.order(),row.id());transfers.markOrderSynced(row.id());return null;});}
   catch(RuntimeException e){String message=String.valueOf(e.getMessage());jdbc.update("UPDATE stock_transfer SET order_sync_error=? WHERE tenant_id=? AND id=?",message.substring(0,Math.min(512,message.length())),row.tenant(),row.id());}
  }
 }
 private record Pending(long tenant,long id,long order,long user,String scope){}
}
