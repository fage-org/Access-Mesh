package cn.ac.fage.accessmesh.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.time.LocalDateTime;

/**
 * 最近变更响应体
 * <p>
 * 返回权限最近变更的详细信息，包括变更类型、影响级别、操作者等。
 * 用于权限变更历史查询的响应。
 * </p>
 *
 * @param changeLogId    变更日志ID
 * @param eventType      事件类型
 * @param changeType     变更类型
 * @param impactLevel    影响级别
 * @param message        变更消息
 * @param permission     权限键信息
 * @param sourceRole     来源角色信息
 * @param operatorId     操作者ID
 * @param operatorName   操作者名称
 * @param changeReason   变更原因
 * @param createdAt      创建时间
 */
public record RecentChangeResp(
    Long changeLogId,
    String eventType,
    String changeType,
    String impactLevel,
    String message,
    PermissionKey permission,
    SourceRole sourceRole,
    Long operatorId,
    String operatorName,
    String changeReason,
    LocalDateTime createdAt
) {
    /**
     * 权限键信息
     * <p>
     * 表示变更涉及的权限唯一标识信息。
     * </p>
     *
     * @param domainCode        业务域编码
     * @param resourceTypeCode  资源类型编码
     * @param resourceCode      资源编码
     * @param codeType          编码类型
     * @param operationCode     操作编码
     * @param scopeMode         范围模式
     */
    public record PermissionKey(
        String domainCode,
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        ScopeMode scopeMode
    ) {}

    /**
     * 来源角色信息
     * <p>
     * 表示变更涉及的来源角色信息。
     * </p>
     *
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部标识
     * @param roleName       角色名称
     */
    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName
    ) {}
}
