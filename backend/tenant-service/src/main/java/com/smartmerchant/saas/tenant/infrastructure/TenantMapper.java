package com.smartmerchant.saas.tenant.infrastructure;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmerchant.saas.tenant.domain.Tenant;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface TenantMapper extends BaseMapper<Tenant> { }
