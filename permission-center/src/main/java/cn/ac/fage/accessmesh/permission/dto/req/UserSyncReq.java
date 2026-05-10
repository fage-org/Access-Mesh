package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户同步请求体
 * <p>
 * 用于从外部系统同步用户信息到权限中心。
 * 包括用户类型、外部标识、名称和版本信息。
 * </p>
 *
 * @param subjectTypeCode 用户类型编码，必填
 * @param externalId      外部标识，必填，用于与外部系统关联
 * @param name            用户名称，可选
 * @param enabled         是否启用，可选
 * @param extra           扩展属性JSON，可选
 * @param version         同步版本号，必填，用于检测数据变化
 */
public record UserSyncReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String externalId,
    String name,
    Boolean enabled,
    String extra,
    @NotBlank String version
) {}