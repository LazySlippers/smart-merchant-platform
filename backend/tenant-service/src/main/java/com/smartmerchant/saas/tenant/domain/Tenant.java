package com.smartmerchant.saas.tenant.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("tenant")
public class Tenant { private Long id; private String tenantCode; private String tenantName; private String status; private String ownerName; private String ownerMobile; private Long sourceApplicationId; private Integer version; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
