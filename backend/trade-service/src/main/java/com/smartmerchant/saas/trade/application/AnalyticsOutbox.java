package com.smartmerchant.saas.trade.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmerchant.saas.security.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.sql.Timestamp; import java.time.*; import java.util.*;

/** Transactional source-side event log. A failed analytics service never rolls back a paid order. */
@Component public class AnalyticsOutbox {
 private final JdbcTemplate jdbc; private final ObjectMapper json; private final RestClient analytics; private final String key;
 public AnalyticsOutbox(JdbcTemplate jdbc,ObjectMapper json,@Value("${saas.trade.analytics-service-url:http://localhost:8083}")String url,@Value("${saas.security.internal-signing-key}")String key){this.jdbc=jdbc;this.json=json;this.analytics=RestClient.builder().baseUrl(url).build();this.key=key;}
 public void orderPaid(long tenant,Map<String,Object> order,List<Map<String,Object>> items,LocalDateTime at){var lines=new ArrayList<Map<String,Object>>();int n=1;for(var i:items)lines.add(Map.of("lineNo",n++,"skuId",num(i,"SKU_ID"),"productName",str(i,"PRODUCT_NAME_SNAPSHOT"),"categoryName","未分类","quantity",num(i,"QUANTITY"),"amountCents",num(i,"LINE_AMOUNT_CENTS")));event(tenant,"ORDER_PAID",String.valueOf(num(order,"ID")),at,Map.of("orderId",num(order,"ID"),"storeId",num(order,"STORE_ID"),"memberId",num(order,"MEMBER_ID"),"paidAmountCents",num(order,"PAYABLE_AMOUNT_CENTS"),"couponDiscountCents",num(order,"COUPON_DISCOUNT_CENTS"),"items",lines));}
 public void refunded(long tenant,Map<String,Object> order,long refund,LocalDateTime at){event(tenant,"ORDER_REFUNDED",String.valueOf(num(order,"ID")),at,Map.of("orderId",num(order,"ID"),"refundAmountCents",refund));}
 private void event(long tenant,String type,String aggregate,LocalDateTime at,Map<String,Object> payload){try{jdbc.update("INSERT INTO analytics_outbox(event_id,tenant_id,event_type,aggregate_id,occurred_at,payload_json) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),tenant,type,aggregate,at,json.writeValueAsString(payload));}catch(Exception e){throw new IllegalStateException("Cannot persist analytics outbox",e);}}
 @Scheduled(fixedDelayString="${saas.trade.analytics-outbox-delay-ms:5000}") public void publish(){for(var r:jdbc.queryForList("SELECT * FROM analytics_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 100")){String id=str(r,"EVENT_ID");try{var payload=json.readValue(str(r,"PAYLOAD_JSON"),new TypeReference<Map<String,Object>>(){});analytics.post().uri("/internal/v1/analytics/events").header("X-Internal-Key",key).body(Map.of("eventId",id,"eventType",str(r,"EVENT_TYPE"),"eventVersion",1,"tenantId",num(r,"TENANT_ID"),"aggregateId",str(r,"AGGREGATE_ID"),"occurredAt",((Timestamp)r.get("OCCURRED_AT")).toInstant().toString(),"payload",payload)).retrieve().toBodilessEntity();jdbc.update("UPDATE analytics_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=?",id);}catch(Exception e){jdbc.update("UPDATE analytics_outbox SET attempts=attempts+1,last_error=? WHERE event_id=?",String.valueOf(e.getMessage()).substring(0,Math.min(500,String.valueOf(e.getMessage()).length())),id);}}}
 private static long num(Map<String,Object>m,String k){Object v=m.get(k);if(v==null)v=m.get(k.toLowerCase());return v==null?0:((Number)v).longValue();} private static String str(Map<String,Object>m,String k){Object v=m.get(k);if(v==null)v=m.get(k.toLowerCase());return String.valueOf(v);}
}
