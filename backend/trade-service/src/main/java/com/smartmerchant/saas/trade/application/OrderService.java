package com.smartmerchant.saas.trade.application;

import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final MerchantGateway merchant;
    private final MemberGateway members;
    private final AnalyticsOutbox analytics;
    private final long unpaidTimeoutMinutes;

    public OrderService(JdbcTemplate jdbc, MerchantGateway merchant, MemberGateway members, AnalyticsOutbox analytics,
                        @Value("${saas.trade.unpaid-timeout-minutes:15}") long unpaidTimeoutMinutes) {
        this.jdbc = jdbc;
        this.merchant = merchant;
        this.members = members;
        this.analytics = analytics;
        this.unpaidTimeoutMinutes = unpaidTimeoutMinutes;
    }

    @Transactional
    public OrderView create(CreateOrder command) {
        validate(command);
        var existing = jdbc.queryForList("SELECT id FROM orders WHERE tenant_id=? AND request_id=?",
                command.tenantId(), command.requestId());
        if (!existing.isEmpty()) return getForConsumer(command.tenantId(), number(existing.getFirst(), "ID"), command.customerMobile());

        var requested = command.items().stream().map(i -> new MerchantGateway.OrderItemRequest(i.skuId(), i.quantity())).toList();
        var quote = merchant.quote(command.tenantId(), command.storeId(), requested);
        if (quote == null || quote.size() != command.items().size()) bad("Merchant quote is incomplete");
        long totalQuantity = 0, totalAmount = 0;
        for (var item : quote) {
            if (item.quantity() <= 0 || item.availableQuantity() < item.quantity()) conflict("Insufficient stock: " + item.skuId());
            totalQuantity = Math.addExact(totalQuantity, item.quantity());
            totalAmount = Math.addExact(totalAmount, Math.multiplyExact(item.priceCents(), item.quantity()));
        }

        long id = Ids.next();
        long freight = freight(command);
        var benefits = command.benefits() == null ? new MemberGateway.BenefitQuote(0,0,0,0,totalAmount,null)
                : members.quote(benefitRequest(command, id, totalAmount));
        var now = LocalDateTime.now();
        try {
            jdbc.update("""
                    INSERT INTO orders(id,tenant_id,order_no,request_id,store_id,customer_name,customer_mobile,status,
                                       total_quantity,total_amount_cents,member_id,coupon_id,coupon_discount_cents,points_used,stored_value_used_cents,payable_amount_cents,expires_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,'PENDING_PAYMENT',?,?,?,?,?,?,?,?,?,?,?)
                    """, id, command.tenantId(), "O" + id, command.requestId(), command.storeId(),
                    command.customerName(), command.customerMobile(), totalQuantity, totalAmount,
                    benefits.memberId()==0?null:benefits.memberId(), benefits.couponId(), benefits.couponDiscountCents(), benefits.pointsUsed(), benefits.storedValueUsedCents(), Math.addExact(benefits.payableAmountCents(),freight), now.plusMinutes(unpaidTimeoutMinutes), now, now);
            var delivery=command.delivery();
            if(delivery!=null&&"PICKUP".equals(delivery.method()))delivery=null;
            jdbc.update("UPDATE orders SET delivery_method=?,shipping_fee_cents=?,recipient_name=?,recipient_phone=?,recipient_province=?,recipient_address=? WHERE tenant_id=? AND id=?",
                delivery==null?"PICKUP":delivery.method(),freight,delivery==null?null:delivery.name(),delivery==null?null:delivery.phone(),delivery==null?null:delivery.province(),delivery==null?null:delivery.address(),command.tenantId(),id);
            int line = 0;
            for (var item : quote) {
                jdbc.update("""
                        INSERT INTO order_item(tenant_id,order_id,line_no,sku_id,sku_code,product_name_snapshot,
                                               sku_name_snapshot,spec_snapshot,image_url_snapshot,unit_price_cents,
                                               quantity,line_amount_cents)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                        """, command.tenantId(), id, ++line, item.skuId(), item.skuCode(), item.productName(),
                        item.skuName(), item.specJson(), item.imageUrl(), item.priceCents(), item.quantity(),
                        Math.multiplyExact(item.priceCents(), item.quantity()));
            }
        } catch (DuplicateKeyException duplicate) {
            var race = jdbc.queryForList("SELECT id FROM orders WHERE tenant_id=? AND request_id=?",
                    command.tenantId(), command.requestId());
            if (!race.isEmpty()) return getForConsumer(command.tenantId(), number(race.getFirst(), "ID"), command.customerMobile());
            throw duplicate;
        }

        var reserved = new ArrayList<MerchantGateway.QuoteItem>();
        try {
            for (var item : quote) {
                merchant.reserve(command.tenantId(), command.storeId(), id, item.skuId(), item.quantity(),
                        "ORDER:RESERVE:" + id + ":" + item.skuId());
                reserved.add(item);
            }
        } catch (RuntimeException failure) {
            for (var item : reserved.reversed()) {
                try {
                    merchant.release(command.tenantId(), command.storeId(), id, item.skuId(), item.quantity(),
                            "ORDER:CREATE-ROLLBACK:" + id + ":" + item.skuId());
                } catch (RuntimeException ignored) { failure.addSuppressed(ignored); }
            }
            throw failure;
        }
        if(command.benefits()!=null) {
            try { members.freeze(benefitRequest(command,id,totalAmount)); }
            catch (RuntimeException failure) { for(var item:reserved.reversed()) try { merchant.release(command.tenantId(),command.storeId(),id,item.skuId(),item.quantity(),"ORDER:BENEFIT-ROLLBACK:"+id+":"+item.skuId()); } catch(RuntimeException ignored){failure.addSuppressed(ignored);} throw failure; }
        }
        return view(command.tenantId(), id);
    }

    @Transactional
    public OrderView simulatePayment(long tenantId, long orderId, String paymentRequestId) {
        return confirmPayment(tenantId,orderId,paymentRequestId,"SIMULATED",paymentRequestId);
    }

    @Transactional
    public OrderView confirmPayment(long tenantId, long orderId, String paymentRequestId,String channel,String providerTradeNo) {
        if (paymentRequestId == null || paymentRequestId.isBlank()) bad("Payment request id is required");
        var order = lockedRow(tenantId, orderId);
        var existingPayment = jdbc.queryForList("SELECT payment_request_id FROM payment WHERE tenant_id=? AND order_id=?", tenantId, orderId);
        if (!existingPayment.isEmpty()) {
            if (!paymentRequestId.equals(string(existingPayment.getFirst(), "PAYMENT_REQUEST_ID"))) conflict("Order already paid by another request");
            return view(tenantId, orderId);
        }
        if (!"PENDING_PAYMENT".equals(string(order, "STATUS"))) conflict("Only a pending order can be paid");
        if (!LocalDateTime.now().isBefore(time(order, "EXPIRES_AT"))) {
            close(order);
            conflict("Order has expired");
        }
        long storeId = number(order, "STORE_ID");
        for (var item : itemRows(tenantId, orderId)) {
            long skuId = number(item, "SKU_ID"), quantity = number(item, "QUANTITY");
            merchant.deduct(tenantId, storeId, orderId, skuId, quantity, "ORDER:DEDUCT:" + orderId + ":" + skuId);
        }
        if(number(order,"MEMBER_ID") > 0) members.confirm(benefitRequest(order));
        var now = LocalDateTime.now();
        var pickupCode = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        boolean shipping="SHIPPING".equals(string(order,"DELIVERY_METHOD"));
        int changed = jdbc.update("""
                UPDATE orders SET status=?,paid_at=?,pickup_code=?,version=version+1,updated_at=?
                 WHERE tenant_id=? AND id=? AND status='PENDING_PAYMENT'
                """, shipping?"PENDING_SHIPMENT":"PICKUP_READY",now,shipping?null:pickupCode,now,tenantId,orderId);
        if (changed != 1) conflict("Order status changed concurrently");
        jdbc.update("INSERT INTO payment(id,tenant_id,order_id,payment_request_id,channel,status,amount_cents,paid_at) VALUES (?,?,?,?,?,'SUCCESS',?,?)",
                Ids.next(), tenantId, orderId, paymentRequestId, channel, number(order, "PAYABLE_AMOUNT_CENTS"), now);
        analytics.orderPaid(tenantId,order,itemRows(tenantId,orderId),now);
        return view(tenantId, orderId);
    }

    @Transactional
    public OrderView verify(long orderId, String pickupCode) {
        var context = TenantContextHolder.require();
        var order = lockedRow(context.tenantId(), orderId);
        if(number(order,"DIRECT_TRANSFER_ID")>0)conflict("订单已申请直寄，请在调拨中完成或取消后自提核销");
        if (!"PICKUP_READY".equals(string(order, "STATUS"))) conflict("Only a pickup-ready order can be verified");
        if (!constantTimeEquals(string(order, "PICKUP_CODE"), pickupCode)) bad("Invalid pickup code");
        long storeId = number(order, "STORE_ID");
        if (!merchant.canVerifyStore(context.tenantId(), context.userId(), context.dataScope(), storeId)) {
            throw new ResponseStatusException(FORBIDDEN, "Only the order store can verify pickup");
        }
        var now = LocalDateTime.now();
        int changed = jdbc.update("UPDATE orders SET status='COMPLETED',verified_at=?,version=version+1,updated_at=? WHERE tenant_id=? AND id=? AND status='PICKUP_READY'",
                now, now, context.tenantId(), orderId);
        if (changed != 1) conflict("Order status changed concurrently");
        jdbc.update("INSERT INTO pickup_verification(id,tenant_id,order_id,store_id,pickup_code,verifier_user_id,verified_at) VALUES (?,?,?,?,?,?,?)",
                Ids.next(), context.tenantId(), orderId, storeId, pickupCode, context.userId(), now);
        jdbc.update("INSERT INTO finance_ledger(id,tenant_id,store_id,business_type,business_id,direction,amount_cents,occurred_at,remark) VALUES (?,?,?,'ORDER',?,'INCOME',?,?,?)",
                Ids.next(), context.tenantId(), storeId, orderId, number(order, "TOTAL_AMOUNT_CENTS"), now, "自提订单核销");
        return merchantView(context.tenantId(), orderId);
    }

    @Transactional
    public int closeExpired() {
        var expired = jdbc.queryForList("SELECT * FROM orders WHERE status='PENDING_PAYMENT' AND expires_at<=CURRENT_TIMESTAMP ORDER BY id");
        int count = 0;
        for (var order : expired) {
            close(order);
            count++;
        }
        return count;
    }

    public PaymentSnapshot paymentSnapshot(long tenantId,long orderId){var o=row(tenantId,orderId);return new PaymentSnapshot(number(o,"ID"),string(o,"ORDER_NO"),string(o,"STATUS"),number(o,"PAYABLE_AMOUNT_CENTS"),time(o,"EXPIRES_AT"));}
    @Transactional public void closeExpiredOrder(long tenantId,long orderId){var o=lockedRow(tenantId,orderId);if("PENDING_PAYMENT".equals(string(o,"STATUS")))close(o);}

    public OrderView getForConsumer(long tenantId, long orderId, String mobile) {
        var order = row(tenantId, orderId);
        if (mobile == null || !mobile.equals(string(order, "CUSTOMER_MOBILE"))) throw new ResponseStatusException(NOT_FOUND, "Order not found");
        return view(tenantId, orderId);
    }

    @Transactional
    public OrderView createForConsumer(CreateOrder command,long memberId) {
        var existing=jdbc.queryForList("SELECT consumer_member_id FROM orders WHERE tenant_id=? AND request_id=?",command.tenantId(),command.requestId());
        if(!existing.isEmpty() && number(existing.getFirst(),"CONSUMER_MEMBER_ID")!=memberId)
            throw new ResponseStatusException(CONFLICT,"订单请求已被使用，请重新提交");
        var order=create(command);
        int changed=jdbc.update("UPDATE orders SET consumer_member_id=? WHERE tenant_id=? AND id=? AND (consumer_member_id IS NULL OR consumer_member_id=?)",memberId,command.tenantId(),order.id(),memberId);
        if(changed!=1)throw new ResponseStatusException(CONFLICT,"订单请求已被使用");
        return order;
    }

    public void requireConsumerOwner(long tenantId,long orderId,long memberId) {
        var order=row(tenantId,orderId);
        if(number(order,"CONSUMER_MEMBER_ID")!=memberId)throw new ResponseStatusException(NOT_FOUND,"Order not found");
    }

    public List<OrderView> ownedConsumerOrders(long tenantId,long memberId,int page) {
        if(page<0 || page>10000)bad("Invalid page");
        return jdbc.queryForList("SELECT id FROM orders WHERE tenant_id=? AND consumer_member_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",tenantId,memberId,page*20)
                .stream().map(r->view(tenantId,number(r,"ID"))).toList();
    }

    public List<OrderView> consumerOrders(long tenantId,String mobile,int page) {
        if(page<0 || page>10000)bad("Invalid page");
        return jdbc.queryForList("SELECT id FROM orders WHERE tenant_id=? AND customer_mobile=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",tenantId,mobile,page*20)
                .stream().map(r->view(tenantId,number(r,"ID"))).toList();
    }

    public ConsumerQuote consumerQuote(CreateOrder command) {
        validate(command);
        var items=merchant.quote(command.tenantId(),command.storeId(),command.items().stream().map(i->new MerchantGateway.OrderItemRequest(i.skuId(),i.quantity())).toList());
        long total=0;
        for(var item:items){if(item.availableQuantity()<item.quantity())conflict("库存不足，请调整数量");total=Math.addExact(total,Math.multiplyExact(item.priceCents(),item.quantity()));}
        // The member contract requires a positive correlation ID even for non-mutating quotes.
        long freight=freight(command);
        var b=command.benefits()==null ? new MemberGateway.BenefitQuote(0,0,0,0,total,null) : members.quote(benefitRequest(command,Ids.next(),total));
        return new ConsumerQuote(b.memberId(),b.couponDiscountCents(),b.pointsUsed(),b.storedValueUsedCents(),Math.addExact(b.payableAmountCents(),freight),b.couponId(),freight);
    }

    private long freight(CreateOrder c){
        var d=c.delivery();
        if(d!=null){
            if(!"PICKUP".equals(d.method())&&!"SHIPPING".equals(d.method()))bad("履约方式无效");
            if("SHIPPING".equals(d.method())&&(d.name()==null||d.name().isBlank()||d.name().length()>64||d.phone()==null||!d.phone().matches("[0-9+ -]{7,32}")||d.province()==null||d.province().isBlank()||d.province().length()>32||d.address()==null||d.address().isBlank()||d.address().length()>512||!d.address().startsWith(d.province())||d.address().trim().length()<d.province().length()+4))bad("请完整填写收件人、电话、省份和详细地址");
        }
        long fee=merchant.shippingFee(c.tenantId(),c.storeId(),d==null?"PICKUP":d.method(),d==null?null:d.province(),c.items().stream().map(i->new MerchantGateway.OrderItemRequest(i.skuId(),i.quantity())).toList());
        if(fee<0)bad("运费无效");return fee;
    }

    @Transactional public OrderView ship(long id,String carrier,String trackingNo){
        var c=TenantContextHolder.require();var o=lockedRow(c.tenantId(),id);
        if(!merchant.canVerifyStore(c.tenantId(),c.userId(),c.dataScope(),number(o,"STORE_ID")))throw new ResponseStatusException(FORBIDDEN,"仅订单所属门店可发货");
        if(carrier==null||carrier.isBlank()||carrier.length()>64||trackingNo==null||!trackingNo.matches("[A-Za-z0-9-]{5,96}"))bad("请填写快递公司和有效单号");
        if("SHIPPED".equals(string(o,"STATUS"))&&carrier.equals(nullable(o,"CARRIER"))&&trackingNo.equals(nullable(o,"TRACKING_NO")))return merchantView(c.tenantId(),id);
        if(!"SHIPPING".equals(string(o,"DELIVERY_METHOD"))||!"PENDING_SHIPMENT".equals(string(o,"STATUS")))conflict("仅待发货邮寄订单可以发货");
        jdbc.update("UPDATE orders SET status='SHIPPED',carrier=?,tracking_no=?,shipped_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",carrier.trim(),trackingNo,c.tenantId(),id);
        return merchantView(c.tenantId(),id);
    }

    @Transactional public OrderView receive(long tenant,long id,long memberId){
        var o=lockedRow(tenant,id);
        if(number(o,"CONSUMER_MEMBER_ID")!=memberId)throw new ResponseStatusException(NOT_FOUND,"Order not found");
        if(!"SHIPPING".equals(string(o,"DELIVERY_METHOD")))conflict("自提订单请到店核销");
        if("COMPLETED".equals(string(o,"STATUS")))return view(tenant,id);
        if(!"SHIPPED".equals(string(o,"STATUS")))conflict("仅已发货订单可以确认收货");
        var now=LocalDateTime.now();
        jdbc.update("UPDATE orders SET status='COMPLETED',verified_at=?,version=version+1,updated_at=? WHERE tenant_id=? AND id=?",now,now,tenant,id);
        jdbc.update("INSERT INTO finance_ledger(id,tenant_id,store_id,business_type,business_id,direction,amount_cents,occurred_at,remark) VALUES (?,?,?,'ORDER',?,'INCOME',?,?,?)",Ids.next(),tenant,number(o,"STORE_ID"),id,number(o,"TOTAL_AMOUNT_CENTS"),now,"邮寄订单确认收货");
        if(number(o,"SHIPPING_FEE_CENTS")>0)jdbc.update("INSERT INTO finance_ledger(id,tenant_id,store_id,business_type,business_id,direction,amount_cents,occurred_at,remark) VALUES (?,?,?,'SHIPPING_FEE',?,'INCOME',?,?,?)",Ids.next(),tenant,number(o,"STORE_ID"),id,number(o,"SHIPPING_FEE_CENTS"),now,"消费者支付运费");
        return view(tenant,id);
    }

    @Transactional
    public OrderView cancelForConsumer(long tenantId,long id,String mobile) {
        var order=lockedRow(tenantId,id);
        if(!mobile.equals(string(order,"CUSTOMER_MOBILE")))throw new ResponseStatusException(NOT_FOUND,"Order not found");
        if("CLOSED".equals(string(order,"STATUS")))return view(tenantId,id);
        if(!"PENDING_PAYMENT".equals(string(order,"STATUS")))conflict("Only pending orders can be cancelled");
        close(order);
        return view(tenantId,id);
    }

    public OrderView getForMerchant(long orderId) {
        var context = TenantContextHolder.require();
        var order = row(context.tenantId(), orderId);
        if (!merchant.canAccessStore(context.tenantId(), context.userId(), context.dataScope(), number(order, "STORE_ID"))) {
            throw new ResponseStatusException(FORBIDDEN, "Order is outside store scope");
        }
        return merchantView(context.tenantId(), orderId);
    }

    public List<OrderView> merchantOrders() {
        var context = TenantContextHolder.require();
        return jdbc.queryForList("SELECT id,store_id FROM orders WHERE tenant_id=? ORDER BY created_at DESC,id DESC", context.tenantId())
                .stream().filter(r -> merchant.canAccessStore(context.tenantId(), context.userId(), context.dataScope(), number(r, "STORE_ID")))
                .map(r -> merchantView(context.tenantId(), number(r, "ID"))).toList();
    }

    public StorePerformance performance(long storeId) {
        var context = TenantContextHolder.require();
        if (!merchant.canAccessStore(context.tenantId(), context.userId(), context.dataScope(), storeId)) throw new ResponseStatusException(FORBIDDEN);
        var row = jdbc.queryForMap("""
                SELECT COUNT(*) AS completed_orders, COALESCE(SUM(amount_cents),0) AS revenue_cents
                  FROM finance_ledger WHERE tenant_id=? AND store_id=? AND business_type='ORDER' AND direction='INCOME'
                """, context.tenantId(), storeId);
        return new StorePerformance(storeId, number(row, "COMPLETED_ORDERS"), number(row, "REVENUE_CENTS"));
    }

    public List<FinanceEntry> finance(long storeId) {
        var context = TenantContextHolder.require();
        if (!merchant.canAccessStore(context.tenantId(), context.userId(), context.dataScope(), storeId)) throw new ResponseStatusException(FORBIDDEN);
        return jdbc.query("SELECT id,business_type,business_id,direction,amount_cents,occurred_at,remark FROM finance_ledger WHERE tenant_id=? AND store_id=? ORDER BY occurred_at DESC,id DESC",
                (rs,n)->new FinanceEntry(rs.getLong("id"),rs.getString("business_type"),rs.getLong("business_id"),
                        rs.getString("direction"),rs.getLong("amount_cents"),rs.getTimestamp("occurred_at").toLocalDateTime(),rs.getString("remark")),
                context.tenantId(),storeId);
    }

    @Transactional public OrderView refund(long orderId,String requestId,String reason){
        if(requestId==null||requestId.isBlank())bad("Refund request id is required");var context=TenantContextHolder.require();var order=lockedRow(context.tenantId(),orderId);long storeId=number(order,"STORE_ID");if(!merchant.canAccessStore(context.tenantId(),context.userId(),context.dataScope(),storeId))throw new ResponseStatusException(FORBIDDEN,"Order is outside store scope");
        var existing=jdbc.queryForList("SELECT id FROM refund WHERE tenant_id=? AND request_id=?",context.tenantId(),requestId);if(!existing.isEmpty())return merchantView(context.tenantId(),orderId);if(!"COMPLETED".equals(string(order,"STATUS")))conflict("Only a completed order can be refunded");
        long refundId=Ids.next();var now=LocalDateTime.now();jdbc.update("INSERT INTO refund(id,tenant_id,order_id,request_id,status,amount_cents,reason,refunded_at) VALUES (?,?,?,?,'SUCCESS',?,?,?)",refundId,context.tenantId(),orderId,requestId,Math.addExact(number(order,"TOTAL_AMOUNT_CENTS"),number(order,"SHIPPING_FEE_CENTS")),reason,now);
        for(var item:itemRows(context.tenantId(),orderId))merchant.restore(context.tenantId(),number(order,"FULFILLMENT_STORE_ID")>0?number(order,"FULFILLMENT_STORE_ID"):storeId,orderId,number(item,"SKU_ID"),number(item,"QUANTITY"),"ORDER:REFUND:"+orderId+":"+number(item,"SKU_ID"));
        if(number(order,"MEMBER_ID")>0)members.refund(benefitRequest(order));
        int changed=jdbc.update("UPDATE orders SET status='REFUNDED',version=version+1,updated_at=? WHERE tenant_id=? AND id=? AND status='COMPLETED'",now,context.tenantId(),orderId);if(changed!=1)conflict("Order status changed concurrently");
        jdbc.update("INSERT INTO finance_ledger(id,tenant_id,store_id,business_type,business_id,direction,amount_cents,occurred_at,remark) VALUES (?,?,?,'REFUND',?,'EXPENSE',?,?,?)",Ids.next(),context.tenantId(),storeId,refundId,number(order,"TOTAL_AMOUNT_CENTS"),now,reason);if(number(order,"SHIPPING_FEE_CENTS")>0)jdbc.update("INSERT INTO finance_ledger(id,tenant_id,store_id,business_type,business_id,direction,amount_cents,occurred_at,remark) VALUES (?,?,?,'SHIPPING_REFUND',?,'EXPENSE',?,?,?)",Ids.next(),context.tenantId(),storeId,refundId,number(order,"SHIPPING_FEE_CENTS"),now,"整单退款退还运费");analytics.refunded(context.tenantId(),order,number(order,"TOTAL_AMOUNT_CENTS"),now);return merchantView(context.tenantId(),orderId);
    }

    @Transactional public DirectBinding bindDirect(long tenantId,long orderId,long transferId,long source,long target,List<MerchantGateway.OrderItemRequest> requested){
        var order=lockedRow(tenantId,orderId);
        if(number(order,"STORE_ID")!=target)throw new ResponseStatusException(FORBIDDEN,"订单不属于申请门店");
        if(!"PICKUP_READY".equals(string(order,"STATUS"))||value(order,"PAID_AT")==null)conflict("仅已支付待履约订单可申请直寄");
        if(source==target||!merchant.canAccessStore(tenantId,0,"TENANT_ALL",source))bad("供货门店不属于当前租户");
        var lines=itemRows(tenantId,orderId).stream().map(i->new MerchantGateway.OrderItemRequest(number(i,"SKU_ID"),number(i,"QUANTITY"))).toList();
        if(requested==null||requested.size()!=lines.size()||!new HashSet<>(requested).equals(new HashSet<>(lines)))bad("直寄必须匹配订单全部商品及数量");
        long bound=number(order,"DIRECT_TRANSFER_ID");
        if(bound>0){if(number(order,"FULFILLMENT_STORE_ID")!=source)conflict("订单已关联其它供货门店");transferId=bound;}
        else jdbc.update("UPDATE orders SET direct_transfer_id=?,fulfillment_store_id=?,version=version+1 WHERE tenant_id=? AND id=?",transferId,source,tenantId,orderId);
        return new DirectBinding(transferId,orderId,string(order,"ORDER_NO"),number(order,"PAYABLE_AMOUNT_CENTS"));
    }
    @Transactional public void cancelDirect(long tenantId,long orderId,long transferId){
        var order=lockedRow(tenantId,orderId);
        if(!"PICKUP_READY".equals(string(order,"STATUS")))conflict("订单已履约，不能取消直寄");
        if(number(order,"DIRECT_TRANSFER_ID")==0)return;
        if(number(order,"DIRECT_TRANSFER_ID")!=transferId)conflict("订单与调拨不匹配");
        jdbc.update("UPDATE orders SET direct_transfer_id=NULL,fulfillment_store_id=NULL,version=version+1 WHERE tenant_id=? AND id=?",tenantId,orderId);
    }
    @Transactional public void completeDirect(long tenantId,long orderId,long transferId,long userId,String dataScope){
        var order=lockedRow(tenantId,orderId);
        if(number(order,"DIRECT_TRANSFER_ID")!=transferId)conflict("订单未绑定此调拨或调拨已取消");
        long source=number(order,"FULFILLMENT_STORE_ID"),target=number(order,"STORE_ID");
        if(!merchant.canAccessStore(tenantId,userId,dataScope,source))throw new ResponseStatusException(FORBIDDEN,"仅供货门店可确认直寄出库");
        if(Set.of("COMPLETED","REFUNDED").contains(string(order,"STATUS")))return;
        if(!"PICKUP_READY".equals(string(order,"STATUS")))conflict("订单状态不允许直寄出库");
        var lines=itemRows(tenantId,orderId).stream().map(i->new MerchantGateway.OrderItemRequest(number(i,"SKU_ID"),number(i,"QUANTITY"))).toList();
        merchant.fulfillDirect(tenantId,transferId,orderId,target,source,userId,dataScope,lines);
        var now=LocalDateTime.now();
        jdbc.update("UPDATE orders SET status='COMPLETED',verified_at=?,version=version+1,updated_at=? WHERE tenant_id=? AND id=?",now,now,tenantId,orderId);
        jdbc.update("INSERT INTO finance_ledger(id,tenant_id,store_id,business_type,business_id,direction,amount_cents,occurred_at,remark) VALUES (?,?,?,'ORDER',?,'INCOME',?,?,?)",Ids.next(),tenantId,target,orderId,number(order,"TOTAL_AMOUNT_CENTS"),now,"同租户门店直寄履约，调拨单 "+transferId+"，收款归原销售门店");
    }
    private Map<String,Object> lockedRow(long tenantId,long id){var rows=jdbc.queryForList("SELECT * FROM orders WHERE tenant_id=? AND id=? FOR UPDATE",tenantId,id);if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Order not found");return rows.getFirst();}
    public record DirectBinding(long transferId,long orderId,String orderNo,long paidAmountCents){}

    private void close(Map<String,Object> order) {
        long tenantId=number(order,"TENANT_ID"), orderId=number(order,"ID"), storeId=number(order,"STORE_ID");
        int changed=jdbc.update("UPDATE orders SET status='CLOSED',closed_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=? AND status='PENDING_PAYMENT'",tenantId,orderId);
        if(changed==0)return;
        for(var item:itemRows(tenantId,orderId)){
            long skuId=number(item,"SKU_ID");
            merchant.release(tenantId,storeId,orderId,skuId,number(item,"QUANTITY"),"ORDER:RELEASE:"+orderId+":"+skuId);
        }
        if(number(order,"MEMBER_ID")>0) members.release(benefitRequest(order));
    }

    private OrderView view(long tenantId,long id){
        var o=row(tenantId,id);
        var items=itemRows(tenantId,id).stream().map(i->new OrderItemView(number(i,"LINE_NO"),number(i,"SKU_ID"),string(i,"SKU_CODE"),
                string(i,"PRODUCT_NAME_SNAPSHOT"),string(i,"SKU_NAME_SNAPSHOT"),nullable(i,"SPEC_SNAPSHOT"),nullable(i,"IMAGE_URL_SNAPSHOT"),
                number(i,"UNIT_PRICE_CENTS"),number(i,"QUANTITY"),number(i,"LINE_AMOUNT_CENTS"))).toList();
        return new OrderView(number(o,"ID"),string(o,"ORDER_NO"),number(o,"TENANT_ID"),number(o,"STORE_ID"),
                string(o,"CUSTOMER_NAME"),string(o,"CUSTOMER_MOBILE"),string(o,"STATUS"),number(o,"TOTAL_QUANTITY"),
                number(o,"TOTAL_AMOUNT_CENTS"),time(o,"EXPIRES_AT"),nullable(o,"PICKUP_CODE"),nullableTime(o,"PAID_AT"),
                nullableTime(o,"VERIFIED_AT"),nullableTime(o,"CLOSED_AT"),number(o,"COUPON_DISCOUNT_CENTS"),number(o,"POINTS_USED"),number(o,"STORED_VALUE_USED_CENTS"),number(o,"PAYABLE_AMOUNT_CENTS"),number(o,"DIRECT_TRANSFER_ID"),number(o,"FULFILLMENT_STORE_ID"),items,new DeliveryView(string(o,"DELIVERY_METHOD"),number(o,"SHIPPING_FEE_CENTS"),nullable(o,"RECIPIENT_NAME"),nullable(o,"RECIPIENT_PHONE"),nullable(o,"RECIPIENT_PROVINCE"),nullable(o,"RECIPIENT_ADDRESS"),nullable(o,"CARRIER"),nullable(o,"TRACKING_NO"),nullableTime(o,"SHIPPED_AT")));
    }
    private OrderView merchantView(long tenantId,long id){var o=view(tenantId,id);return new OrderView(o.id(),o.orderNo(),o.tenantId(),o.storeId(),o.customerName(),o.customerMobile(),o.status(),o.totalQuantity(),o.totalAmountCents(),o.expiresAt(),null,o.paidAt(),o.verifiedAt(),o.closedAt(),o.couponDiscountCents(),o.pointsUsed(),o.storedValueUsedCents(),o.payableAmountCents(),o.directTransferId(),o.fulfillmentStoreId(),o.items(),o.delivery());}
    private MemberGateway.BenefitRequest benefitRequest(CreateOrder c,long id,long total){var b=c.benefits();return new MemberGateway.BenefitRequest(c.tenantId(),id,c.customerMobile(),b.couponId(),b.pointsToUse(),b.storedValueToUseCents(),total);}private MemberGateway.BenefitRequest benefitRequest(Map<String,Object>o){return new MemberGateway.BenefitRequest(number(o,"TENANT_ID"),number(o,"ID"),string(o,"CUSTOMER_MOBILE"),number(o,"COUPON_ID")==0?null:number(o,"COUPON_ID"),number(o,"POINTS_USED"),number(o,"STORED_VALUE_USED_CENTS"),number(o,"TOTAL_AMOUNT_CENTS"));}

    private Map<String,Object> row(long tenantId,long id){var rows=jdbc.queryForList("SELECT * FROM orders WHERE tenant_id=? AND id=?",tenantId,id);if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Order not found");return rows.getFirst();}
    private List<Map<String,Object>> itemRows(long tenantId,long id){return jdbc.queryForList("SELECT * FROM order_item WHERE tenant_id=? AND order_id=? ORDER BY line_no",tenantId,id);}
    private static void validate(CreateOrder c){if(c.tenantId()<=0||c.storeId()<=0||c.requestId()==null||c.requestId().isBlank()||c.customerName()==null||c.customerName().isBlank()||c.customerMobile()==null||c.customerMobile().isBlank()||c.items()==null||c.items().isEmpty())bad("Invalid order request");var ids=new HashSet<Long>();for(var i:c.items())if(i.skuId()<=0||i.quantity()<=0||!ids.add(i.skuId()))bad("Invalid or duplicate order item");}
    private static long number(Map<String,Object>r,String k){Object v=value(r,k);return v==null?0:((Number)v).longValue();}private static String string(Map<String,Object>r,String k){return String.valueOf(value(r,k));}private static String nullable(Map<String,Object>r,String k){Object v=value(r,k);return v==null?null:String.valueOf(v);}private static Object value(Map<String,Object>r,String k){Object v=r.get(k);return v==null?r.get(k.toLowerCase(Locale.ROOT)):v;}private static LocalDateTime time(Map<String,Object>r,String k){return((java.sql.Timestamp)value(r,k)).toLocalDateTime();}private static LocalDateTime nullableTime(Map<String,Object>r,String k){Object v=value(r,k);return v==null?null:((java.sql.Timestamp)v).toLocalDateTime();}
    private static boolean constantTimeEquals(String expected,String actual){if(expected==null||actual==null)return false;return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
    private static void bad(String m){throw new ResponseStatusException(BAD_REQUEST,m);}private static void conflict(String m){throw new ResponseStatusException(CONFLICT,m);}

    public record CreateItem(long skuId,long quantity){}
    public record CreateOrder(long tenantId,long storeId,String customerName,String customerMobile,String requestId,List<CreateItem> items,BenefitSelection benefits,Delivery delivery){
        public CreateOrder(long tenantId,long storeId,String customerName,String customerMobile,String requestId,List<CreateItem> items,BenefitSelection benefits){this(tenantId,storeId,customerName,customerMobile,requestId,items,benefits,null);}
    }
    public record Delivery(String method,String name,String phone,String province,String address){}
    public record DeliveryView(String method,long shippingFeeCents,String name,String phone,String province,String address,String carrier,String trackingNo,LocalDateTime shippedAt){}
    public record ConsumerQuote(long memberId,long couponDiscountCents,long pointsUsed,long storedValueUsedCents,long payableAmountCents,Long couponId,long shippingFeeCents){}
    public record BenefitSelection(Long couponId,long pointsToUse,long storedValueToUseCents){}
    public record OrderItemView(long lineNo,long skuId,String skuCode,String productName,String skuName,String specJson,String imageUrl,long unitPriceCents,long quantity,long lineAmountCents){}
    public record OrderView(long id,String orderNo,long tenantId,long storeId,String customerName,String customerMobile,String status,long totalQuantity,long totalAmountCents,LocalDateTime expiresAt,String pickupCode,LocalDateTime paidAt,LocalDateTime verifiedAt,LocalDateTime closedAt,long couponDiscountCents,long pointsUsed,long storedValueUsedCents,long payableAmountCents,long directTransferId,long fulfillmentStoreId,List<OrderItemView> items,DeliveryView delivery){}
    public record StorePerformance(long storeId,long completedOrders,long revenueCents){}
    public record FinanceEntry(long id,String businessType,long businessId,String direction,long amountCents,LocalDateTime occurredAt,String remark){}
    public record PaymentSnapshot(long id,String orderNo,String status,long amountCents,LocalDateTime expiresAt){}
}
