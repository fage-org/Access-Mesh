package org.dromara.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.permission.domain.PcUserRole;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户-角色关联表 user_role 数据层
 *
 * @author RuoYi-Cloud-Plus
 */
@Mapper
public interface PcUserRoleMapper extends BaseMapperPlus<PcUserRole, PcUserRole> {

    List<PcUserRole> selectEffectiveByTenantAndUser(@Param("tenantId") Long tenantId,
                                                    @Param("abstractUserId") Long abstractUserId,
                                                    @Param("now") LocalDateTime now);
}
