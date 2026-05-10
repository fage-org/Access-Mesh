package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * 角色树查询请求体
 * <p>
 * 用于查询角色层级树结构。
 * domainCode为null或空时：仅返回全局域的角色（无业务域关联的角色）。
 * domainCode设置时：返回该域的角色加上全局角色，语义与RoleManageService#getRoleTree一致。
 * </p>
 *
 * @param domainCode 业务域编码，可选，用于过滤
 */
public record RoleTreeReq(
    String domainCode
) {}