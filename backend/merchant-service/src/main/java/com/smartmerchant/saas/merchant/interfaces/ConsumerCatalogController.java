package com.smartmerchant.saas.merchant.interfaces;

import com.smartmerchant.saas.merchant.application.ConsumerCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/consumer/v1/catalog/tenants/{tenantId}")
public class ConsumerCatalogController {
    private final ConsumerCatalogService catalog;

    public ConsumerCatalogController(ConsumerCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/stores")
    public List<ConsumerCatalogService.StoreView> stores(@PathVariable long tenantId) {
        return catalog.stores(tenantId);
    }

    @GetMapping("/stores/{storeId}/products")
    public ConsumerCatalogService.StoreCatalogView products(@PathVariable long tenantId,
                                                             @PathVariable long storeId) {
        return catalog.products(tenantId, storeId);
    }
}
