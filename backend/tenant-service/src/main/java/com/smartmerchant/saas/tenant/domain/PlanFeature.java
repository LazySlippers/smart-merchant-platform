package com.smartmerchant.saas.tenant.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("plan_feature")
public class PlanFeature { private Long id; private Long planId; private String featureCode; private Boolean enabled; private Long quotaValue; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
