package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 子权限允许类型只读查询请求体（api-contract §6.5.2）
 * <p>
 * 目标角色业务键仅用于门禁定位（resolveRoleId 失败 20001、无 ROLE:VIEW 抛
 * SecurityException——本接口不采用空结果掩盖鉴权失败）；parentResourceTypeCode
 * 定位 SUB_PERM 策略解析。
 * </p>
 *
 * @param domainCode              业务域编码，可选（授权页恒传 null，同 §6.4 list 口径）
 * @param roleTypeCode            角色类型编码，必填（门禁定位用）
 * @param roleExternalId          角色外部标识，必填（门禁定位用）
 * @param parentResourceTypeCode  父资源类型编码，必填（无效 20007）
 */
public record SubPermAllowedTypesReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    @NotBlank String parentResourceTypeCode
) {}
