package cn.ac.fage.accessmesh.access.admin.dto.req;

/**
 * 文件分页查询请求记录类
 * <p>
 * 用于文件列表的分页查询参数。
 * 支持按业务类型过滤。
 * </p>
 *
 * @param pageNum  页码（可选，默认1）
 * @param pageSize 每页大小（可选，默认10）
 * @param sort     排序字段（可选）
 * @param bizType  业务类型（可选，用于过滤）
 */
public record FilePageReq(
    /**
     * 页码
     */
    Integer pageNum,

    /**
     * 每页大小
     */
    Integer pageSize,

    /**
     * 排序字段
     */
    String sort,

    /**
     * 业务类型（用于过滤）
     */
    String bizType
) {}