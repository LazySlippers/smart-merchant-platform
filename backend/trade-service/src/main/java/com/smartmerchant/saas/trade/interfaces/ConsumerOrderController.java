package com.smartmerchant.saas.trade.interfaces;

import com.smartmerchant.saas.trade.application.OrderService;
import com.smartmerchant.saas.security.ConsumerIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consumer/v1/orders")
public class ConsumerOrderController {
    @org.springframework.beans.factory.annotation.Value("${saas.trade.simulated-payment-enabled:false}")
    private boolean simulatedPaymentEnabled;
    private final OrderService orders;
    public ConsumerOrderController(OrderService orders){this.orders=orders;}

    @GetMapping("/capabilities")
    public Object capabilities(){ConsumerIdentity.require();return java.util.Map.of("simulatedPaymentEnabled",simulatedPaymentEnabled);}
    @PostMapping("/quote")
    public Object quote(@Valid @RequestBody CreateRequest request){
        ConsumerIdentity.require().check(request.tenantId(),request.customerMobile());
        return orders.consumerQuote(new OrderService.CreateOrder(request.tenantId(),request.storeId(),request.customerName(),request.customerMobile(),request.requestId(),request.items().stream().map(i->new OrderService.CreateItem(i.skuId(),i.quantity())).toList(),request.benefits()==null?null:new OrderService.BenefitSelection(request.benefits().couponId(),request.benefits().pointsToUse(),request.benefits().storedValueToUseCents()),request.delivery()));
    }

    @PostMapping
    public OrderService.OrderView create(@Valid @RequestBody CreateRequest request){
        ConsumerIdentity.require().check(request.tenantId(),request.customerMobile());
        return orders.createForConsumer(new OrderService.CreateOrder(request.tenantId(),request.storeId(),request.customerName(),
                request.customerMobile(),request.requestId(),request.items().stream().map(i->new OrderService.CreateItem(i.skuId(),i.quantity())).toList(),request.benefits()==null?null:new OrderService.BenefitSelection(request.benefits().couponId(),request.benefits().pointsToUse(),request.benefits().storedValueToUseCents()),request.delivery()),ConsumerIdentity.require().accountId());
    }
    @PostMapping("/{id}/receive")
    public Object receive(@PathVariable long id){var c=ConsumerIdentity.require();return orders.receive(c.tenantId(),id,c.accountId());}
    @GetMapping("/{id}")
    public OrderService.OrderView get(@PathVariable long id,@RequestParam long tenantId,@RequestParam String mobile){ConsumerIdentity.require().check(tenantId,mobile);orders.requireConsumerOwner(tenantId,id,ConsumerIdentity.require().accountId());return orders.getForConsumer(tenantId,id,mobile);}
    @GetMapping
    public Object list(@RequestParam(defaultValue="0") int page) {var c=ConsumerIdentity.require();return orders.ownedConsumerOrders(c.tenantId(),c.accountId(),page);}
    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable long id) {var c=ConsumerIdentity.require();orders.requireConsumerOwner(c.tenantId(),id,c.accountId());return orders.cancelForConsumer(c.tenantId(),id,c.mobile());}
    @PostMapping("/{id}/simulate-payment")
    public OrderService.OrderView pay(@PathVariable long id,@Valid @RequestBody PaymentRequest request){var c=ConsumerIdentity.require();if(!simulatedPaymentEnabled)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"模拟支付未启用");c.check(request.tenantId(),null);orders.requireConsumerOwner(c.tenantId(),id,c.accountId());orders.getForConsumer(c.tenantId(),id,c.mobile());return orders.simulatePayment(request.tenantId(),id,request.paymentRequestId());}

    public record Item(@Positive long skuId,@Positive long quantity){}
    public record PaymentRequest(@Positive long tenantId,@NotBlank String paymentRequestId){}
    public record Benefits(Long couponId,@PositiveOrZero long pointsToUse,@PositiveOrZero long storedValueToUseCents){}
    public record CreateRequest(@Positive long tenantId,@Positive long storeId,@NotBlank String customerName,
                                @NotBlank String customerMobile,@NotBlank String requestId,@NotEmpty List<@Valid Item> items,@Valid Benefits benefits,OrderService.Delivery delivery){}
}
