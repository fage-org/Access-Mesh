package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API映射添加请求体
 * <p>
 * 用于为资源添加API映射关系，结合路径参数和映射字段。
 * </p>
 *
 * @param resourceId   资源ID，必填
 * @param serviceCode  服务编码，必填
 * @param httpMethod   HTTP方法，必填
 * @param pathPattern  路径模式，必填
 * @param matchOrder   匹配顺序，可选，数值越小优先级越高
 * @param enabled      是否启用，可选
 * @param extra        扩展属性JSON，可选
 */
public record ApiMappingAddReq(
    @NotNull Long resourceId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra
) {}