package com.smartmerchant.saas.iam.domain;
import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("login_audit") public class LoginAudit { private Long id; private Long tenantId; private Long userId; private String username; private String result; private String failureReason; private String ipAddress; private String traceId; private LocalDateTime createdAt; }
