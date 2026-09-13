package com.smartmerchant.saas.merchant.interfaces;
import com.smartmerchant.saas.merchant.application.CatalogService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/merchant/v1") public class CatalogController {
 private final CatalogService service;public CatalogController(CatalogService service){this.service=service;}
 @GetMapping("/categories") @PreAuthorize("hasAuthority('merchant:product:view')") public Object categories(){return service.categories();}
 @PostMapping("/categories") @PreAuthorize("hasAuthority('merchant:product:manage')") public Object category(@Valid @RequestBody CategoryCreate r){return service.createCategory(r.parentId(),r.categoryCode(),r.categoryName(),r.sortOrder());}
 @GetMapping("/products") @PreAuthorize("hasAuthority('merchant:product:view')") public Object products(@RequestParam(required=false)Long storeId){return service.products(storeId);}
 @PostMapping("/products") @PreAuthorize("hasAuthority('merchant:product:manage')") public Object product(@Valid @RequestBody SpuCreate r){return service.createSpu(r.categoryId(),r.spuCode(),r.productName(),r.description(),r.imageUrl());}
 @PutMapping("/products/{id}") @PreAuthorize("hasAuthority('merchant:product:manage')") public Object updateProduct(@PathVariable long id,@Valid @RequestBody SpuUpdate r){return service.updateSpu(id,r.categoryId(),r.productName(),r.description(),r.imageUrl(),r.status(),r.version());}
 @PostMapping("/products/{spuId}/skus") @PreAuthorize("hasAuthority('merchant:product:manage')") public Object sku(@PathVariable long spuId,@Valid @RequestBody SkuCreate r){return service.createSku(spuId,r.skuCode(),r.skuName(),r.specJson(),r.barcode(),r.basePriceCents(),r.allowStorePrice());}
 @PutMapping("/skus/{id}") @PreAuthorize("hasAnyAuthority('merchant:product:manage','merchant:price:manage')") public Object updateSku(@PathVariable long id,@Valid @RequestBody SkuUpdate r){return service.updateSku(id,r.skuName(),r.specJson(),r.barcode(),r.basePriceCents(),r.allowStorePrice(),r.status(),r.version());}
 @PutMapping("/stores/{storeId}/products/{skuId}") @PreAuthorize("hasAnyAuthority('merchant:store-product:manage','merchant:price:manage')") public Object configure(@PathVariable long storeId,@PathVariable long skuId,@Valid @RequestBody StoreProductUpdate r){return service.configureStore(storeId,skuId,r.sellable(),r.storePriceCents());}
 @PostMapping("/products/with-sku") @PreAuthorize("hasAuthority('merchant:product:manage')") public Object withSku(@Valid @RequestBody ProductCreate r){return service.createWithSku(r.categoryId(),r.productCode(),r.productName(),r.skuCode(),r.skuName(),r.barcode(),r.priceCents(),r.imageUrl());}
 public record ProductCreate(@Positive long categoryId,@NotBlank @Size(max=64) String productCode,@NotBlank @Size(max=160) String productName,@NotBlank @Size(max=64) String skuCode,@NotBlank @Size(max=128) String skuName,@Size(max=64) String barcode,@PositiveOrZero long priceCents,@Size(max=512) String imageUrl){}
 public record CategoryCreate(Long parentId,@NotBlank String categoryCode,@NotBlank String categoryName,@NotNull @PositiveOrZero Integer sortOrder){}
 public record SpuCreate(@Positive long categoryId,@NotBlank String spuCode,@NotBlank String productName,String description,String imageUrl){}
 public record SpuUpdate(@Positive long categoryId,@NotBlank String productName,String description,String imageUrl,@Pattern(regexp="DRAFT|ACTIVE|INACTIVE")String status,@NotNull @PositiveOrZero Integer version){}
 public record SkuCreate(@NotBlank String skuCode,@NotBlank String skuName,String specJson,String barcode,@PositiveOrZero long basePriceCents,boolean allowStorePrice){}
 public record SkuUpdate(@NotBlank String skuName,String specJson,String barcode,@PositiveOrZero long basePriceCents,boolean allowStorePrice,@Pattern(regexp="DRAFT|ACTIVE|INACTIVE")String status,@NotNull @PositiveOrZero Integer version){}
 public record StoreProductUpdate(boolean sellable,@PositiveOrZero Long storePriceCents){}
}
