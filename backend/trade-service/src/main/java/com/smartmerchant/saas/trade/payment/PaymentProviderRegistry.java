package com.smartmerchant.saas.trade.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public final class PaymentProviderRegistry {
    private final Map<String,PaymentProvider> providers;
    PaymentProviderRegistry(SandboxPaymentProvider sandbox,
        @Value("${saas.trade.payment.wechat.enabled:false}") boolean wechat,
        @Value("${saas.trade.payment.alipay.enabled:false}") boolean alipay){providers=Map.of("SANDBOX",sandbox,"WECHAT",new ConfiguredPaymentProvider("WECHAT",wechat),"ALIPAY",new ConfiguredPaymentProvider("ALIPAY",alipay));}
    PaymentProvider require(String code){if(code==null)throw new ResponseStatusException(BAD_REQUEST,"Payment provider is required");var p=providers.get(code.toUpperCase(Locale.ROOT));if(p==null)throw new ResponseStatusException(BAD_REQUEST,"Unknown payment provider");if(!p.enabled())throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Payment provider is not enabled");return p;}
    public List<Capability> capabilities(){return providers.values().stream().map(p->new Capability(p.code(),p.enabled(),switch(p.code()){case "SANDBOX"->"本地沙箱";case "WECHAT"->"微信支付";case "ALIPAY"->"支付宝";default->p.code();})).sorted(Comparator.comparing(Capability::code)).toList();}
    public record Capability(String code,boolean enabled,String displayName){}
}
