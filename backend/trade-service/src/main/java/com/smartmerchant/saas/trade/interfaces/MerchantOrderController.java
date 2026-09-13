package com.smartmerchant.saas.trade.interfaces;

import com.smartmerchant.saas.trade.application.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant/v1")
public class MerchantOrderController {
    private final OrderService orders;
    public MerchantOrderController(OrderService orders){this.orders=orders;}

    @GetMapping("/orders") @PreAuthorize("hasAuthority('merchant:order:view')") public Object list(){return orders.merchantOrders();}
    @GetMapping("/orders/{id}") @PreAuthorize("hasAuthority('merchant:order:view')") public Object get(@PathVariable long id){return orders.getForMerchant(id);}
    @PostMapping("/orders/expire-scan") @PreAuthorize("hasAuthority('merchant:order:view')") public Object expireScan(){return java.util.Map.of("closedOrders",orders.closeExpired());}
    @PostMapping("/orders/{id}/verify") @PreAuthorize("hasAuthority('merchant:order:verify')") public Object verify(@PathVariable long id,@Valid @RequestBody VerifyRequest request){return orders.verify(id,request.pickupCode());}
    @PostMapping("/orders/{id}/refund") @PreAuthorize("hasAuthority('merchant:order:refund')") public Object refund(@PathVariable long id,@Valid @RequestBody RefundRequest request){return orders.refund(id,request.requestId(),request.reason());}
    @GetMapping("/orders/performance") @PreAuthorize("hasAuthority('merchant:order:view')") public Object performance(@RequestParam long storeId){return orders.performance(storeId);}
    @GetMapping("/settlements/finance-ledger") @PreAuthorize("hasAuthority('merchant:finance:view')") public Object finance(@RequestParam long storeId){return orders.finance(storeId);}
    @PostMapping("/orders/{id}/ship") @PreAuthorize("hasAuthority('merchant:order:verify')") public Object ship(@PathVariable long id,@Valid @RequestBody ShipRequest request){return orders.ship(id,request.carrier(),request.trackingNo());}
    public record ShipRequest(@NotBlank String carrier,@NotBlank String trackingNo){}
    public record VerifyRequest(@NotBlank String pickupCode){}
    public record RefundRequest(@NotBlank String requestId,String reason){}
}
