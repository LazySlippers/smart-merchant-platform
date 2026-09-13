package com.smartmerchant.saas.trade.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmerchant.saas.security.Ids;
import com.smartmerchant.saas.trade.application.OrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc; private final OrderService orders; private final PaymentProviderRegistry registry; private final SandboxPaymentProvider sandbox; private final ObjectMapper json;
    public PaymentService(JdbcTemplate jdbc,OrderService orders,PaymentProviderRegistry registry,SandboxPaymentProvider sandbox,ObjectMapper json){this.jdbc=jdbc;this.orders=orders;this.registry=registry;this.sandbox=sandbox;this.json=json;}

    @Transactional
    public View create(long tenantId,long accountId,long orderId,String requestId,String providerCode){
        if(requestId==null||requestId.isBlank()||requestId.length()>96)throw new ResponseStatusException(BAD_REQUEST,"Invalid payment request id");
        orders.requireConsumerOwner(tenantId,orderId,accountId);
        var order=orders.paymentSnapshot(tenantId,orderId);
        var replay=jdbc.queryForList("SELECT * FROM payment_order WHERE tenant_id=? AND request_id=?",tenantId,requestId);
        if(!replay.isEmpty()){if(number(replay.getFirst(),"ORDER_ID")!=orderId)throw new ResponseStatusException(CONFLICT,"Payment request id already used");return view(replay.getFirst());}
        var existing=jdbc.queryForList("SELECT * FROM payment_order WHERE tenant_id=? AND order_id=?",tenantId,orderId);
        if(!existing.isEmpty())return view(existing.getFirst());
        if(!"PENDING_PAYMENT".equals(order.status()))throw new ResponseStatusException(CONFLICT,"Only a pending order can be paid");
        if(!LocalDateTime.now().isBefore(order.expiresAt())){orders.closeExpiredOrder(tenantId,orderId);throw new ResponseStatusException(CONFLICT,"Order has expired");}
        var provider=registry.require(providerCode);var created=provider.create(new PaymentProvider.Request(tenantId,orderId,order.orderNo(),order.amountCents(),requestId));
        long id=Ids.next();var payload=created.checkoutPayload();
        try{jdbc.update("INSERT INTO payment_order(id,tenant_id,order_id,request_id,provider,provider_trade_no,status,amount_cents,checkout_payload,expires_at) VALUES (?,?,?,?,?,?,'PENDING',?,?,?)",id,tenantId,orderId,requestId,provider.code(),created.providerTradeNo(),order.amountCents(),payload,order.expiresAt());}
        catch(DuplicateKeyException e){var race=jdbc.queryForList("SELECT * FROM payment_order WHERE tenant_id=? AND order_id=?",tenantId,orderId);if(!race.isEmpty())return view(race.getFirst());throw e;}
        return new View(id,orderId,provider.code(),"PENDING",order.amountCents(),created.checkoutType(),payload,order.expiresAt());
    }

    @Transactional
    public CallbackResult callback(String providerCode,String raw,String timestamp,String signature){
        var provider=registry.require(providerCode);var event=provider.verify(raw,timestamp,signature);
        var duplicate=jdbc.queryForList("SELECT result,payment_order_id FROM payment_callback_event WHERE provider=? AND event_id=?",provider.code(),event.eventId());
        if(!duplicate.isEmpty())return new CallbackResult(string(duplicate.getFirst(),"RESULT"),number(duplicate.getFirst(),"PAYMENT_ORDER_ID"));
        var rows=jdbc.queryForList("SELECT * FROM payment_order WHERE provider=? AND provider_trade_no=? FOR UPDATE",provider.code(),event.providerTradeNo());
        if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Payment not found");var payment=rows.getFirst();
        if(number(payment,"TENANT_ID")!=event.tenantId()||number(payment,"AMOUNT_CENTS")!=event.amountCents())throw new ResponseStatusException(BAD_REQUEST,"Payment callback does not match payment");
        String result;
        if("SUCCEEDED".equals(string(payment,"STATUS"))) result="SUCCEEDED";
        else if(Set.of("CLOSED","FAILED").contains(string(payment,"STATUS")))result="IGNORED";
        else if(!event.succeeded()){jdbc.update("UPDATE payment_order SET status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE id=?",number(payment,"ID"));result="FAILED";}
        else if(!LocalDateTime.now().isBefore(time(payment,"EXPIRES_AT"))){jdbc.update("UPDATE payment_order SET status='CLOSED',closed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",number(payment,"ID"));orders.closeExpiredOrder(event.tenantId(),number(payment,"ORDER_ID"));result="IGNORED";}
        else {orders.confirmPayment(event.tenantId(),number(payment,"ORDER_ID"),string(payment,"REQUEST_ID"),provider.code(),event.providerTradeNo());jdbc.update("UPDATE payment_order SET status='SUCCEEDED',succeeded_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",number(payment,"ID"));result="SUCCEEDED";}
        jdbc.update("INSERT INTO payment_callback_event(id,provider,event_id,tenant_id,payment_order_id,result) VALUES (?,?,?,?,?,?)",Ids.next(),provider.code(),event.eventId(),event.tenantId(),number(payment,"ID"),result);
        return new CallbackResult(result,number(payment,"ID"));
    }

    public View get(long tenantId,long accountId,long orderId){orders.requireConsumerOwner(tenantId,orderId,accountId);var rows=jdbc.queryForList("SELECT * FROM payment_order WHERE tenant_id=? AND order_id=?",tenantId,orderId);return rows.isEmpty()?null:view(rows.getFirst());}
    @Transactional public CallbackResult completeSandbox(long tenantId,long accountId,long paymentId){var rows=jdbc.queryForList("SELECT * FROM payment_order WHERE tenant_id=? AND id=?",tenantId,paymentId);if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Payment not found");var p=rows.getFirst();orders.requireConsumerOwner(tenantId,number(p,"ORDER_ID"),accountId);if(!"SANDBOX".equals(string(p,"PROVIDER")))throw new ResponseStatusException(BAD_REQUEST,"Not a sandbox payment");try{String ts=String.valueOf(java.time.Instant.now().getEpochSecond());String body=json.writeValueAsString(Map.of("eventId","evt-"+UUID.randomUUID(),"tenantId",tenantId,"providerTradeNo",string(p,"PROVIDER_TRADE_NO"),"amountCents",number(p,"AMOUNT_CENTS"),"status","SUCCEEDED"));return callback("SANDBOX",body,ts,sandbox.sign(ts,body));}catch(Exception e){if(e instanceof RuntimeException r)throw r;throw new IllegalStateException(e);}}
    @Transactional public int closeExpired(){int count=jdbc.update("UPDATE payment_order SET status='CLOSED',closed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE status='PENDING' AND expires_at<=CURRENT_TIMESTAMP");return count;}
    public List<PaymentProviderRegistry.Capability> capabilities(){return registry.capabilities();}
    private View view(Map<String,Object> p){return new View(number(p,"ID"),number(p,"ORDER_ID"),string(p,"PROVIDER"),string(p,"STATUS"),number(p,"AMOUNT_CENTS"),"SANDBOX".equals(string(p,"PROVIDER"))?"SANDBOX":"REDIRECT",nullable(p,"CHECKOUT_PAYLOAD"),time(p,"EXPIRES_AT"));}
    private static Object value(Map<String,Object>r,String k){Object v=r.get(k);return v==null?r.get(k.toLowerCase(Locale.ROOT)):v;}private static long number(Map<String,Object>r,String k){Object v=value(r,k);return v==null?0:((Number)v).longValue();}private static String string(Map<String,Object>r,String k){return String.valueOf(value(r,k));}private static String nullable(Map<String,Object>r,String k){Object v=value(r,k);return v==null?null:String.valueOf(v);}private static LocalDateTime time(Map<String,Object>r,String k){return((java.sql.Timestamp)value(r,k)).toLocalDateTime();}
    public record View(long id,long orderId,String provider,String status,long amountCents,String checkoutType,String checkoutPayload,LocalDateTime expiresAt){}
    public record CallbackResult(String result,long paymentId){}
}
