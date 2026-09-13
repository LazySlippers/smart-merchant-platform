package com.smartmerchant.saas.iam.infrastructure;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.smartmerchant.saas.iam.domain.TenantUser; import org.apache.ibatis.annotations.Mapper;
@Mapper public interface TenantUserMapper extends BaseMapper<TenantUser>{}
