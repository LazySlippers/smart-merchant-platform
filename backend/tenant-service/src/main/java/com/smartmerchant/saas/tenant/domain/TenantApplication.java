package com.smartmerchant.saas.tenant.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
@Data @TableName("tenant_application")
public class TenantApplication { private Long id; private String merchantName; private String contactName; private String contactMobile; @JsonIgnore private String passwordHash; private String requestedPlanCode; private String status; private String reviewReason; private Long reviewedBy; private LocalDateTime reviewedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
