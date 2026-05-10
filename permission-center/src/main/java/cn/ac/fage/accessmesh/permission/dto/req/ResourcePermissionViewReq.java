package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源权限视图查询请求体
 * <p>
 * 用于查询资源的权限配置视图，显示哪些角色拥有此资源的权限。
 * </p>
 *
 * @param domainCode        业务域编码，可选
 * @param resourceTypeCode  资源类型编码，必填
 * @param resourceCode      资源编码，必填
 * @param codeType          编码类型，可选
 */
public record ResourcePermissionViewReq(
    String domainCode,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,
    String codeType
) {}