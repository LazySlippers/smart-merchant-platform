package com.smartmerchant.saas.merchant.domain;
import com.baomidou.mybatisplus.annotation.TableName;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("product_sku") public class ProductSku {private Long id;private Long tenantId;private Long spuId;private String skuCode;private String skuName;private String specJson;private String barcode;private Long basePriceCents;private Boolean allowStorePrice;private String status;private Integer version;private Long createdBy;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
