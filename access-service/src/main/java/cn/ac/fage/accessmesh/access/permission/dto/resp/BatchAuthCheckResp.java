package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 批量权限校验响应体
 * <p>
 * 返回批量权限校验的结果列表，每个结果对应一次权限校验。
 * 用于批量权限检查接口的响应。
 * </p>
 * <p>
 * T-API-003（2026-09-09 定案，推翻 T-API-002 的 check 族裁剪）：单项结果的结果记录
 * （matchedRoleIds / matchedPermissionIds，role / role_resource_permission 内部行 id）
 * 全量回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场。
 * Query* 响应族的字段裁剪不在推翻范围（维持 T-API-002 终态）。
 * 线格式与 perm-common 副本保持同形（双副本形状由回归锁钉死）。
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
     * @param resourceTypeCode    资源类型编码
     * @param resourceCode        资源编码
     * @param operationCode       操作编码
     * @param allowed             是否允许访问
     * @param reason              拒绝原因，允许时为null
     * @param matchedRoleIds      匹配的角色ID列表（拒绝时为空列表）
     * @param matchedPermissionIds 匹配的权限ID列表（拒绝时为空列表）
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
