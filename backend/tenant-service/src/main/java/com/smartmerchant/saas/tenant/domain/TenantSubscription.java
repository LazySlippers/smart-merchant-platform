package com.smartmerchant.saas.tenant.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("tenant_subscription")
public class TenantSubscription { private Long id; private Long tenantId; private Long planId; private String status; private LocalDateTime startsAt; private LocalDateTime expiresAt; private Integer version; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
