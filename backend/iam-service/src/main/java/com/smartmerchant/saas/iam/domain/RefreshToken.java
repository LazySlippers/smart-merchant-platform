package com.smartmerchant.saas.iam.domain;
import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("refresh_token") public class RefreshToken { private Long id; private Long tenantId; private Long userId; private String tokenHash; private LocalDateTime expiresAt; private LocalDateTime revokedAt; private LocalDateTime createdAt; }
