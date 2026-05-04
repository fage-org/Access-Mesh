package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Single auth check request — uses stable business keys.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 *
 * For type-level operations (e.g., CREATE), resourceCode can be null.
 * When resourceCode is null, the check is against scopeAll permissions on the resource type.
 */
public record AuthCheckReq(
    @NotBlank(message = "主体类型编码不能为空")
    String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空")
    String subjectExternalId,
    @NotBlank(message = "资源类型编码不能为空")
    String resourceTypeCode,
    String resourceCode,  // Nullable for type-level operations (CREATE)
    @NotBlank(message = "操作编码不能为空")
    String operationCode,
    String domainCode,
    String codeType,
    String inheritMode,
    Map<String, Object> context
) {}
