package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 角色树查询请求体
 * <p>
 * 用于查询角色层级树结构。
 * domainCode为null或空时：返回全部角色。
 * domainCode设置时：按域分类规则判断当前域是否覆盖角色管理资源类型。
 * </p>
 *
 * @param domainCode  业务域编码，可选，用于过滤
 * @param enabledOnly 仅返回启用角色（status=1）；null/false 返回全部有效角色
 *                   （T-PERM-022：默认含禁用——角色管理页需禁用角色可见可再启用；
 *                   授权页主体树传 true；T-PERM-022 设计定案：前端入参后端过滤）
 */
public record RoleTreeReq(
    String domainCode,
    Boolean enabledOnly
) {}