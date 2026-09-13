package com.smartmerchant.saas.iam.infrastructure;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.smartmerchant.saas.iam.domain.UserAccount; import org.apache.ibatis.annotations.Mapper;
@Mapper public interface UserAccountMapper extends BaseMapper<UserAccount>{
 @org.apache.ibatis.annotations.Select("SELECT u.* FROM user_account u JOIN tenant_user tu ON tu.user_id=u.id WHERE tu.tenant_id=#{tenantId} ORDER BY u.created_at DESC") java.util.List<UserAccount> findByTenant(@org.apache.ibatis.annotations.Param("tenantId") long tenantId);
}
