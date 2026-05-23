package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.springframework.stereotype.Component;

/**
 * 角色权限条目映射器
 * <p>
 * 统一的映射工具：将RoleResourcePermission实体转换为RolePermEntry记录。
 * 消除PermissionServiceImpl中重复的13参数构造函数调用。
 * </p>
 * <p>
 * grantedBits 替代 operationPermissionId，
 * operationCode/effectiveBits 从 OperationPermission 反查填充。
 * </p>
 */
@Component
public class RolePermEntryMapper {

    /**
     * 将权限实体转换为权限条目
     *
     * @param p 角色资源权限实体
     * @return 权限条目记录
     */
    public RolePermEntry toEntry(RoleResourcePermission p) {
        return new RolePermEntry(
            p.getId(),
            p.getAbstractRoleId(),
            p.getResourceEntityId(),
            null,
            p.getResourceType(),
            p.getGrantedBits(),
            null,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn()
        );
    }
}