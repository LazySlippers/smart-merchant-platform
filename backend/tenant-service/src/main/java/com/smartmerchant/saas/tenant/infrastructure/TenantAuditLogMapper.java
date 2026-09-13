package com.smartmerchant.saas.tenant.infrastructure;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmerchant.saas.tenant.domain.TenantAuditLog;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface TenantAuditLogMapper extends BaseMapper<TenantAuditLog> { }
