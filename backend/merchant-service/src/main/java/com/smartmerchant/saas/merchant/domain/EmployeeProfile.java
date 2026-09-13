package com.smartmerchant.saas.merchant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("employee_profile")
public class EmployeeProfile {
    private Long id; private Long tenantId; private Long userId; private String employeeNo;
    private String employeeName; private String mobile; private Long primaryStoreId; private String jobTitle;
    private String status; private Integer version; private Long createdBy;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
