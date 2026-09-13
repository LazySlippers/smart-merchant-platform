package com.smartmerchant.saas.tenant.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("plan")
public class Plan { private Long id; private String planCode; private String planName; private String status; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
