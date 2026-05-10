package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 角色权限授予请求
 * <p>
 * 用于向角色批量授予权限的共享请求对象。
 * 支持添加、更新和删除权限项。
 * </p>
 */
public record RoleGrantReq(
    /**
     * 业务域码（可选）
     */
    String domainCode,
    /**
     * 角色类型码
     */
    @NotBlank String roleTypeCode,
    /**
     * 角色外部ID
     */
    @NotBlank String roleExternalId,
    /**
     * 待添加的权限项列表
     */
    List<GrantAddItem> add,
    /**
     * 待更新的权限项列表
     */
    List<GrantUpdateItem> update,
    /**
     * 待删除的权限ID列表
     */
    List<Long> remove
) {
    /**
     * 权限添加项
     * <p>
     * 定义单个权限的添加请求。
     * </p>
     */
    public record GrantAddItem(
        /**
         * 资源类型码
         */
        @NotBlank String resourceTypeCode,
        /**
         * 资源码（可选）
         */
        String resourceCode,
        /**
         * 编码类型（可选）
         */
        String codeType,
        /**
         * 操作码
         */
        @NotBlank String operationCode,
        /**
         * 是否授予全部范围
         */
        Boolean scopeAll,
        /**
         * 是否可授权给他人
         */
        Boolean canGrant,
        /**
         * 条件编码
         */
        String conditionCode
    ) {}

    /**
     * 权限更新项
     * <p>
     * 定义单个权限的更新请求。
     * </p>
     */
    public record GrantUpdateItem(
        /**
         * 权限ID
         */
        Long id,
        /**
         * 是否可授权给他人
         */
        Boolean canGrant,
        /**
         * 条件编码
         */
        String conditionCode
    ) {}
}
