package org.dromara.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.permission.domain.PcRoleResourcePermission;

import java.util.Collection;
import java.util.List;

/**
 * 角色-资源-操作中间表 role_resource_permission 数据层
 *
 * @author RuoYi-Cloud-Plus
 */
@Mapper
public interface PcRoleResourcePermissionMapper extends BaseMapperPlus<PcRoleResourcePermission, PcRoleResourcePermission> {

    List<PcRoleResourcePermission> selectByTenantAndRoleIds(@Param("tenantId") Long tenantId,
                                                            @Param("roleIds") Collection<Long> roleIds);

    List<PcRoleResourcePermission> selectByTenantAndRoleIdsAndTarget(@Param("tenantId") Long tenantId,
                                                                     @Param("roleIds") Collection<Long> roleIds,
                                                                     @Param("resourceEntityId") Long resourceEntityId,
                                                                     @Param("operationPermissionId") Long operationPermissionId);
}
