package com.smartmerchant.saas.trade.interfaces;

import com.smartmerchant.saas.security.ConsumerIdentity;
import com.smartmerchant.saas.trade.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentController {
    private final PaymentService payments; public PaymentController(PaymentService payments){this.payments=payments;}
    @GetMapping("/api/consumer/v1/payments/capabilities") public Object capabilities(){ConsumerIdentity.require();return payments.capabilities();}
    @PostMapping("/api/consumer/v1/orders/{orderId}/payments") public Object create(@PathVariable long orderId,@Valid @RequestBody Create r){var c=ConsumerIdentity.require();return payments.create(c.tenantId(),c.accountId(),orderId,r.requestId(),r.provider());}
    @GetMapping("/api/consumer/v1/orders/{orderId}/payment") public Object get(@PathVariable long orderId){var c=ConsumerIdentity.require();return payments.get(c.tenantId(),c.accountId(),orderId);}
    @PostMapping("/api/consumer/v1/payments/{paymentId}/sandbox/complete") public Object sandbox(@PathVariable long paymentId){var c=ConsumerIdentity.require();return payments.completeSandbox(c.tenantId(),c.accountId(),paymentId);}
    @PostMapping(value="/api/payment/v1/callback/{provider}",consumes=MediaType.APPLICATION_JSON_VALUE) public Object callback(@PathVariable String provider,@RequestHeader("X-Payment-Timestamp") String timestamp,@RequestHeader("X-Payment-Signature") String signature,@RequestBody String body){return payments.callback(provider.toUpperCase(),body,timestamp,signature);}
    public record Create(@NotBlank String requestId,@NotBlank String provider){}
}
