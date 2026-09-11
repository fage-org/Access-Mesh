package cn.ac.fage.accessmesh.access.permission.service.domain;

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
     * 创建请求级批量互斥评估器（T-PERM-061 A+ 形态：计算与通知解耦 + 静态数据共享装载）。
     * <p>
     * 批量判定路径消费——per-request 实例经方法参数传递；规则请求级一次、操作索引按
     * distinct 类型惰性扩，计算按传入条目集合（不共享计算）。通知由调用方 ledger
     * 聚合后经 {@code notifyHits} 显式触发。见 {@link BatchPermMutexEvaluator}。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 请求级批量互斥评估器
     */
    BatchPermMutexEvaluator openBatchMutexEvaluator(Long tenantId);

}
