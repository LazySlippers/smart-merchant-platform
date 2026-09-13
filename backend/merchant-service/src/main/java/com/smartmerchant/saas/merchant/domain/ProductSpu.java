package com.smartmerchant.saas.merchant.domain;
import com.baomidou.mybatisplus.annotation.TableName;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("product_spu") public class ProductSpu {private Long id;private Long tenantId;private Long categoryId;private String spuCode;private String productName;private String description;private String imageUrl;private String status;private Integer version;private Long createdBy;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
