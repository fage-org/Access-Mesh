package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 批量权限校验响应体
 * <p>
 * 返回批量权限校验的结果列表，每个结果对应一次权限校验。
 * 用于批量权限检查接口的响应。
 * </p>
 *
 * @param items 权限校验结果列表
 */
public record BatchAuthCheckResp(
    List<AuthCheckItemResult> items
) {
    /**
     * 单项权限校验结果
     * <p>
     * 包含一次权限校验的完整信息，包括资源类型、编码、操作和校验结果。
     * </p>
     *
     * @param resourceTypeCode  资源类型编码
     * @param resourceCode      资源编码
     * @param operationCode     操作编码
     * @param allowed           是否允许访问
     * @param reason            拒绝原因，允许时为null
     * @param matchedRoleIds    匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     */
    public record AuthCheckItemResult(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed,
        String reason,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds
    ) {}
}