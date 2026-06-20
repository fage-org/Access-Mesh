package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 接口快照请求体（T-PERM-001 迁入 perm-common 供 Gateway 共享）
 * <p>
 * 用于Gateway获取用户允许访问的API接口列表。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 * <p>
 * T-PERM-018：移除 permissionVersion（缓存下沉，不再条件请求）。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param serviceCode       服务编码，必填
 */
public record InterfaceSnapshotReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode
) {}
