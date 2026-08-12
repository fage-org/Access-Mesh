package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 角色树查询请求体
 * <p>
 * 用于查询角色层级树结构。
 * domainCode为null或空时：返回全部角色。
 * domainCode设置时：按域分类规则判断当前域是否覆盖角色管理资源类型。
 * </p>
 *
 * @param domainCode 业务域编码，可选，用于过滤
 */
public record RoleTreeReq(
    String domainCode
) {}