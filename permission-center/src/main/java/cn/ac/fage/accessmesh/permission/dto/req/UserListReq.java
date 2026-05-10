package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * 用户列表查询请求体
 * <p>
 * 用于查询用户列表，支持按用户类型、业务域、关键词过滤和标准分页。
 * </p>
 *
 * @param subjectTypeCode 用户类型编码，可选，用于过滤
 * @param domainCode      业务域编码，可选，用于过滤
 * @param keyword         关键词，可选，用于名称模糊搜索
 * @param pageNum         页码，可选
 * @param pageSize        每页条数，可选
 * @param sort            排序字段，可选
 */
public record UserListReq(
    String subjectTypeCode,
    String domainCode,
    String keyword,
    Integer pageNum,
    Integer pageSize,
    String sort
) {}