package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 角色权限条目映射器
 * <p>
 * 统一的映射工具：将RoleResourcePermission实体转换为RolePermEntry记录。
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
            p.getGrantedBits(),
            null,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn(),
            p.getScopeAll()
        );
    }

    /**
     * 将权限实体转换为权限条目（带操作码）
     *
     * @param p             角色资源权限实体
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
            p.getGrantedBits(),
            operationCode,
            null,
            p.getGrantSource(),
            p.getCanGrant(),
            p.getConditionId(),
            p.getConditionId() != null,
            p.getDependOn(),
            p.getScopeAll()
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

    /**
     * 批量填充操作信息
     *
     * @param entries 权限条目列表
     * @param opMap   OperationPermission 映射（id → op）
     * @return 填充后的权限条目列表
     */
    public List<RolePermEntry> fillOperationInfo(
        List<RolePermEntry> entries,
        Map<Long, OperationPermission> opMap) {
        return entries.stream().map(e -> {
            OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, e.resourceType(), e.grantedBits());
            return new RolePermEntry(
                e.permissionId(), e.roleId(), e.resourceEntityId(),
                e.resourceCode(), e.resourceType(),
                e.grantedBits(),
                op != null ? op.getCode() : null,
                op != null ? op.getEffectiveBits() : null,
                e.grantSource(), e.canGrant(), e.conditionId(),
                e.hasCondition(), e.dependOn(), e.scopeAll()
            );
        }).toList();
    }

    /**
     * 填充单个条目的操作信息
     *
     * @param entry 权限条目
     * @param op    OperationPermission
     * @return 填充后的权限条目
     */
    public RolePermEntry fillOperationInfo(RolePermEntry entry, OperationPermission op) {
        if (op == null) {
            return entry;
        }
        return new RolePermEntry(
            entry.permissionId(), entry.roleId(), entry.resourceEntityId(),
            entry.resourceCode(), entry.resourceType(),
            entry.grantedBits(),
            op.getCode(),
            op.getEffectiveBits(),
            entry.grantSource(), entry.canGrant(), entry.conditionId(),
            entry.hasCondition(), entry.dependOn(), entry.scopeAll()
        );
    }
}
