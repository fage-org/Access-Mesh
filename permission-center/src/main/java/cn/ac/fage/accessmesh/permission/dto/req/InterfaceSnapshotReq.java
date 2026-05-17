package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 接口快照请求体
 * <p>
 * 用于Gateway获取用户允许访问的API接口列表。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param serviceCode       服务编码，必填
 * @param permissionVersion 权限令牌，用于缓存一致性检查，可选
 */
public record InterfaceSnapshotReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode,
    String permissionVersion
) {}