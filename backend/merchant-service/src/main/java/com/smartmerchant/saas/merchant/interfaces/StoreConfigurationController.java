package com.smartmerchant.saas.merchant.interfaces;
import com.smartmerchant.saas.merchant.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class StoreConfigurationController {
    private final MerchantOrganizationService organization;
    private final StorefrontService storefront;
    public StoreConfigurationController(MerchantOrganizationService organization,StorefrontService storefront){this.organization=organization;this.storefront=storefront;}
    @PutMapping("/api/merchant/v1/stores/{id}/configuration")
    @PreAuthorize("hasAuthority('merchant:store:manage')")
    @Transactional
    public Object update(@PathVariable long id,@Valid @RequestBody Configuration request){
        var r=request.store();
        var store=organization.updateStore(id,r.storeName(),r.address(),r.businessHours(),r.status(),r.version());
        var settings=storefront.update(id,request.storefront());
        return java.util.Map.of("store",store,"storefront",settings);
    }
    public record Configuration(@NotNull @Valid MerchantOrganizationController.StoreUpdate store,@NotNull StorefrontService.Settings storefront){}
}
