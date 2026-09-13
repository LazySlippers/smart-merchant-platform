package com.smartmerchant.saas.merchant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("store")
public class Store {
    private Long id; private Long tenantId; private String storeCode; private String storeName;
    private String address; private String businessHours; private String status; private Integer version;
    private Long createdBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
