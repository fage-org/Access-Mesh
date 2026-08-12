package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 批量撤销权限请求体
 * <p>
 * 用于批量撤销角色的权限配置。
 * </p>
 *
 * @param domainCode     业务域编码，可选
 * @param roleTypeCode   角色类型编码，必填
 * @param roleExternalId 角色外部标识，必填
 * @param permissionIds  权限配置ID列表，可选
 */
public record BatchRevokeReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    List<Long> permissionIds
) {}