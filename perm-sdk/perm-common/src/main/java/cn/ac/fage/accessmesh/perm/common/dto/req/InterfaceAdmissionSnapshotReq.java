package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 操作准入快照请求体（T-ACCESS-059，契约总册 §25.2）。
 * <p>
 * 按服务+主体拉取准入快照（网关本地判定用）；与 interface-snapshot 旧快照同形，
 * 租户取 Header／可信链。服务须为 OPERATION_ADMISSION 模式（LEGACY_API 服务按
 * 配置故障 20071 拒绝，不回落旧协议）。
 * </p>
 *
 * @param subjectTypeCode   主体类型编码，必填
 * @param subjectExternalId 主体外部标识，必填
 * @param serviceCode       服务编码，必填
 */
public record InterfaceAdmissionSnapshotReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode
) {}
