package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Single auth check request — uses stable business keys.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record AuthCheckReq(
    @NotBlank(message = "主体类型编码不能为空")
    String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空")
    String subjectExternalId,
    @NotBlank(message = "资源类型编码不能为空")
    String resourceTypeCode,
    @NotBlank(message = "资源编码不能为空")
    String resourceCode,
    @NotBlank(message = "操作编码不能为空")
    String operationCode,
    String domainCode,
    String codeType,
    String inheritMode,
    Map<String, Object> context
) {}
