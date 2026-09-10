package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 单条权限校验请求
 * <p>
 * 使用稳定的业务键进行权限校验（主体类型码、主体外部ID、资源类型码、资源码、操作码）。
 * 租户ID通过 X-Tenant-Id 请求头传递，不在请求体中包含。
 * </p>
 * <p>
 * T-PERM-058 主资源上下文（可选）：查询目标为 depend_on 子权限实例时，
 * 传入父资源上下文（父 INSTANCE 判定通过且子行 dependOn ∈ 父命中集合才放行）；
 * 不传时子权限行不参与判定（fail-closed，拒绝原因为 DEPENDENT_NOT_IN_PARENT_CONTEXT）。
 * 字段名与 query-scopes 的父入参对齐；与服务端 access-service 副本双副本同形（HTTP 契约等价）。
 * </p>
 */
public record AuthCheckReq(
    /**
     * 主体类型码，如 "LOCAL_USER"
     */
    @NotBlank String subjectTypeCode,
    /**
     * 主体外部ID，如 userId.toString() 或外部用户标识
     */
    @NotBlank String subjectExternalId,
    /**
     * 资源类型码，如 "USER"、"ORG"
     */
    @NotBlank String resourceTypeCode,
    /**
     * 资源码，类型级权限（CREATE）时为null，实例级权限时为具体编码
     */
    String resourceCode,
    /**
     * 操作码，如 "CREATE"、"UPDATE"、"DELETE"
     */
    @NotBlank String operationCode,
    /**
     * 业务域码（可选），指定权限范围
     */
    String domainCode,
    /**
     * 编码类型过滤器（可选）
     */
    String codeType,
    /**
     * 继承模式（可选）：PARENT、CHILDREN、BOTH
     */
    String inheritMode,
    /**
     * 主资源类型码（可选，T-PERM-058）：与 parentResourceCode 成对提供，
     * 查询 depend_on 子权限实例时声明父上下文
     */
    String parentResourceTypeCode,
    /**
     * 主资源码（可选，T-PERM-058）：与 parentResourceTypeCode 成对提供
     */
    String parentResourceCode,
    /**
     * 主资源编码类型过滤器（可选，T-PERM-058）
     */
    String parentCodeType,
    /**
     * 主资源操作码集合（T-PERM-058）：给出父上下文时必填非空（与 query-scopes 对齐）
     */
    @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<String> parentOperationCodes,
    /**
     * 条件评估上下文（可选）
     */
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
