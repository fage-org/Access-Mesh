package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * API映射更新请求体
 * <p>
 * 用于更新API映射信息，字段命名与ApiMappingAddReq保持一致。
 * </p>
 *
 * @param resourceId   资源ID，必填
 * @param mappingId    映射ID，必填
 * @param httpMethod   HTTP方法，可选
 * @param pathPattern  路径模式，可选
 * @param matchOrder   匹配顺序，可选
 * @param enabled      是否启用，可选
 * @param extra        扩展属性JSON，可选
 */
public record ApiMappingUpdateReq(
    @NotNull Long resourceId,
    @NotNull Long mappingId,
    String httpMethod,
    String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra
) {}