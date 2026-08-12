package cn.ac.fage.accessmesh.access.admin.dto.req;

/**
 * 同步任务增强查询请求 DTO
 * <p>
 * 支持按 syncAction / status / phase / batchKey / businessKey 过滤分页查询。
 * 当字段为 null 或空字符串时该过滤条件不生效。
 * </p>
 */
public record SyncTaskQueryReq(
    /** 同步动作（PERM_ABSTRACT_USER_SYNC 等），可空 */
    String syncAction,

    /** 状态（PENDING / PROCESSING / SUCCESS / FAILED），可空 */
    String status,

    /** 执行阶段（USER_SUBJECT / USER_RESOURCE 等），可空 */
    String phase,

    /** 批次键原文，可空 */
    String batchKey,

    /** 业务键原文，可空 */
    String businessKey,

    /** 页码，从 1 开始 */
    Integer pageNum,

    /** 每页大小 */
    Integer pageSize
) {
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 解析有效页码（>=1） */
    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /** 解析有效页大小（>0，最大 200） */
    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, 200);
    }
}
