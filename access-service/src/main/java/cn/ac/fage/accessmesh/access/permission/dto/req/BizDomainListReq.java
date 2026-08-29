package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 业务域列表查询请求体（T-PERM-026 收口：服务端 keyword 过滤 + 分页，范式同 system-config）
 * <p>
 * 支持按关键字模糊过滤（code/name/description，LIKE 大小写敏感）与服务端分页（ORDER BY code, id）。
 * 不传分页参数时返回字典全量（上限 PageUtil.MAX_PAGE_SIZE，先例 /role/list、/system-config/list）。
 * </p>
 *
 * @param keyword  关键字，可选，匹配 code/name/description
 * @param pageNum  页码，可选，默认 1
 * @param pageSize 每页条数，可选，默认 10；pageNum/pageSize 均未传时取上限全量
 */
public record BizDomainListReq(
    String keyword,
    Integer pageNum,
    Integer pageSize
) {}
