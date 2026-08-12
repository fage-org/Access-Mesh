package cn.ac.fage.accessmesh.access.permission.dto.resp;

/**
 * 角色摘要响应体
 * <p>
 * 返回角色的精简信息，包括ID、类型、外部标识和名称。
 * 用于分组角色额外角色列表查询的响应。
 * </p>
 *
 * @param id           角色ID
 * @param roleTypeCode 角色类型编码
 * @param externalId   角色外部标识
 * @param name         角色名称
 */
public record RoleSummaryResp(
    Long id,
    String roleTypeCode,
    String externalId,
    String name
) {}