package com.smartmerchant.saas.merchant.interfaces;

import com.smartmerchant.saas.merchant.application.ConsumerCatalogService;
import com.smartmerchant.saas.merchant.application.InventoryService;
import com.smartmerchant.saas.merchant.application.StoreAccessGuard;
import com.smartmerchant.saas.security.TenantContext;
import com.smartmerchant.saas.security.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/internal/v1/trade")
public class TradeInternalController {
    private final ConsumerCatalogService catalog;
    private final InventoryService inventory;
    private final StoreAccessGuard access;
    private final String internalKey;
    @org.springframework.beans.factory.annotation.Autowired private com.smartmerchant.saas.merchant.application.StorefrontService storefront;

    @PostMapping("/shipping/quote")
    public Object shipping(@RequestHeader("X-Internal-Key") String key,@RequestBody ShippingRequest request){
        requireKey(key);
        if(!"PICKUP".equals(request.method())&&!"SHIPPING".equals(request.method()))throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"履约方式无效");
        return java.util.Map.of("shippingFeeCents",storefront.freight(request.tenantId(),request.storeId(),request.method(),request.province(),request.items()));
    }
    public record ShippingRequest(long tenantId,long storeId,String method,String province,List<ConsumerCatalogService.QuoteRequestItem> items){}

    public TradeInternalController(ConsumerCatalogService catalog, InventoryService inventory,
                                   StoreAccessGuard access,
                                   @Value("${saas.security.internal-signing-key}") String internalKey) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.access = access;
        this.internalKey = internalKey;
    }

    @PostMapping("/catalog/quote")
    public Object quote(@RequestHeader("X-Internal-Key") String key, @RequestBody QuoteRequest request) {
        requireKey(key);
        return catalog.quote(request.tenantId(), request.storeId(), request.items());
    }

    @PostMapping("/inventory/{skuId}/{operation}")
    public Object inventory(@RequestHeader("X-Internal-Key") String key, @PathVariable long skuId,
                            @PathVariable String operation, @RequestBody StockRequest request) {
        requireKey(key);
        return TenantContextHolder.callAs(context(request.tenantId(), 0, "TENANT_ALL"), () -> switch (operation) {
            case "reserve" -> inventory.reserve(request.storeId(), skuId, request.quantity(), request.idempotencyKey(), request.orderId());
            case "release" -> inventory.release(request.storeId(), skuId, request.quantity(), request.idempotencyKey(), request.orderId());
            case "deduct" -> inventory.deductReserved(request.storeId(), skuId, request.quantity(), request.idempotencyKey(), request.orderId());
            case "restore" -> inventory.restore(request.storeId(), skuId, request.quantity(), request.idempotencyKey(), request.orderId());
            default -> throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Inventory operation not found");
        });
    }

    @GetMapping("/store-access")
    public AccessResponse storeAccess(@RequestHeader("X-Internal-Key") String key,
                                      @RequestParam long tenantId, @RequestParam long userId,
                                      @RequestParam String dataScope, @RequestParam long storeId,
                                      @RequestParam(defaultValue="false") boolean strictStore) {
        requireKey(key);
        return TenantContextHolder.callAs(context(tenantId, userId, dataScope),
                () -> new AccessResponse(strictStore ? access.canOperateAtStore(storeId) : access.canAccess(storeId)));
    }

    private void requireKey(String key) {
        if (!internalKey.equals(key)) throw new ResponseStatusException(FORBIDDEN, "Invalid internal key");
    }

    private static TenantContext context(long tenantId, long userId, String scope) {
        return new TenantContext(tenantId, userId, false, scope, Set.of());
    }

    public record QuoteRequest(long tenantId, long storeId, List<ConsumerCatalogService.QuoteRequestItem> items) {}
    public record StockRequest(long tenantId, long storeId, long quantity, long orderId, String idempotencyKey) {}
    public record AccessResponse(boolean allowed) {}
}
