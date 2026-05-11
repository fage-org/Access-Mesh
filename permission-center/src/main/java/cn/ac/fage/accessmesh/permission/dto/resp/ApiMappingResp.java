package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * API映射响应体
 * <p>
 * 返回资源与API接口映射关系的详细信息。
 * 用于API映射查询接口的响应，支持Gateway接口级权限校验。
 * </p>
 *
 * @param id               映射ID
 * @param tenantId         租户ID
 * @param resourceEntityId 资源实体ID
 * @param serviceCode      服务编码
 * @param httpMethod       HTTP方法（GET/POST/PUT/DELETE等）
 * @param pathPattern      路径模式，支持通配符匹配
 * @param matchOrder       匹配顺序，数值越小优先级越高
 * @param enabled          是否启用
 * @param extra            扩展属性JSON
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record ApiMappingResp(
    Long id,
    Long tenantId,
    Long resourceEntityId,
    String serviceCode,
    String httpMethod,
    String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}