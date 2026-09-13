package com.smartmerchant.saas.merchant.application;

import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Component
public class DirectOrderGateway {
    private final RestClient client;
    private final String key;
    public DirectOrderGateway(@Value("${saas.merchant.trade-service-url:http://localhost:8085}") String url,
                              @Value("${saas.security.internal-signing-key}") String key) {
        this.client=RestClient.builder().baseUrl(url).build();this.key=key;
    }
    public Binding bind(long orderId,long transferId,long source,long target,List<StockTransferService.Line> items) {
        return call("bind",new Command(TenantContextHolder.requireTenantId(),orderId,transferId,source,target,items),Binding.class);
    }
    public void cancel(long orderId,long transferId) { call("cancel",new Command(TenantContextHolder.requireTenantId(),orderId,transferId,0,0,List.of()),Void.class); }
    public void complete(long orderId,long transferId) {
        var c=TenantContextHolder.require();
        call("complete",new Completion(c.tenantId(),orderId,transferId,c.userId(),c.dataScope()),Void.class);
    }
    private <T> T call(String action,Object body,Class<T> type) {
        try{return client.post().uri("/internal/v1/trade/direct-transfers/"+action).header("X-Internal-Key",key).body(body).retrieve().body(type);}
        catch(RestClientResponseException e){throw new ResponseStatusException(e.getStatusCode(),"关联订单状态已变化或无法处理，请刷新订单后重试",e);}
    }
    public record Binding(long transferId,long orderId,String orderNo,long paidAmountCents){}
    public record Command(long tenantId,long orderId,long transferId,long sourceStoreId,long targetStoreId,List<StockTransferService.Line> items){}
    public record Completion(long tenantId,long orderId,long transferId,long userId,String dataScope){}
}
