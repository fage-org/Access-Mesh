package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.vo.MutexFilterResult;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.List;
import java.util.Set;

/**
 * 权限冲突领域服务接口
 * <p>
 * 提供权限冲突检测和处理功能。
 * 支持两种冲突类型：
 * - 角色互斥（ROLE_MUTEX）：两个角色不能同时拥有
 * - 权限互斥（PERM_MUTEX）：两个操作权限不能同时授予
 * 冲突检测用于权限分配时预警和过滤潜在的权限冲突。
 * </p>
 */
public interface PermissionConflictDomainService {

    /**
     * 过滤角色互斥冲突
     * <p>
     * 根据角色互斥规则过滤有效角色集合。
     * 如果用户同时拥有互斥的两个角色，则同时移除这两个角色。
     * 角色互斥规则从数据库或缓存加载。
     * </p>
     *
     * @param tenantId        租户ID
     * @param effectiveRoleIds 有效角色ID集合
     * @return 过滤后的有效角色ID集合（移除互斥角色）
     */
    Set<Long> filterRoleMutex(Long tenantId, Set<Long> effectiveRoleIds);

    /**
     * 过滤权限互斥冲突
     * <p>
     * 根据权限互斥规则过滤权限条目列表。
     * 如果用户同时拥有互斥的两个操作权限，则同时移除这两个权限。
     * 检测到冲突时异步发出通知。
     * </p>
     *
     * @param tenantId     租户ID
     * @param passedEntries 通过初步检查的权限条目列表
     * @return 过滤后的权限条目列表（移除互斥权限）
     */
    List<RolePermEntry> filterPermMutex(Long tenantId, List<RolePermEntry> passedEntries);

    /**
     * 权限互斥过滤（带丢弃明细，T-PERM-033 explain DTO 扩展）。
     * <p>
     * 过滤语义与 {@link #filterPermMutex} 完全一致，但保留被互斥规则丢弃的条目
     * 及其命中规则（规则ID + 两侧操作码），供权限排查视图解释「本可命中但被互斥
     * 规则移除」。只读排查路径专用，不触发冲突通知。
     * </p>
     *
     * @param tenantId     租户ID
     * @param passedEntries 条件评估后的权限条目列表
     * @return 过滤结果（通过条目 + 被丢弃条目及命中规则）
     */
    MutexFilterResult filterPermMutexWithDrops(Long tenantId, List<RolePermEntry> passedEntries);
}