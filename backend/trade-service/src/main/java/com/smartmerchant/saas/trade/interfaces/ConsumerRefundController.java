package com.smartmerchant.saas.trade.interfaces;
import com.smartmerchant.saas.trade.application.ConsumerRefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class ConsumerRefundController {
    private final ConsumerRefundService refunds;
    public ConsumerRefundController(ConsumerRefundService refunds){this.refunds=refunds;}
    @GetMapping("/api/consumer/v1/orders/{id}/refund-request") public Object get(@PathVariable long id){return refunds.get(id);}
    @PostMapping("/api/consumer/v1/orders/{id}/refund-request") public Object apply(@PathVariable long id,@Valid @RequestBody Apply r){return refunds.apply(id,r.reason());}
    @GetMapping("/api/merchant/v1/orders/{id}/refund-request") @PreAuthorize("hasAuthority('merchant:order:view')") public Object merchantGet(@PathVariable long id){return refunds.merchantGet(id);}
    @PostMapping("/api/merchant/v1/orders/{id}/refund-request") @PreAuthorize("hasAuthority('merchant:order:refund')") public Object decide(@PathVariable long id,@Valid @RequestBody Decision r){return refunds.decide(id,r.approve(),r.reply());}
    public record Apply(@NotBlank @Size(max=500) String reason){}
    public record Decision(boolean approve,@NotBlank @Size(max=500) String reply){}
}
