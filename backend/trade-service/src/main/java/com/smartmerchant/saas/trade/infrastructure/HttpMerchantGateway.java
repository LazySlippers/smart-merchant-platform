package com.smartmerchant.saas.trade.infrastructure;

import com.smartmerchant.saas.trade.application.MerchantGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class HttpMerchantGateway implements MerchantGateway {
    private final RestClient client;
    private final String internalKey;

    public HttpMerchantGateway(@Value("${saas.trade.merchant-service-url}") String merchantUrl,
                               @Value("${saas.security.internal-signing-key}") String internalKey) {
        this.client = RestClient.builder().baseUrl(merchantUrl).build();
        this.internalKey = internalKey;
    }

    @Override
    public List<QuoteItem> quote(long tenantId, long storeId, List<OrderItemRequest> items) {
        return client.post().uri("/internal/v1/trade/catalog/quote").header("X-Internal-Key", internalKey)
                .body(new QuoteRequest(tenantId, storeId, items)).retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override public void reserve(long tenantId,long storeId,long orderId,long skuId,long quantity,String key){stock("reserve",tenantId,storeId,orderId,skuId,quantity,key);}
    @Override public long shippingFee(long tenantId,long storeId,String method,String province,List<OrderItemRequest> items){
        try {
            var result=client.post().uri("/internal/v1/trade/shipping/quote").header("X-Internal-Key",internalKey).body(new ShippingRequest(tenantId,storeId,method,province,items)).retrieve().body(ShippingQuote.class);
            if(result==null||result.shippingFeeCents()<0)throw new IllegalStateException("Invalid freight quote");
            return result.shippingFeeCents();
        } catch(org.springframework.web.client.HttpClientErrorException e){throw new org.springframework.web.server.ResponseStatusException(e.getStatusCode(),"门店或配送方式不可用，请检查收货地区及仅自提商品");}
    }
    private record ShippingRequest(long tenantId,long storeId,String method,String province,List<OrderItemRequest> items){}
    private record ShippingQuote(long shippingFeeCents){}
    @Override public void release(long tenantId,long storeId,long orderId,long skuId,long quantity,String key){stock("release",tenantId,storeId,orderId,skuId,quantity,key);}
    @Override public void deduct(long tenantId,long storeId,long orderId,long skuId,long quantity,String key){stock("deduct",tenantId,storeId,orderId,skuId,quantity,key);}
    @Override public void restore(long tenantId,long storeId,long orderId,long skuId,long quantity,String key){stock("restore",tenantId,storeId,orderId,skuId,quantity,key);}

    private void stock(String operation,long tenantId,long storeId,long orderId,long skuId,long quantity,String key){
        client.post().uri("/internal/v1/trade/inventory/{skuId}/{operation}",skuId,operation)
                .header("X-Internal-Key",internalKey)
                .body(new StockRequest(tenantId,storeId,quantity,orderId,key)).retrieve().toBodilessEntity();
    }

    @Override
    public boolean canAccessStore(long tenantId, long userId, String dataScope, long storeId) {
        return access(tenantId,userId,dataScope,storeId,false);
    }
    @Override public boolean canVerifyStore(long tenantId,long userId,String dataScope,long storeId){return access(tenantId,userId,dataScope,storeId,true);}
    private boolean access(long tenantId,long userId,String dataScope,long storeId,boolean strictStore){
        var response=client.get().uri(uri -> uri.path("/internal/v1/trade/store-access")
                        .queryParam("tenantId",tenantId).queryParam("userId",userId)
                        .queryParam("dataScope",dataScope).queryParam("storeId",storeId).queryParam("strictStore",strictStore).build())
                .header("X-Internal-Key",internalKey).retrieve().body(AccessResponse.class);
        return response != null && response.allowed();
    }

    @Override public void fulfillDirect(long tenantId,long transferId,long orderId,long targetStoreId,long sourceStoreId,long userId,String dataScope,List<OrderItemRequest> items){
        client.post().uri("/internal/v1/trade/direct-transfers/fulfill").header("X-Internal-Key",internalKey).body(new DirectFulfill(tenantId,transferId,orderId,targetStoreId,sourceStoreId,userId,dataScope,items)).retrieve().toBodilessEntity();
    }
    private record DirectFulfill(long tenantId,long transferId,long orderId,long targetStoreId,long sourceStoreId,long userId,String dataScope,List<OrderItemRequest> items){}
    private record QuoteRequest(long tenantId,long storeId,List<OrderItemRequest> items){}
    private record StockRequest(long tenantId,long storeId,long quantity,long orderId,String idempotencyKey){}
    private record AccessResponse(boolean allowed){}
}
