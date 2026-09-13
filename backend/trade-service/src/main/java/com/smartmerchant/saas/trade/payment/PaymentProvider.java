package com.smartmerchant.saas.trade.payment;

public interface PaymentProvider {
    String code();
    boolean enabled();
    Created create(Request request);
    Callback verify(String rawBody, String timestamp, String signature);

    record Request(long tenantId,long orderId,String orderNo,long amountCents,String requestId){}
    record Created(String providerTradeNo,String checkoutType,String checkoutPayload){}
    record Callback(String eventId,long tenantId,String providerTradeNo,long amountCents,boolean succeeded){}
}
