package cn.ac.fage.accessmesh.access.permission.dto.req;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 角色权限添加子权限请求体
 * <p>
 * 用于为角色添加子权限配置，支持批量添加多个权限条目。
 * </p>
 *
 * @param parentPermissionId 父权限ID，必填
 * @param children           子权限条目列表，必填且不能为空
 */
public record RolePermissionAddChildReq(
    @NotNull Long parentPermissionId,
    @NotEmpty List<@Valid ChildItem> children
) {
    /**
     * 子权限条目
     * <p>
     * 表示单个权限配置的参数，包括资源类型、编码和操作。
     * </p>
     *
     * @param resourceTypeCode 资源类型编码，必填
     * @param resourceCode     资源编码，可选
     * @param codeType         编码类型，可选
     * @param operationCode    操作编码，必填
     * @param scopeMode        范围模式，INSTANCE/ALL
     * @param canGrant         是否可授予他人，可选
     * @param conditionCode    条件编码，可选
     */
    public record ChildItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        String codeType,
        @NotBlank String operationCode,
        ScopeMode scopeMode,
        Boolean canGrant,
        String conditionCode
    ) {}
}
