package cn.ac.fage.accessmesh.access.grant.service.domain;

import java.util.Collection;
import java.util.Set;

/**
 * 自动授权物化领域服务（T-PERM-072）。
 * <p>
 * 按角色 MANUAL 实例主授权种子完整重算 desired AUTO_DEP 并与 actual diff 同事务落库；
 * 复用 {@link AutoGrantDerivation} 共享推导与精确去重核心。不建 support 表、不存全路径、
 * 不做二遍路径补录；多来源按完整事实键去重，无来源的自动行同事务删除。
 * </p>
 * <p>
 * 硬契约：调用方持有租户 RESOURCE_ENTITY 树写锁（读取推导输入之前取得）并在本事务内调用；
 * 缓存失效（ROLE_PERM_SNAPSHOT）由调用方按返回的变化角色集合经 {@code PermissionChangeContext.markRoles}
 * 登记 afterCommit。INLINE 条件回收按统一时序——自身删除与重算完成后，按实际引用归零回收。
 * </p>
 */
public interface AutoGrantMaterializationDomainService {

    /**
     * 重算角色集的 AUTO_DEP 授权（完整 desired 重算 + actual diff 落库 + INLINE 归零回收）。
     *
     * @param tenantId                租户ID
     * @param roleIds                 待重算角色集合（内部按 ID 升序处理以固定写入顺序）
     * @param inlineRecycleCandidates 调用方同事务已删除/换绑的 MANUAL 行携带的 INLINE 条件候选
     *                               （与物化删除的 AUTO_DEP 行条件一并按引用归零回收）
     * @return AUTO_DEP 行发生增删的角色集合（供调用方 markRoles；无变化返回空集合）
     */
    Set<Long> recompute(Long tenantId, Collection<Long> roleIds, Set<Long> inlineRecycleCandidates);

    /**
     * 触发面入口便捷形态：按受影响实体定位持有授权事实的角色并重算（编译图收缩/资源删除等场景）。
     *
     * @param tenantId         租户ID
     * @param resourceEntityIds 受影响资源实体集合（编译生命周期方法返回的端点并集等）
     * @return AUTO_DEP 行发生增删的角色集合（供调用方 markRoles）
     */
    Set<Long> recomputeByResourceEntities(Long tenantId, Collection<Long> resourceEntityIds);

    /**
     * 角色删除级联回收（设计 §7 触发面；2026-09-21 拍板 A）：软删角色集全部有效授权行
     * （MANUAL + AUTO_DEP + AUTHORITY_ROOT）并按引用归零回收 INLINE 条件；被删角色不做重算。
     *
     * @param tenantId 租户ID
     * @param roleIds  被删角色集合（含级联子孙）
     */
    void recycleRoleGrants(Long tenantId, Set<Long> roleIds);
}
