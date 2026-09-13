package com.smartmerchant.saas.merchant.interfaces;
import com.smartmerchant.saas.merchant.application.StockTransferService;
import com.smartmerchant.saas.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/internal/v1/trade/direct-transfers")
public class DirectTransferInternalController {
 private final StockTransferService transfers;private final String key;
 public DirectTransferInternalController(StockTransferService transfers,@Value("${saas.security.internal-signing-key}")String key){this.transfers=transfers;this.key=key;}
 @PostMapping("/fulfill") public void fulfill(@RequestHeader("X-Internal-Key")String supplied,@RequestBody Fulfill request){
  if(!key.equals(supplied))throw new ResponseStatusException(FORBIDDEN);
  TenantContextHolder.callAs(new TenantContext(request.tenantId(),request.userId(),false,request.dataScope(),Set.of()),()->transfers.fulfillDirect(request.transferId(),request.orderId(),request.targetStoreId(),request.sourceStoreId(),request.items()));
 }
 public record Fulfill(long tenantId,long transferId,long orderId,long targetStoreId,long sourceStoreId,long userId,String dataScope,List<StockTransferService.Line> items){}
}
