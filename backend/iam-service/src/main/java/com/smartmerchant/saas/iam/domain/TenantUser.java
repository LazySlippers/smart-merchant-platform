package com.smartmerchant.saas.iam.domain;
import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("tenant_user") public class TenantUser { private Long id; private Long tenantId; private Long userId; private String memberType; private String dataScope; private String status; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
