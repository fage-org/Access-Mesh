package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 接口权限检查请求体
 * <p>
 * Gateway回调的接口权限检查请求，根据服务编码、HTTP方法、路径检查权限。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param serviceCode       服务编码，必填
 * @param httpMethod        HTTP方法，必填
 * @param path              请求路径，必填
 * @param context           评估上下文，可选，用于条件权限评估
 */
public record CheckInterfaceReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String path,
    Map<String, Object> context
) {}