package com.smartmerchant.saas.iam.domain;
import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("role") public class Role { private Long id; private Long tenantId; private String roleCode; private String roleName; private Boolean platformRole; private String status; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
