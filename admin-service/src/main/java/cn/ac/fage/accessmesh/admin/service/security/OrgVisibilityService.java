package cn.ac.fage.accessmesh.admin.service.security;

import java.util.Collection;
import java.util.Set;

/**
 * 组织可见性服务
 * <p>
 * 基于操作者的 ADMIN_ORG:VIEW 权限，过滤出其可见的组织范围。
 * 用于 memberCandidates / pageUsers / validateUsersInDefaultTreeScope 等场景的可见性裁剪，
 * 消除默认树后代全量暴露的越权风险（P1-D / EXT-3 / EXT-4）。
 * <p>
 * 内部通过 {@code PermissionFeignClient.batchCheckAuth} 批量校验，
 * 结果按 {@code (tenantId, operatorId)} 缓存 60 秒，减少 Feign 调用频次。
 */
public interface OrgVisibilityService {

    /**
     * 过滤出操作者通过 ADMIN_ORG:VIEW 可见的组织子集。
     * <p>
     * 对候选 orgIds 分批（每批 500）调用 {@code batchCheckAuth}，
     * 返回 {@code allowed=true} 的 orgId 子集。
     *
     * @param tenantId   租户 ID
     * @param operatorId 操作者用户 ID
     * @param orgIds     候选组织 ID 集合
     * @return 可见的子集
     */
    Set<Long> filterVisibleOrgIds(Long tenantId, Long operatorId, Collection<Long> orgIds);

    /**
     * 取操作者在默认树范围内可见的所有组织 ID。
     * <p>
     * 等价于 {@code getDescendantIdsIncludingSelf(defaultRootOrgId)} 再经
     * {@link #filterVisibleOrgIds} 裁剪，结果按 (tenantId, operatorId) 缓存。
     *
     * @param tenantId   租户 ID
     * @param operatorId 操作者用户 ID
     * @return 默认树后代 ∩ 操作者 ADMIN_ORG:VIEW 通过的子集
     */
    Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId);
}
