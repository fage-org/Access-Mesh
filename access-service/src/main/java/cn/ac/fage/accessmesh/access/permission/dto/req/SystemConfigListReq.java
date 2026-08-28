package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 系统配置列表查询请求体
 * <p>
 * 支持按关键字模糊过滤（configKey/description，LIKE 大小写敏感）与服务端分页。
 * 不传分页参数时返回字典全量（上限 PageUtil.MAX_PAGE_SIZE，先例 /role/list）。
 * </p>
 *
 * @param keyword  关键字，可选，匹配 configKey/description
 * @param pageNum  页码，可选，默认 1
 * @param pageSize 每页条数，可选，默认 10；pageNum/pageSize 均未传时取上限全量
 */
public record SystemConfigListReq(
    String keyword,
    Integer pageNum,
    Integer pageSize
) {}
