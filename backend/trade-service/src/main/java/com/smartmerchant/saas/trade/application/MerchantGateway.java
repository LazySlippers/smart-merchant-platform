package com.smartmerchant.saas.trade.application;

import java.util.List;

public interface MerchantGateway {
    List<QuoteItem> quote(long tenantId, long storeId, List<OrderItemRequest> items);
    void reserve(long tenantId, long storeId, long orderId, long skuId, long quantity, String idempotencyKey);
    void release(long tenantId, long storeId, long orderId, long skuId, long quantity, String idempotencyKey);
    void deduct(long tenantId, long storeId, long orderId, long skuId, long quantity, String idempotencyKey);
    void restore(long tenantId, long storeId, long orderId, long skuId, long quantity, String idempotencyKey);
    boolean canAccessStore(long tenantId, long userId, String dataScope, long storeId);
    boolean canVerifyStore(long tenantId, long userId, String dataScope, long storeId);
    default long shippingFee(long tenantId,long storeId,String method,String province,List<OrderItemRequest> items) {
        if("PICKUP".equals(method))return 0;
        throw new UnsupportedOperationException("Shipping gateway not configured");
    }

    default void fulfillDirect(long tenantId,long transferId,long orderId,long targetStoreId,long sourceStoreId,long userId,String dataScope,List<OrderItemRequest> items){throw new UnsupportedOperationException("Direct fulfilment gateway not configured");}

    record OrderItemRequest(long skuId, long quantity) {}
    record QuoteItem(long skuId, String skuCode, String productName, String skuName,
                     String specJson, String imageUrl, long priceCents, long quantity,
                     long availableQuantity) {}
}
