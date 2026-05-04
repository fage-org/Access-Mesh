package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * Batch auth check request — uses stable business keys.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 *
 * For type-level operations (e.g., CREATE), resourceCode can be null in AuthCheckItem.
 */
public record BatchAuthCheckReq(
    @NotBlank(message = "主体类型编码不能为空")
    String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空")
    String subjectExternalId,
    @NotEmpty(message = "检查项不能为空")
    List<AuthCheckItem> items,
    Map<String, Object> context
) {
    public record AuthCheckItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,  // Nullable for type-level operations (CREATE)
        @NotBlank String operationCode,
        String domainCode,
        String codeType,
        String inheritMode
    ) {}
}
