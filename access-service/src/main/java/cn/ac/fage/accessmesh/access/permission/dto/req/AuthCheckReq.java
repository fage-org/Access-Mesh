package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 单次权限检查请求体
 * <p>
 * 使用稳定的业务键进行权限检查。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * 对于类型级操作（如CREATE），resourceCode可以为null，
 * 此时检查是对资源类型的scopeAll权限。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param resourceTypeCode  资源类型编码，必填
 * @param resourceCode      资源编码，可选（类型级操作可为null）
 * @param operationCode     操作编码，必填
 * @param domainCode        业务域编码，可选
 * @param codeType          编码类型，可选
 * @param inheritMode       继承模式，可选
 * @param context           评估上下文，可选，用于条件权限评估
 */
public record AuthCheckReq(
    @NotBlank(message = "主体类型编码不能为空")
    String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空")
    String subjectExternalId,
    @NotBlank(message = "资源类型编码不能为空")
    String resourceTypeCode,
    String resourceCode,
    @NotBlank(message = "操作编码不能为空")
    String operationCode,
    String domainCode,
    String codeType,
    String inheritMode,
    Map<String, Object> context
) {}