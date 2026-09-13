package com.smartmerchant.saas.merchant.interfaces;

import com.smartmerchant.saas.merchant.application.StorefrontService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class StorefrontController {
    private final StorefrontService service;
    public StorefrontController(StorefrontService service){this.service=service;}
    @GetMapping("/api/consumer/v1/catalog/stores")
    public Object discover(@RequestParam(required=false) Double latitude,@RequestParam(required=false) Double longitude,@RequestParam(defaultValue="") String area,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.discover(latitude,longitude,area,search,page);}
    @GetMapping("/api/consumer/v1/catalog/tenants/{tenantId}/stores/{storeId}/fulfillment")
    public Object publicSettings(@PathVariable long tenantId,@PathVariable long storeId){return service.publicSettings(tenantId,storeId);}
    @GetMapping("/api/merchant/v1/stores/{id}/storefront") @PreAuthorize("hasAuthority('merchant:store:view')")
    public Object get(@PathVariable long id){return service.settings(id);}
    @PutMapping("/api/merchant/v1/stores/{id}/storefront") @PreAuthorize("hasAuthority('merchant:store:manage')")
    public Object update(@PathVariable long id,@RequestBody StorefrontService.Settings settings){return service.update(id,settings);}
}
