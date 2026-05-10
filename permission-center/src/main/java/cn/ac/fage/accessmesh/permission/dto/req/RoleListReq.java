package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * 角色列表查询请求体
 * <p>
 * 用于查询角色列表，支持按业务域、角色类型、关键词过滤和标准分页。
 * </p>
 *
 * @param domainCode   业务域编码，可选，用于过滤
 * @param roleTypeCode 角色类型编码，可选，用于过滤
 * @param keyword      关键词，可选，用于名称模糊搜索
 * @param pageNum      页码，可选
 * @param pageSize     每页条数，可选
 * @param sort         排序字段，可选
 */
public record RoleListReq(
    String domainCode,
    String roleTypeCode,
    String keyword,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}