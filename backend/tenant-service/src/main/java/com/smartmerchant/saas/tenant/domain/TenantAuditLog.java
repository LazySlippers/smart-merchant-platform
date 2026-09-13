package com.smartmerchant.saas.tenant.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("tenant_audit_log")
public class TenantAuditLog { private Long id; private Long operatorId; private String action; private String resourceType; private String resourceId; private String detail; private String traceId; private LocalDateTime createdAt; }
