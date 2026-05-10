package cn.ac.fage.accessmesh.perm.common.dto.req;

/**
 * 操作列表请求
 * <p>
 * 用于查询操作列表，支持可选的资源类型过滤。
 * </p>
 */
public record OperationListReq(
    /**
     * 资源类型码（可选），用于过滤特定资源类型的操作
     */
    String resourceTypeCode
) {}
