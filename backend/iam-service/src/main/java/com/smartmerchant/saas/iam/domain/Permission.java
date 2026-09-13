package com.smartmerchant.saas.iam.domain;
import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data;
@Data @TableName("permission") public class Permission { private Long id; private String permissionCode; private String permissionName; private String audience; }
