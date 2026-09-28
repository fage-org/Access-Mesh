package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 操作准入快照请求体（T-ACCESS-059，契约总册 §25.2）。
 * <p>
 * 按服务+主体拉取准入快照（网关本地判定用）；租户取 Header／可信链；服务统一使用操作准入。
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
