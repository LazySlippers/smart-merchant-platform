package com.smartmerchant.saas.merchant.interfaces;
import com.smartmerchant.saas.merchant.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/merchant/v1/inventory")
public class TenantInventoryController {
 private final TenantInventoryService service;private final InventoryService inventory;
 public TenantInventoryController(TenantInventoryService service,InventoryService inventory){this.service=service;this.inventory=inventory;}
 @GetMapping("/tenant-stock") @PreAuthorize("hasAuthority('merchant:inventory:view')") public Object stock(@RequestParam(required=false)Long storeId){return InventoryController.json(service.stock(storeId));}
 @GetMapping("/low-stock-alerts") @PreAuthorize("hasAuthority('merchant:inventory:view')") public Object alerts(){return InventoryController.json(service.alerts());}
 @GetMapping("/allocations") @PreAuthorize("hasAuthority('merchant:inventory:view')") public Object allocations(){return InventoryController.json(service.allocations());}
 @PostMapping("/allocations") @PreAuthorize("hasAuthority('merchant:inventory:manage')") public Object allocate(@Valid @RequestBody Allocation r){return service.allocate(r.requestId(),r.reason(),r.items().stream().map(i->new TenantInventoryService.Line(i.storeId(),i.skuId(),i.quantity())).toList());}
 @PutMapping("/threshold") @PreAuthorize("hasAuthority('merchant:inventory:manage')") public void threshold(@Valid @RequestBody Threshold r){inventory.threshold(r.storeId(),r.skuId(),r.lowStockThreshold());}
 public record Item(@Positive long storeId,@Positive long skuId,@Positive long quantity){}
 public record Allocation(@NotBlank @Size(max=64)String requestId,@NotBlank @Size(max=512)String reason,@NotEmpty @Size(max=100)List<@Valid Item> items){}
 public record Threshold(@Positive long storeId,@Positive long skuId,@PositiveOrZero long lowStockThreshold){}
}
