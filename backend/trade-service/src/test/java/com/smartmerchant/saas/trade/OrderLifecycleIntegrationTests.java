package com.smartmerchant.saas.trade;

import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import com.smartmerchant.saas.trade.application.MerchantGateway;
import com.smartmerchant.saas.trade.application.MemberGateway;
import com.smartmerchant.saas.trade.application.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","saas.trade.expiry-scan-delay-ms=3600000","saas.trade.payment.sandbox.enabled=true"})
@Import(OrderLifecycleIntegrationTests.Fakes.class)
class OrderLifecycleIntegrationTests {
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.smartmerchant.saas.trade.application.ConsumerRefundService refunds;
    @Autowired com.smartmerchant.saas.trade.payment.PaymentService payments;
    @Autowired com.smartmerchant.saas.trade.payment.SandboxPaymentProvider sandboxProvider;
    @Autowired FakeMerchantGateway merchant;

    @BeforeEach void setup(){merchant.reset();}
    @AfterEach void clear(){org.springframework.security.core.context.SecurityContextHolder.clearContext();TenantContextHolder.clear();jdbc.update("DELETE FROM consumer_refund_request");jdbc.update("DELETE FROM analytics_outbox");jdbc.update("DELETE FROM finance_ledger");jdbc.update("DELETE FROM refund");jdbc.update("DELETE FROM pickup_verification");jdbc.update("DELETE FROM payment_callback_event");jdbc.update("DELETE FROM payment_order");jdbc.update("DELETE FROM payment");jdbc.update("DELETE FROM order_item");jdbc.update("DELETE FROM orders");}

    @Test void sandboxPaymentIsCreatedConfirmedAndReplayedWithoutDoubleDeduction(){
        var order=orders.createForConsumer(command("provider-order"),801);
        var payment=payments.create(41,801,order.id(),"provider-pay","SANDBOX");
        assertThat(payments.create(41,801,order.id(),"provider-pay","SANDBOX").id()).isEqualTo(payment.id());
        assertThat(payments.completeSandbox(41,801,payment.id()).result()).isEqualTo("SUCCEEDED");
        assertThat(payments.completeSandbox(41,801,payment.id()).result()).isEqualTo("SUCCEEDED");
        assertThat(orders.getForConsumer(41,order.id(),order.customerMobile()).status()).isEqualTo("PICKUP_READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_order",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment",Long.class)).isEqualTo(1);
        assertThat(merchant.actual(601)).isEqualTo(8);
    }

    @Test void paymentOwnershipAndTenantAreNeverAcceptedFromTheRequest(){
        var order=orders.createForConsumer(command("isolated-payment"),801);
        assertThatThrownBy(()->payments.create(41,802,order.id(),"foreign-member","SANDBOX")).hasMessageContaining("404");
        assertThatThrownBy(()->payments.create(42,801,order.id(),"foreign-tenant","SANDBOX")).hasMessageContaining("404");
        var payment=payments.create(41,801,order.id(),"owner-payment","SANDBOX");
        assertThatThrownBy(()->payments.completeSandbox(42,801,payment.id())).hasMessageContaining("404");
    }

    @Test void identicalSignedCallbackEventIsPersistedExactlyOnce(){
        var order=orders.createForConsumer(command("callback-replay"),801);var payment=payments.create(41,801,order.id(),"callback-payment","SANDBOX");
        var tradeNo=jdbc.queryForObject("SELECT provider_trade_no FROM payment_order WHERE id=?",String.class,payment.id());
        var timestamp=String.valueOf(java.time.Instant.now().getEpochSecond());
        var body="{\"eventId\":\"same-event\",\"tenantId\":41,\"providerTradeNo\":\""+tradeNo+"\",\"amountCents\":4600,\"status\":\"SUCCEEDED\"}";
        var signature=sandboxProvider.sign(timestamp,body);
        assertThat(payments.callback("SANDBOX",body,timestamp,signature).result()).isEqualTo("SUCCEEDED");
        assertThat(payments.callback("SANDBOX",body,timestamp,signature).result()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_callback_event WHERE event_id='same-event'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment",Long.class)).isEqualTo(1);
    }

    @Test void expiredPaymentClosesOrderAndRejectsLateSuccess(){
        var order=orders.createForConsumer(command("late-payment"),801);
        var payment=payments.create(41,801,order.id(),"late-payment-request","SANDBOX");
        jdbc.update("UPDATE orders SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE id=?",order.id());
        jdbc.update("UPDATE payment_order SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE id=?",payment.id());
        assertThat(payments.completeSandbox(41,801,payment.id()).result()).isEqualTo("IGNORED");
        assertThat(orders.getForConsumer(41,order.id(),order.customerMobile()).status()).isEqualTo("CLOSED");
        assertThat(merchant.available(601)).isEqualTo(10);
    }

    @Test
    void fullPickupChainSnapshotsPriceDeductsOnceAndBooksStorePerformance(){
        var created=orders.create(command("create-1"));
        assertThat(created.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(created.totalAmountCents()).isEqualTo(4600);
        assertThat(created.items()).extracting(OrderService.OrderItemView::productName).containsExactly("鲜奶拿铁","黄油面包");
        assertThat(created.items()).extracting(OrderService.OrderItemView::unitPriceCents).containsExactly(1800L,1000L);
        assertThat(merchant.available(601)).isEqualTo(8);
        assertThat(merchant.reserved(601)).isEqualTo(2);

        var paid=orders.simulatePayment(41,created.id(),"pay-1");
        var replay=orders.simulatePayment(41,created.id(),"pay-1");
        assertThat(paid.status()).isEqualTo("PICKUP_READY");
        assertThat(paid.pickupCode()).isNotBlank().isEqualTo(replay.pickupCode());
        assertThat(merchant.actual(601)).isEqualTo(8);
        assertThat(merchant.reserved(601)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_outbox WHERE event_type='ORDER_PAID'",Long.class)).isEqualTo(1);

        TenantContextHolder.set(new TenantContext(41,701,false,"STORE_SELF",Set.of("merchant:order:verify")));
        var completed=orders.verify(created.id(),paid.pickupCode());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(orders.performance(501)).extracting(OrderService.StorePerformance::completedOrders,OrderService.StorePerformance::revenueCents).containsExactly(1L,4600L);
        assertThat(orders.finance(501)).singleElement().extracting(OrderService.FinanceEntry::businessId,OrderService.FinanceEntry::amountCents).containsExactly(created.id(),4600L);
    }

    @Test void previewUsesValidMemberCorrelationAndNeverReservesInventory(){
        var base=command("preview");
        var preview=new OrderService.CreateOrder(base.tenantId(),base.storeId(),base.customerName(),base.customerMobile(),base.requestId(),base.items(),new OrderService.BenefitSelection(null,0,0));
        assertThat(orders.consumerQuote(preview).payableAmountCents()).isEqualTo(4600);
        assertThat(merchant.available(601)).isEqualTo(10);
        assertThat(merchant.reserved(601)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
    }

    @Test void shippingSnapshotsFreightThenRequiresOwningStoreAndConsumerToComplete(){
        var base=command("shipping-1");
        var command=new OrderService.CreateOrder(base.tenantId(),base.storeId(),base.customerName(),base.customerMobile(),base.requestId(),base.items(),null,new OrderService.Delivery("SHIPPING","李四","13900000000","浙江省","浙江省杭州市西湖区文一路18号"));
        var quote=orders.consumerQuote(command);
        assertThat(quote.shippingFeeCents()).isEqualTo(1200);
        assertThat(quote.payableAmountCents()).isEqualTo(5800);
        var created=orders.createForConsumer(command,801);
        assertThat(created.delivery().shippingFeeCents()).isEqualTo(1200);
        assertThat(created.delivery().name()).isEqualTo("李四");
        assertThat(created.customerMobile()).isEqualTo("13800000000");
        assertThat(orders.createForConsumer(command,801).id()).isEqualTo(created.id());
        assertThatThrownBy(()->orders.receive(41,created.id(),801)).hasMessageContaining("409");
        var paid=orders.simulatePayment(41,created.id(),"shipping-pay");
        assertThat(paid.status()).isEqualTo("PENDING_SHIPMENT");
        assertThat(paid.pickupCode()).isNull();
        assertThat(jdbc.queryForObject("SELECT amount_cents FROM payment WHERE order_id=?",Long.class,created.id())).isEqualTo(5800);
        TenantContextHolder.set(new TenantContext(41,702,false,"STORE_SELF",Set.of("merchant:order:verify")));
        assertThatThrownBy(()->orders.ship(created.id(),"顺丰","SF123456789")).hasMessageContaining("403");
        TenantContextHolder.set(new TenantContext(41,701,false,"STORE_SELF",Set.of("merchant:order:verify")));
        assertThatThrownBy(()->orders.verify(created.id(),"anything")).hasMessageContaining("409");
        assertThat(orders.ship(created.id(),"顺丰","SF123456789").status()).isEqualTo("SHIPPED");
        orders.ship(created.id(),"顺丰","SF123456789");
        assertThatThrownBy(()->orders.ship(created.id(),"顺丰","SF000000000")).hasMessageContaining("409");
        assertThatThrownBy(()->orders.receive(41,created.id(),802)).hasMessageContaining("404");
        assertThat(orders.receive(41,created.id(),801).status()).isEqualTo("COMPLETED");
        orders.receive(41,created.id(),801);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_ledger",Long.class)).isEqualTo(2);
        assertThat(orders.performance(501).revenueCents()).isEqualTo(4600);
        orders.refund(created.id(),"shipping-refund","整单退货");
        assertThat(jdbc.queryForObject("SELECT amount_cents FROM refund WHERE order_id=?",Long.class,created.id())).isEqualTo(5800);
        assertThat(jdbc.queryForObject("SELECT amount_cents FROM finance_ledger WHERE business_type='SHIPPING_REFUND'",Long.class)).isEqualTo(1200);
        assertThat(merchant.actual(601)).isEqualTo(10);
    }

    @Test void shippingCannotReserveInventoryWithIncompleteAddress(){
        var b=command("invalid-shipping");
        var invalid=new OrderService.CreateOrder(b.tenantId(),b.storeId(),b.customerName(),b.customerMobile(),b.requestId(),b.items(),null,new OrderService.Delivery("SHIPPING","收件人","13800000000","浙江省","缺少省份的地址"));
        assertThatThrownBy(()->orders.create(invalid)).hasMessageContaining("400");
        assertThat(merchant.reserved(601)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
    }

    @Test void consumerCancelReleasesStockOnceAndRejectsOtherCustomers(){
        var order=orders.create(command("consumer-cancel"));
        assertThatThrownBy(()->orders.cancelForConsumer(41,order.id(),"13999999999")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(orders.consumerOrders(41,order.customerMobile(),0)).extracting(OrderService.OrderView::id).contains(order.id());
        assertThat(orders.consumerOrders(41,"13999999999",0)).isEmpty();
        assertThat(orders.cancelForConsumer(41,order.id(),order.customerMobile()).status()).isEqualTo("CLOSED");
        orders.cancelForConsumer(41,order.id(),order.customerMobile());
        assertThat(merchant.available(601)).isEqualTo(10);
        assertThat(merchant.reserved(601)).isZero();
    }

    @Test void ownershipDoesNotAdoptLegacyOrdersAndIsPreservedOnRetries(){
        var legacy=orders.create(command("legacy-request"));
        assertThatThrownBy(()->orders.requireConsumerOwner(41,legacy.id(),801)).hasMessageContaining("404");
        assertThatThrownBy(()->orders.createForConsumer(command("legacy-request"),801)).hasMessageContaining("409");
        var own=orders.createForConsumer(command("owned-request"),801);
        assertThat(orders.createForConsumer(command("owned-request"),801).id()).isEqualTo(own.id());
        orders.requireConsumerOwner(41,own.id(),801);
        assertThatThrownBy(()->orders.requireConsumerOwner(41,own.id(),802)).hasMessageContaining("404");
        assertThat(orders.ownedConsumerOrders(41,801,0)).extracting(OrderService.OrderView::id).containsExactly(own.id());
        assertThat(orders.ownedConsumerOrders(41,802,0)).isEmpty();
    }

    @Test void sameUnifiedAccountStillNeedsTheCorrectTenantAndMemberForOrders(){
        var own=orders.createForConsumer(command("unified-owner"),801);
        // Even a member id known to the same global account grants no cross-tenant access.
        assertThat(orders.ownedConsumerOrders(42,801,0)).isEmpty();
        assertThatThrownBy(()->orders.requireConsumerOwner(42,own.id(),801)).hasMessageContaining("404");
        assertThatThrownBy(()->orders.requireConsumerOwner(41,own.id(),802)).hasMessageContaining("404");
        var controller=new com.smartmerchant.saas.trade.interfaces.ConsumerOrderController(orders);
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("scoped-b").header("alg","HS256")
                .claim("actor_type","CONSUMER").claim("user_id",901).claim("tenant_id",42)
                .claim("member_id",802).claim("consumer_mobile",own.customerMobile()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,List.of()));
        assertThatThrownBy(()->controller.get(own.id(),41,own.customerMobile())).hasMessageContaining("403");
        assertThatThrownBy(()->controller.cancel(own.id())).hasMessageContaining("404");
        var global=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("global").header("alg","HS256")
                .claim("actor_type","CONSUMER_ACCOUNT").claim("user_id",901).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(global,List.of()));
        assertThatThrownBy(()->controller.list(0)).hasMessageContaining("403");
    }

    @Test void consumerRefundIsReviewedByTheOwningStoreAndReplayedOnce(){
        var command=command("refund-request");
        var order=orders.createForConsumer(command,801);
        var paid=orders.simulatePayment(41,order.id(),"refund-payment");
        TenantContextHolder.set(new TenantContext(41,701,false,"STORE_SELF",Set.of("merchant:order:verify")));
        orders.verify(order.id(),paid.pickupCode());
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","HS256").claim("actor_type","CONSUMER").claim("tenant_id",41).claim("member_id",801).claim("consumer_mobile",command.customerMobile()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,List.of()));
        refunds.apply(order.id(),"商品有问题");refunds.apply(order.id(),"重复提交");
        assertThat(refunds.get(order.id())).singleElement().extracting(com.smartmerchant.saas.trade.application.ConsumerRefundService.RequestView::status).isEqualTo("PENDING");
        refunds.decide(order.id(),true,"门店同意退款");refunds.decide(order.id(),true,"重复审核");
        assertThat(orders.getForConsumer(41,order.id(),command.customerMobile()).status()).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refund WHERE order_id=?",Long.class,order.id())).isEqualTo(1);
        assertThat(merchant.available(601)).isEqualTo(10);
    }

    @Test
    void onlyEmployeeOfOrderStoreCanVerify(){
        var order=orders.create(command("create-2"));
        var paid=orders.simulatePayment(41,order.id(),"pay-2");
        TenantContextHolder.set(new TenantContext(41,702,false,"STORE_SELF",Set.of("merchant:order:verify")));
        assertThatThrownBy(()->orders.verify(order.id(),paid.pickupCode())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_ledger",Long.class)).isZero();
    }

    @Test
    void expiredUnpaidOrderClosesAndReleasesEveryReservation(){
        var order=orders.create(command("create-3"));
        jdbc.update("UPDATE orders SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE id=?",order.id());
        assertThat(orders.closeExpired()).isEqualTo(1);
        assertThat(orders.getForConsumer(41,order.id(),"13800000000").status()).isEqualTo("CLOSED");
        assertThat(merchant.available(601)).isEqualTo(10);
        assertThat(merchant.reserved(601)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment",Long.class)).isZero();
    }

    @Test
    void createRequestIsIdempotent(){var first=orders.create(command("same-request"));var replay=orders.create(command("same-request"));assertThat(replay.id()).isEqualTo(first.id());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isEqualTo(1);assertThat(merchant.reserved(601)).isEqualTo(2);}

    @Test void storeAccountCannotReadOtherStoresOrdersOrFinance(){
        var order=orders.create(command("scope-read"));
        TenantContextHolder.set(new TenantContext(41,702,false,"STORE_SELF",Set.of("merchant:order:view","merchant:finance:view")));
        assertThat(orders.merchantOrders()).isEmpty();
        assertThatThrownBy(()->orders.getForMerchant(order.id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->orders.finance(501)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->orders.performance(501)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test void directTransferKeepsOriginalPaymentAndRevenueAndRefundsSupplyingStore(){
        var order=orders.create(command("direct-order"));var paid=orders.simulatePayment(41,order.id(),"direct-payment");
        var lines=List.of(new MerchantGateway.OrderItemRequest(601,2),new MerchantGateway.OrderItemRequest(602,1));
        var binding=orders.bindDirect(41,order.id(),8801,502,501,lines);
        assertThat(binding.paidAmountCents()).isEqualTo(paid.payableAmountCents());
        assertThat(orders.bindDirect(41,order.id(),8802,502,501,lines).transferId()).isEqualTo(8801);
        TenantContextHolder.set(new TenantContext(41,701,false,"STORE_SELF",Set.of("merchant:order:verify")));
        assertThatThrownBy(()->orders.verify(order.id(),paid.pickupCode())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(()->orders.completeDirect(41,order.id(),8801,701,"STORE_SELF")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        orders.completeDirect(41,order.id(),8801,702,"STORE_SELF");orders.completeDirect(41,order.id(),8801,702,"STORE_SELF");
        assertThat(merchant.directCompletions).isEqualTo(1);
        assertThat(orders.getForMerchant(order.id()).status()).isEqualTo("COMPLETED");
        assertThat(orders.finance(501)).singleElement().extracting(OrderService.FinanceEntry::amountCents).isEqualTo(4600L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_ledger WHERE store_id=502",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment",Long.class)).isEqualTo(1);
        orders.refund(order.id(),"direct-refund","客户退货");assertThat(merchant.lastRestoredStore).isEqualTo(502);
        orders.completeDirect(41,order.id(),8801,702,"STORE_SELF");assertThat(merchant.directCompletions).isEqualTo(1);
    }
    @Test void cancelledDirectRequestRestoresNormalPickupAndInvalidBindingsFail(){
        var order=orders.create(command("cancel-direct"));var paid=orders.simulatePayment(41,order.id(),"cancel-payment");
        var lines=List.of(new MerchantGateway.OrderItemRequest(601,2),new MerchantGateway.OrderItemRequest(602,1));
        assertThatThrownBy(()->orders.bindDirect(41,order.id(),8803,502,599,lines)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->orders.bindDirect(41,order.id(),8803,502,501,List.of(lines.getFirst()))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(()->orders.bindDirect(99,order.id(),8803,502,501,lines)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        orders.bindDirect(41,order.id(),8803,502,501,lines);orders.cancelDirect(41,order.id(),8803);
        assertThatThrownBy(()->orders.completeDirect(41,order.id(),8803,702,"STORE_SELF")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        TenantContextHolder.set(new TenantContext(41,701,false,"STORE_SELF",Set.of("merchant:order:verify")));
        assertThat(orders.verify(order.id(),paid.pickupCode()).status()).isEqualTo("COMPLETED");
    }
    @Test void failedDirectFulfilmentDoesNotCompleteOrderOrBookRevenue(){
        var order=orders.create(command("retry-direct"));orders.simulatePayment(41,order.id(),"retry-payment");
        orders.bindDirect(41,order.id(),8804,502,501,List.of(new MerchantGateway.OrderItemRequest(601,2),new MerchantGateway.OrderItemRequest(602,1)));
        merchant.failDirect=true;
        assertThatThrownBy(()->orders.completeDirect(41,order.id(),8804,702,"STORE_SELF")).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.id())).isEqualTo("PICKUP_READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_ledger",Long.class)).isZero();
        merchant.failDirect=false;orders.completeDirect(41,order.id(),8804,702,"STORE_SELF");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_ledger",Long.class)).isEqualTo(1);
    }

    private static OrderService.CreateOrder command(String requestId){return new OrderService.CreateOrder(41,501,"张三","13800000000",requestId,List.of(new OrderService.CreateItem(601,2),new OrderService.CreateItem(602,1)),null);}

    @TestConfiguration static class Fakes{@Bean @Primary FakeMerchantGateway fakeMerchantGateway(){return new FakeMerchantGateway();}@Bean @Primary MemberGateway memberGateway(){return new NoBenefitMemberGateway();}}
    static class NoBenefitMemberGateway implements MemberGateway {public BenefitQuote quote(BenefitRequest r){if(r.orderId()<=0)throw new IllegalArgumentException("Member quote requires positive correlation id");return new BenefitQuote(0,0,0,0,r.orderAmountCents(),null);}public BenefitQuote freeze(BenefitRequest r){return quote(r);}public void confirm(BenefitRequest r){}public void release(BenefitRequest r){}public void refund(BenefitRequest r){}}

    static class FakeMerchantGateway implements MerchantGateway {
        @Override public long shippingFee(long tenant,long store,String method,String province,List<OrderItemRequest> items){return "SHIPPING".equals(method)?1200:0;}
        private final Map<Long,Stock> stocks=new ConcurrentHashMap<>();private final Set<String> operations=ConcurrentHashMap.newKeySet();
        int directCompletions;long lastRestoredStore;boolean failDirect;
        void reset(){directCompletions=0;lastRestoredStore=0;failDirect=false;stocks.clear();stocks.put(601L,new Stock(10,10,0));stocks.put(602L,new Stock(5,5,0));operations.clear();}
        @Override public List<QuoteItem> quote(long tenant,long store,List<OrderItemRequest> items){if(tenant!=41||store!=501)throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);return items.stream().map(i->{var s=stocks.get(i.skuId());String product=i.skuId()==601?"鲜奶拿铁":"黄油面包";long price=i.skuId()==601?1800:1000;return new QuoteItem(i.skuId(),"SKU"+i.skuId(),product,product,"{}",null,price,i.quantity(),s.available);}).toList();}
        @Override public synchronized void reserve(long t,long store,long order,long sku,long q,String key){if(!operations.add(key))return;var s=stocks.get(sku);if(s.available<q)throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);s.available-=q;s.reserved+=q;}
        @Override public synchronized void release(long t,long store,long order,long sku,long q,String key){if(!operations.add(key))return;var s=stocks.get(sku);if(s.reserved<q)throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);s.available+=q;s.reserved-=q;}
        @Override public synchronized void deduct(long t,long store,long order,long sku,long q,String key){if(!operations.add(key))return;var s=stocks.get(sku);if(s.reserved<q)throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);s.actual-=q;s.reserved-=q;}
        @Override public synchronized void restore(long t,long store,long order,long sku,long q,String key){lastRestoredStore=store;if(!operations.add(key))return;var s=stocks.get(sku);s.actual+=q;s.available+=q;}
        @Override public boolean canAccessStore(long tenant,long user,String scope,long store){return tenant==41&&((store==501&&("TENANT_ALL".equals(scope)||user==701))||(store==502&&("TENANT_ALL".equals(scope)||user==702)));}
        @Override public boolean canVerifyStore(long tenant,long user,String scope,long store){return tenant==41&&store==501&&user==701;}
        @Override public void fulfillDirect(long tenant,long transfer,long order,long target,long source,long user,String scope,List<OrderItemRequest> items){if(failDirect)throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"stock unavailable");directCompletions++;}
        long actual(long sku){return stocks.get(sku).actual;}long available(long sku){return stocks.get(sku).available;}long reserved(long sku){return stocks.get(sku).reserved;}
        static class Stock{long actual,available,reserved;Stock(long a,long v,long r){actual=a;available=v;reserved=r;}}
    }
}
