package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 批量权限检查请求体
 * <p>
 * 使用稳定的业务键进行批量权限检查。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * 对于类型级操作（如CREATE），AuthCheckItem中的resourceCode可以为null。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param items             检查项列表，必填且不能为空
 * @param context           评估上下文，可选，用于条件权限评估
 */
public record BatchAuthCheckReq(
    @NotBlank(message = "主体类型编码不能为空")
    String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空")
    String subjectExternalId,
    @NotEmpty(message = "检查项不能为空")
    @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<AuthCheckItem> items,
    Map<String, Object> context
) {
    /**
     * 权限检查项
     * <p>
     * 表示单次权限检查的参数，包括资源类型、资源编码和操作。
     * </p>
     *
     * @param resourceTypeCode 资源类型编码，必填
     * @param resourceCode     资源编码，可选（类型级操作可为null）
     * @param operationCode    操作编码，必填
     * @param domainCode       业务域编码，可选
     * @param codeType         编码类型，可选
     * @param inheritMode      继承模式，可选
     */
    public record AuthCheckItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        @NotBlank String operationCode,
        String domainCode,
        String codeType,
        String inheritMode
    ) {}
}