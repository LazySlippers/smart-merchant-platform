package com.smartmerchant.saas.merchant;

import com.smartmerchant.saas.merchant.analytics.application.AnalyticsService;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="spring.cloud.nacos.discovery.enabled=false")
class AnalyticsIntegrationTests {
 @Autowired AnalyticsService analytics; @Autowired JdbcTemplate jdbc;
 @AfterEach void clear(){TenantContextHolder.clear();jdbc.update("DELETE FROM analytics_metric_daily");jdbc.update("DELETE FROM analytics_metric_minute");jdbc.update("DELETE FROM analytics_member_event_fact");jdbc.update("DELETE FROM analytics_order_item_fact");jdbc.update("DELETE FROM analytics_order_fact");jdbc.update("DELETE FROM analytics_inbox");}
 @Test void inboxIsIdempotentAndFactsProduceNetRevenueAndRanks(){
   var at=Instant.parse("2026-08-25T10:15:30Z"); var paid=new AnalyticsService.AnalyticsEvent("paid-1","ORDER_PAID",1,7,"100",at,Map.of("orderId",100L,"storeId",11L,"memberId",80L,"paidAmountCents",1200L,"couponDiscountCents",100L,"items",List.of(Map.of("lineNo",1L,"skuId",9L,"productName","拿铁","categoryName","咖啡","quantity",2L,"amountCents",1200L))));
   assertThat(analytics.ingest(paid)).isTrue(); assertThat(analytics.ingest(paid)).isFalse();
   assertThat(analytics.ingest(new AnalyticsService.AnalyticsEvent("refund-1","ORDER_REFUNDED",1,7,"100",at.plusSeconds(60),Map.of("orderId",100L,"refundAmountCents",200L)))).isTrue();
   TenantContextHolder.set(new TenantContext(7,1,false,"TENANT_ALL",Set.of())); var result=analytics.dashboard(LocalDate.of(2026,8,25),LocalDate.of(2026,8,25),Set.of(11L));
   assertThat(result.summary().paidOrders()).isEqualTo(1);assertThat(result.summary().netRevenueCents()).isEqualTo(1000);assertThat(result.products()).singleElement().extracting(AnalyticsService.ProductRank::productName).isEqualTo("拿铁");assertThat(analytics.reconcile(LocalDate.of(2026,8,25))).isEqualTo(1);
 }
 @Test void tenantQueryNeverReadsAnotherTenantFact(){var at=Instant.parse("2026-08-25T10:15:30Z");analytics.ingest(new AnalyticsService.AnalyticsEvent("other","ORDER_PAID",1,8,"200",at,Map.of("orderId",200L,"storeId",11L,"paidAmountCents",999L,"items",List.of())));TenantContextHolder.set(new TenantContext(7,1,false,"TENANT_ALL",Set.of()));assertThat(analytics.dashboard(LocalDate.of(2026,8,25),LocalDate.of(2026,8,25),Set.of(11L)).summary().grossRevenueCents()).isZero();}
 @Test void membershipMetricsRemainVisibleBeforeFirstSale(){
   var at=Instant.parse("2026-08-25T10:15:30Z");
   analytics.ingest(new AnalyticsService.AnalyticsEvent("member-only","MEMBER_REGISTERED",1,7,"80",at,Map.of("memberId",80L)));
   TenantContextHolder.set(new TenantContext(7,1,false,"TENANT_ALL",Set.of()));
   var result=analytics.dashboard(LocalDate.of(2026,8,25),LocalDate.of(2026,8,25),Set.of());
   assertThat(result.summary().paidOrders()).isZero();assertThat(result.members().newMembers()).isEqualTo(1);
 }
 @Test void legacyOriginalPriceRefundCannotExceedCashReceived(){
   var at=Instant.parse("2026-08-25T10:15:30Z");
   analytics.ingest(new AnalyticsService.AnalyticsEvent("discount-paid","ORDER_PAID",1,7,"300",at,Map.of("orderId",300L,"storeId",11L,"paidAmountCents",800L,"items",List.of())));
   analytics.ingest(new AnalyticsService.AnalyticsEvent("discount-refund","ORDER_REFUNDED",1,7,"300",at,Map.of("orderId",300L,"refundAmountCents",1000L)));
   TenantContextHolder.set(new TenantContext(7,1,false,"TENANT_ALL",Set.of()));
   var result=analytics.dashboard(LocalDate.of(2026,8,25),LocalDate.of(2026,8,25),Set.of(11L));
   assertThat(result.summary().netRevenueCents()).isZero();assertThat(result.summary().refundCents()).isEqualTo(800);
   assertThat(result.trend().getFirst().netRevenueCents()).isZero();assertThat(result.stores().getFirst().netRevenueCents()).isZero();
 }
}
