package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 批量权限校验响应
 * <p>
 * 包含批量校验中每个项目的校验结果。
 * </p>
 * <p>
 * T-API-003（2026-09-09 定案，推翻 T-API-002 的 check 族裁剪）：单项结果的结果记录
 * （matchedRoleIds / matchedPermissionIds，role / role_resource_permission 内部行 id）
 * 全量回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场。
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
        String reason,
        /**
         * 匹配的角色ID列表（拒绝时为空列表）
         */
        List<Long> matchedRoleIds,
        /**
         * 匹配的权限ID列表（拒绝时为空列表）
         */
        List<Long> matchedPermissionIds
    ) {}
}
