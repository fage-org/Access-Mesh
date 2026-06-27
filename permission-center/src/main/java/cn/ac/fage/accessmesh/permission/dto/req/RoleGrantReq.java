package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 角色权限授予请求体
 * <p>
 * 用于批量配置角色的权限，支持添加、更新和移除操作。
 * </p>
 *
 * @param domainCode     业务域编码，可选
 * @param roleTypeCode   角色类型编码，必填
 * @param roleExternalId 角色外部标识，必填
 * @param add            权限添加条目列表，可选
 * @param update         权限更新条目列表，可选
 * @param remove         权限移除ID列表，可选
 */
public record RoleGrantReq(
    String domainCode,
    @NotBlank(message = "角色类型不能为空")
    String roleTypeCode,
    @NotBlank(message = "角色外部标识不能为空")
    String roleExternalId,
    List<GrantAddItem> add,
    List<GrantUpdateItem> update,
    List<Long> remove
) {
    /**
     * 权限添加条目
     * <p>
     * 表示要添加的权限配置参数。
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
    public record GrantAddItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        String codeType,
        @NotBlank String operationCode,
        ScopeMode scopeMode,
        Boolean canGrant,
        String conditionCode
    ) {}

    /**
     * 权限更新条目
     * <p>
     * 表示要更新的权限配置参数。
     * </p>
     *
     * @param id           权限配置ID，必填
     * @param canGrant     是否可授予他人，可选
     * @param conditionCode 条件编码，可选
     */
    public record GrantUpdateItem(
        Long id,
        Boolean canGrant,
        String conditionCode
    ) {}
}
