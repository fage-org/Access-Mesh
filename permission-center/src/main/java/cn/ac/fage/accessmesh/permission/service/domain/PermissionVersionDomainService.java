package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Set;

/**
 * 权限版本领域服务接口
 * <p>
 * T-PERM-003（2026-06-20）删除 permission_version 持久化层后，本接口仅保留
 * {@link #buildPermissionVersionKey} 作为权限令牌的占位构造器。
 * </p>
 * <p>
 * <b>占位说明（方案1）</b>：原实现基于 permission_version 表的 maxVersion 构造令牌，
 * 删除 version 表后改为基于 roleIds 的稳定指纹。此时令牌仅在角色集合变化时变化，
 * <b>不再反映权限内容变更</b>——权限失效改由 Redis pub/sub 主动广播（T-PERM-006）+ TTL 兜底承担。
 * T-PERM-001（Gateway 快照模式）落地后，令牌将改为 sha256(permissions)（v3.5 §5.1 ETag），
 * 届时本接口与 {@code InterfaceSnapshotResp.permissionVersion} 字段一并移除。
 * </p>
 */
public interface PermissionVersionDomainService {

    /**
     * 构建权限版本键（占位）
     * <p>
     * 占位实现：基于 userId + 有序 roleIds 的稳定指纹，不再读取 permission_version 表。
     * 格式为 {@code userId:roleIdsHash}。
     * </p>
     *
     * @param userId   用户ID
     * @param tenantId 租户ID（占位实现未使用，保留参数兼容调用方）
     * @param roleIds  角色ID集合
     * @return 版本键字符串
     */
    String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds);
}
