package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermEntry;
import lombok.Builder;

import java.util.*;
import java.util.stream.Stream;

/**
 * 统一权限查询结果类
 * <p>
 * 包含权限引擎收集的所有信息，调用者通过便捷方法获取所需内容。
 * 空字段表示"未请求"，根据查询选项决定。
 * </p>
 */
public class PermResult {

    /**
     * 是否允许
     */
    private final boolean allowed;

    /**
     * 原因说明（拒绝时填写）
     */
    private final String reason;

    /**
     * 全范围是否匹配
     */
    private final boolean scopeAllMatched;

    /**
     * 全范围权限条目列表
     */
    private final List<RolePermEntry> scopeAllEntries;

    /**
     * 实例级权限条目列表
     */
    private final List<RolePermEntry> instanceEntries;

    /**
     * 有效操作权限投影列表
     * <p>
     * 基于已命中的原始授权条目和 {@code OperationPermission.effectiveBits}
     * 展开得到。它表示调用方最终可用的操作集合，不替代原始授权条目。
     * </p>
     */
    private final List<EffectiveOperationEntry> effectiveOperationEntries;

    /**
     * 资源实体映射
     */
    private final Map<Long, ResourceEntity> resourceMap;

    /**
     * 操作权限映射
     */
    private final Map<Long, OperationPermission> operationMap;

    /**
     * 角色映射
     */
    private final Map<Long, AbstractRole> roleMap;

    @lombok.Builder(builderClassName = "Builder", builderMethodName = "_builder")
    private PermResult(
        boolean allowed,
        String reason,
        boolean scopeAllMatched,
        List<RolePermEntry> scopeAllEntries,
        List<RolePermEntry> instanceEntries,
        List<EffectiveOperationEntry> effectiveOperationEntries,
        Map<Long, ResourceEntity> resourceMap,
        Map<Long, OperationPermission> operationMap,
        Map<Long, AbstractRole> roleMap
    ) {
        this.allowed = allowed;
        this.reason = reason;
        this.scopeAllMatched = scopeAllMatched;
        this.scopeAllEntries = List.copyOf(scopeAllEntries != null ? scopeAllEntries : List.of());
        this.instanceEntries = List.copyOf(instanceEntries != null ? instanceEntries : List.of());
        this.effectiveOperationEntries = List.copyOf(
            effectiveOperationEntries != null ? effectiveOperationEntries : List.of());
        this.resourceMap = resourceMap == null ? null : Map.copyOf(resourceMap);
        this.operationMap = operationMap == null ? null : Map.copyOf(operationMap);
        this.roleMap = roleMap == null ? null : Map.copyOf(roleMap);
    }

    // ===== Getter 方法（保留 xxx() 形式） =====

    public boolean allowed() { return allowed; }
    public String reason() { return reason; }
    public boolean scopeAllMatched() { return scopeAllMatched; }
    public List<RolePermEntry> scopeAllEntries() { return scopeAllEntries; }
    public List<RolePermEntry> instanceEntries() { return instanceEntries; }
    public List<EffectiveOperationEntry> effectiveOperationEntries() { return effectiveOperationEntries; }
    public Map<Long, ResourceEntity> resourceMap() { return resourceMap; }
    public Map<Long, OperationPermission> operationMap() { return operationMap; }
    public Map<Long, AbstractRole> roleMap() { return roleMap; }

    /**
     * 创建Builder实例（必填参数）
     *
     * @param allowed 是否允许
     * @param reason  原因说明
     * @return Builder实例
     */
    public static Builder builder(boolean allowed, String reason) {
        return _builder().allowed(allowed).reason(reason);
    }

    /**
     * 创建拒绝结果
     *
     * @param reason 拒绝原因
     * @return 拒绝结果实例
     */
    public static PermResult deny(String reason) {
        return builder(false, reason).build();
    }

    // ===== 便捷方法 =====

    /**
     * 获取所有权限条目（合并全范围和实例级）
     *
     * @return 所有权限条目列表
     */
    public List<RolePermEntry> allEntries() {
        return Stream.concat(scopeAllEntries.stream(), instanceEntries.stream()).toList();
    }

    /**
     * 获取匹配的角色ID集合
     *
     * @return 匹配的角色ID集合
     */
    public Set<Long> matchedRoleIds() {
        return allEntries().stream()
            .map(RolePermEntry::roleId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取匹配的权限ID集合
     *
     * @return 匹配的权限ID集合
     */
    public Set<Long> matchedPermissionIds() {
        return allEntries().stream()
            .map(RolePermEntry::permissionId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 有效操作权限投影。
     * <p>
     * 一条显式授权可能展开为多条有效操作，例如 UPDATE 通过 inheritMask
     * 覆盖 VIEW 时，会产生 UPDATE 和 VIEW 两条投影。permissionId/roleId
     * 等来源字段仍指向原始授权条目，便于调用方追溯来源。
     * </p>
     *
     * @param permissionId         原始权限ID
     * @param roleId               来源角色ID
     * @param resourceEntityId     资源实体ID，scopeAll 权限可为空
     * @param resourceType         资源类型值
     * @param grantedBits          原始授予操作位
     * @param grantedOperationCode 原始授予操作码
     * @param effectiveBits        原始授予操作的有效位掩码
     * @param operationCode        最终可用操作码
     * @param operationBinaryBit   最终可用操作位
     * @param grantSource          授权来源
     * @param scopeAll             是否全范围权限
     */
    public record EffectiveOperationEntry(
        Long permissionId,
        Long roleId,
        Long resourceEntityId,
        Integer resourceType,
        Long grantedBits,
        String grantedOperationCode,
        Long effectiveBits,
        String operationCode,
        Long operationBinaryBit,
        String grantSource,
        Boolean scopeAll
    ) {}
}
