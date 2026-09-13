package com.smartmerchant.saas.merchant.domain;
import com.baomidou.mybatisplus.annotation.TableName;import lombok.Data;import java.time.LocalDateTime;
@Data @TableName("category") public class Category {private Long id;private Long tenantId;private Long parentId;private String categoryCode;private String categoryName;private Integer sortOrder;private String status;private Integer version;private Long createdBy;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
