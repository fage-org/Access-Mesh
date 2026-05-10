package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色权限条目映射器
 * <p>
 * 统一的映射工具：将RoleResourcePermission实体转换为RolePermEntry记录。
 * 消除PermissionServiceImpl中重复的13参数构造函数调用。
 * 提供单个转换、带操作码转换、批量转换等方法。
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
            p.getOperationPermissionId(),
            null,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn()
        );
    }

    /**
     * 将权限实体转换为权限条目（带操作码）
     *
     * @param p            角色资源权限实体
     * @param operationCode 操作码
     * @return 权限条目记录（包含操作码）
     */
    public RolePermEntry toEntryWithOpCode(RoleResourcePermission p, String operationCode) {
        return new RolePermEntry(
            p.getId(),
            p.getAbstractRoleId(),
            p.getResourceEntityId(),
            null,
            p.getResourceType(),
            p.getOperationPermissionId(),
            operationCode,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn()
        );
    }

    /**
     * 批量将权限实体列表转换为权限条目列表
     *
     * @param perms 权限实体列表
     * @return 权限条目列表
     */
    public List<RolePermEntry> toEntryList(List<RoleResourcePermission> perms) {
        return perms.stream().map(this::toEntry).toList();
    }
}