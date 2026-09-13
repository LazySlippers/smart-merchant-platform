package com.smartmerchant.saas.trade.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public final class SandboxPaymentProvider implements PaymentProvider {
    private final ObjectMapper json; private final String secret; private final boolean enabled; private final long toleranceSeconds;
    public SandboxPaymentProvider(ObjectMapper json,
        @Value("${saas.trade.payment.sandbox.enabled:true}") boolean enabled,
        @Value("${saas.trade.payment.sandbox.callback-secret:local-sandbox-callback-secret-change-me}") String secret,
        @Value("${saas.trade.payment.callback-tolerance-seconds:300}") long toleranceSeconds){this.json=json;this.enabled=enabled;this.secret=secret;this.toleranceSeconds=toleranceSeconds;}
    public String code(){return "SANDBOX";} public boolean enabled(){return enabled;}
    public Created create(Request r){return new Created("SBX-"+UUID.randomUUID(),"SANDBOX","{\"mode\":\"LOCAL_SANDBOX\"}");}
    public Callback verify(String body,String timestamp,String signature){
        try {
            long epoch=Long.parseLong(timestamp);
            if(Math.abs(Instant.now().getEpochSecond()-epoch)>toleranceSeconds)throw new ResponseStatusException(UNAUTHORIZED,"Stale payment callback");
            byte[] expected=hmac(timestamp+"."+body);
            byte[] actual=HexFormat.of().parseHex(signature==null?"":signature);
            if(!MessageDigest.isEqual(expected,actual))throw new ResponseStatusException(UNAUTHORIZED,"Invalid payment callback signature");
            var n=json.readTree(body);
            return new Callback(required(n,"eventId"),n.path("tenantId").asLong(),required(n,"providerTradeNo"),n.path("amountCents").asLong(),"SUCCEEDED".equals(n.path("status").asText()));
        } catch(ResponseStatusException e){throw e;} catch(Exception e){throw new ResponseStatusException(BAD_REQUEST,"Invalid payment callback",e);}
    }
    public String sign(String timestamp,String body){return HexFormat.of().formatHex(hmac(timestamp+"."+body));}
    private byte[] hmac(String value){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String required(com.fasterxml.jackson.databind.JsonNode n,String key){var v=n.path(key).asText();if(v.isBlank())throw new ResponseStatusException(BAD_REQUEST,"Missing "+key);return v;}
}
