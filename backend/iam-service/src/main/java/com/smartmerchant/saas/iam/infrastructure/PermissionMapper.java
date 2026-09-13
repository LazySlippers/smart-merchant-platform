package com.smartmerchant.saas.iam.infrastructure;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.smartmerchant.saas.iam.domain.Permission; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface PermissionMapper extends BaseMapper<Permission>{
 @Select("SELECT p.permission_code FROM permission p JOIN role_permission rp ON rp.permission_id=p.id JOIN user_role ur ON ur.role_id=rp.role_id AND ur.tenant_id=rp.tenant_id WHERE ur.tenant_id=#{tenantId} AND ur.user_id=#{userId}") List<String> findCodes(@Param("tenantId") long tenantId,@Param("userId") long userId);
}
