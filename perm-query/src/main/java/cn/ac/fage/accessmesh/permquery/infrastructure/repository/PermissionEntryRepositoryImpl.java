package cn.ac.fage.accessmesh.permquery.infrastructure.repository;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper.BitMaskEntry;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;
import cn.ac.fage.accessmesh.permquery.domain.repository.PermissionEntryRepository;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限条目仓储实现
 * <p>
 * 复用 RoleResourcePermissionMapper 的 SQL 位掩码查询逻辑。
 * 将实体转换为 GrantedPermission 值对象。
 * </p>
 */
@Repository
public class PermissionEntryRepositoryImpl implements PermissionEntryRepository {

    private final RoleResourcePermissionMapper rolePermMapper;

    public PermissionEntryRepositoryImpl(RoleResourcePermissionMapper rolePermMapper) {
        this.rolePermMapper = rolePermMapper;
    }

    @Override
    public List<GrantedPermission> queryScopeAllPermissions(
        Long tenantId, Set<Long> roleIds, Map<Integer, Long> bitMasks) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty() ||
            bitMasks == null || bitMasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建 BitMaskEntry 列表
        List<BitMaskEntry> entries = bitMasks.entrySet().stream()
            .map(e -> new BitMaskEntry(e.getKey(), e.getValue()))
            .toList();

        // 单次SQL查询
        List<RoleResourcePermission> perms = rolePermMapper.selectScopeAllPermsByBitsBatch(
            tenantId, roleIds, entries);

        return perms.stream()
            .map(this::toGrantedPermission)
            .toList();
    }

    @Override
    public List<GrantedPermission> queryInstancePermissions(
        Long tenantId, Set<Long> roleIds, Set<Long> resourceEntityIds, Map<Integer, Long> bitMasks) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty() ||
            resourceEntityIds == null || resourceEntityIds.isEmpty() ||
            bitMasks == null || bitMasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建 BitMaskEntry 列表
        List<BitMaskEntry> entries = bitMasks.entrySet().stream()
            .map(e -> new BitMaskEntry(e.getKey(), e.getValue()))
            .toList();

        // 单次SQL查询
        List<RoleResourcePermission> perms = rolePermMapper.selectInstancePermsByBitsBatch(
            tenantId, roleIds, resourceEntityIds, entries);

        return perms.stream()
            .map(this::toGrantedPermission)
            .toList();
    }

    @Override
    public List<GrantedPermission> queryPermissionsByRole(Long tenantId, Long roleId) {
        if (tenantId == null || roleId == null) {
            return Collections.emptyList();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectByRoleId(tenantId, roleId);
        return perms.stream()
            .map(this::toGrantedPermission)
            .toList();
    }

    @Override
    public List<GrantedPermission> queryPermissionsByRoles(Long tenantId, Set<Long> roleIds) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleIds(tenantId, roleIds);
        return perms.stream()
            .map(this::toGrantedPermission)
            .toList();
    }

    @Override
    public List<GrantedPermission> queryPermissionsByResourceEntity(Long tenantId, Long resourceEntityId) {
        if (tenantId == null || resourceEntityId == null) {
            return Collections.emptyList();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByResourceEntityId(
            tenantId, resourceEntityId);
        return perms.stream()
            .map(this::toGrantedPermission)
            .toList();
    }

    /**
     * 将实体转换为值对象
     */
    private GrantedPermission toGrantedPermission(RoleResourcePermission p) {
        return new GrantedPermission(
            p.getId(),
            p.getAbstractRoleId(),
            p.getResourceType(),
            p.getResourceEntityId(),
            p.getGrantedBits() != null ? p.getGrantedBits() : 0L,
            null, // operationCode 从 OperationPermission 反查填充
            0L,   // effectiveBits 从 OperationPermission 反查填充
            p.getGrantSource(),
            p.getCanGrant() != null ? p.getCanGrant() : false,
            p.getConditionId(),
            p.getDependOn(),
            null, // conditionMet 待条件评估后填充
            false // hasConflict 待冲突检测后填充
        );
    }
}