package com.smartmerchant.saas.trade.interfaces;
import com.smartmerchant.saas.trade.application.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/internal/v1/trade/direct-transfers")
public class DirectTransferOrderController {
 private final OrderService orders;private final String key;
 public DirectTransferOrderController(OrderService orders,@Value("${saas.security.internal-signing-key}")String key){this.orders=orders;this.key=key;}
 private void authenticate(String supplied){if(!key.equals(supplied))throw new ResponseStatusException(FORBIDDEN);}
 @PostMapping("/bind") public Object bind(@RequestHeader("X-Internal-Key")String supplied,@RequestBody Binding r){authenticate(supplied);return orders.bindDirect(r.tenantId(),r.orderId(),r.transferId(),r.sourceStoreId(),r.targetStoreId(),r.items());}
 @PostMapping("/cancel") public void cancel(@RequestHeader("X-Internal-Key")String supplied,@RequestBody Binding r){authenticate(supplied);orders.cancelDirect(r.tenantId(),r.orderId(),r.transferId());}
 @PostMapping("/complete") public void complete(@RequestHeader("X-Internal-Key")String supplied,@RequestBody Completion r){authenticate(supplied);orders.completeDirect(r.tenantId(),r.orderId(),r.transferId(),r.userId(),r.dataScope());}
 public record Binding(long tenantId,long orderId,long transferId,long sourceStoreId,long targetStoreId,List<MerchantGateway.OrderItemRequest> items){}
 public record Completion(long tenantId,long orderId,long transferId,long userId,String dataScope){}
}
