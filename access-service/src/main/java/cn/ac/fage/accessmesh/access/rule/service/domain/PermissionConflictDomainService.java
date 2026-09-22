package cn.ac.fage.accessmesh.access.rule.service.domain;

import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;

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
     * 双删命中时记录 CONFLICT_DETECTED 操作日志（T-PERM-063，对齐 PERM_MUTEX 先例；
     * 每「租户×用户×规则」每 JVM 1 小时至多一条，去重限流）。
     * </p>
     *
     * @param tenantId        租户ID
     * @param userId          用户ID（快照构建方已知，日志归因用）
     * @param effectiveRoleIds 有效角色ID集合
     * @return 过滤后的有效角色ID集合（移除互斥角色）
     */
    Set<Long> filterRoleMutex(Long tenantId, Long userId, Set<Long> effectiveRoleIds);

    /**
     * 解析参与运行时判定的有效角色集（T-PERM-075 共同判定语义唯一入口）。
     * <p>
     * = {@code SubjectDomainService.resolveEffectiveRoles}（有效期窗口 + 启用态 + 组展开）
     * 叠加 {@link #filterRoleMutex} 互斥双删。check / batch-check / 管理门禁 validate / scope /
     * 接口快照 / 菜单与权限串视图全部经本方法消费——同一主体/时刻的互斥语义跨入口一致。
     * </p>
     * <p>
     * EFFECTIVE_ROLES 缓存语义维持「过滤前集合」：互斥过滤在判定时叠加（规则经
     * ROLE_MUTEX_RULE 缓存读取），规则变更沿既有 10s TTL 收敛；写守卫不得使用本方法
     * （写时必须看原始持有候选，见 {@code SubjectDomainService.batchResolveRawHoldings}）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 互斥过滤后的有效角色ID集合（双删命中时记录 CONFLICT_DETECTED 审计）
     */
    Set<Long> resolveJudgementRoleIds(Long tenantId, Long userId);

    /**
     * 授予前互斥冲突检测（T-PERM-063 写路径校验；T-PERM-075 窗口语义重定义）
     * <p>
     * 规则走 DB 直查（不经 ROLE_MUTEX_RULE 缓存——写路径要求新建规则即刻生效，
     * 10s TTL 陈旧窗口不可接受；快照链路缓存读取维持不变）。
     * 调用方负责组装「授予后窗口集」（未过期原始持有窗口 ∪ 本批新增窗口）；
     * 冲突判定按**区间交**：两个互斥角色各自任一持有窗口重叠（闭区间，null=无限端，
     * 首尾相接当天算重叠）才命中——真正不相交的未来窗口放行（U002-1 拍板口径），
     * 同批内两个互斥角色由窗口交语义天然覆盖。
     * </p>
     *
     * @param tenantId              租户ID
     * @param holdingsByUser        每用户授予后持有窗口集（仅含有新增关系的用户）
     * @return 命中的冲突列表（空=无冲突）
     */
    List<RoleMutexAssignConflict> findAssignMutexConflicts(
        Long tenantId, Map<Long, Set<SubjectDomainService.RawHolding>> holdingsByUser);

    /**
     * 存量双持查询（T-PERM-063 规则写路径守卫；T-PERM-075 口径扩展）
     * <p>
     * 候选超集经 {@link SubjectDomainService#findUserIdsByEffectiveRoles} 按角色反查用户
     * （ROLE 直授 + GROUP_ROLE 直绑 + 祖先组展开三路——组角色间接持有同入候选），
     * 再经 {@link SubjectDomainService#batchResolveRawHoldings} 收敛到原始持有窗口做
     * 区间交判定——未过期（含未来窗口）、含禁用持有、含禁用组子树，且两个互斥角色的
     * 持有窗口真正重叠才计双持：与全部用户-角色写守卫同口径（U002 写时堵死），
     * 避免「绑定时拒、立规时放」的同冲突双通道不一致。
     * </p>
     *
     * @param tenantId     租户ID
     * @param firstRoleId  互斥角色一
     * @param secondRoleId 互斥角色二
     * @return 持有窗口重叠的用户ID列表（空=无存量持有）
     */
    List<Long> findUsersHoldingBothRoles(Long tenantId, Long firstRoleId, Long secondRoleId);

    /**
     * 角色互斥授予冲突条目（T-PERM-063）。
     *
     * @param userId     冲突用户
     * @param ruleId     命中规则
     * @param firstRoleId  互斥角色一
     * @param secondRoleId 互斥角色二
     */
    record RoleMutexAssignConflict(Long userId, Long ruleId, Long firstRoleId, Long secondRoleId) {}

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
