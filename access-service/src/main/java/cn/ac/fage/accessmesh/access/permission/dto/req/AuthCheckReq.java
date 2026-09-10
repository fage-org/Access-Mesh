package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 单次权限检查请求体
 * <p>
 * 使用稳定的业务键进行权限检查。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * 对于类型级操作（如CREATE），resourceCode可以为null，
 * 此时检查是对资源类型的scopeAll权限。
 * </p>
 * <p>
 * T-PERM-058 主资源上下文（可选）：查询目标为 depend_on 子权限实例时，
 * 传入父资源上下文（父 INSTANCE 判定通过且子行 dependOn ∈ 父命中集合才放行）；
 * 不传时子权限行不参与判定（fail-closed，拒绝原因为 DEPENDENT_NOT_IN_PARENT_CONTEXT）。
 * 字段名与 query-scopes 的父入参对齐。
 * </p>
 *
 * @param subjectTypeCode        用户类型编码，必填
 * @param subjectExternalId      用户外部标识，必填
 * @param resourceTypeCode       资源类型编码，必填
 * @param resourceCode           资源编码，可选（类型级操作可为null）
 * @param operationCode          操作编码，必填
 * @param domainCode             业务域编码，可选
 * @param codeType               编码类型，可选
 * @param inheritMode            继承模式，可选
 * @param parentResourceTypeCode 主资源类型编码，可选（与 parentResourceCode 成对提供）
 * @param parentResourceCode     主资源编码，可选（与 parentResourceTypeCode 成对提供）
 * @param parentCodeType         主资源编码类型，可选
 * @param parentOperationCodes   主资源操作编码集合（给出父上下文时必填非空，与 query-scopes 对齐）
 * @param context                评估上下文，可选，用于条件权限评估
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
    String parentResourceTypeCode,
    String parentResourceCode,
    String parentCodeType,
    @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<String> parentOperationCodes,
    Map<String, Object> context
) {
    @AssertTrue(message = "parentResourceTypeCode 与 parentResourceCode 必须同时提供或同时缺省")
    public boolean isParentContextConsistent() {
        return (parentResourceTypeCode == null) == (parentResourceCode == null);
    }

    @AssertTrue(message = "给出主资源上下文时 parentOperationCodes 必须一并提供且非空（与 query-scopes 必填口径对齐；缺省/空集时父判定必不命中，勿依赖静默回退）")
    public boolean isParentOperationsPresent() {
        if (parentResourceTypeCode == null) {
            return true;
        }
        return parentOperationCodes != null && !parentOperationCodes.isEmpty();
    }
}
