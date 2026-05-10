package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * 权限版本查询响应体
 * <p>
 * 返回权限版本号信息，用于缓存一致性检查。
 * Gateway通过版本号判断缓存的权限快照是否过期。
 * </p>
 *
 * @param roleId         角色ID
 * @param roleTypeCode   角色类型编码
 * @param roleExternalId 角色外部标识
 * @param version        权限版本号，权限变更时递增
 */
public record PermissionVersionResp(
    Long roleId,
    String roleTypeCode,
    String roleExternalId,
    long version
) {}