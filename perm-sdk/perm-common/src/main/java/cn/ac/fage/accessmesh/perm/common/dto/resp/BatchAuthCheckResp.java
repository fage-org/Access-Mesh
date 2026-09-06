package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 批量权限校验响应
 * <p>
 * 包含批量校验中每个项目的校验结果。
 * </p>
 * <p>
 * T-API-002（2026-09-06 定案，用户决策扩大裁剪面）：单项结果的内部数据库 id 字段族
 * （matchedRoleIds / matchedPermissionIds，role / role_resource_permission 内部行 id）
 * 裁剪，与 core-flows §15「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。
 * </p>
 */
public record BatchAuthCheckResp(
    /**
     * 批量校验结果项列表
     */
    List<AuthCheckItemResult> items
) {

    /**
     * 批量校验中的单项结果
     * <p>
     * 表示单个资源操作的权限校验结果。
     * </p>
     */
    public record AuthCheckItemResult(
        /**
         * 资源类型码
         */
        String resourceTypeCode,
        /**
         * 资源码
         */
        String resourceCode,
        /**
         * 操作码
         */
        String operationCode,
        /**
         * 是否允许通过
         */
        boolean allowed,
        /**
         * 拒绝原因
         */
        String reason
    ) {}
}
