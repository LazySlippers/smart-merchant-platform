package com.smartmerchant.saas.iam.domain;
import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("user_account") public class UserAccount { private Long id; private String username; private String passwordHash; private String displayName; private String mobile; private String status; private Integer tokenVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
