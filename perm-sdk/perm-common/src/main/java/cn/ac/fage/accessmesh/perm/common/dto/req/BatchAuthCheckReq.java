package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 批量权限校验请求
 * <p>
 * 在单次调用中校验多个资源/操作的权限，减少网络往返开销。
 * </p>
 * <p>
 * T-PERM-058 主资源上下文（可选，请求级）：与 query-scopes 的父入参对齐——批量项
 * 通常查询同一主资源上下文下的多个目标（如报表A上下文内的广东/杭州/上海）；
 * 传入时子权限行要求父 INSTANCE 判定通过且 dependOn ∈ 父命中集合，不传时
 * 子权限行不参与判定（fail-closed）。与服务端 access-service 副本双副本同形（HTTP 契约等价）。
 * </p>
 */
public record BatchAuthCheckReq(
    /**
     * 主体类型码
     */
    @NotBlank String subjectTypeCode,
    /**
     * 主体外部ID
     */
    @NotBlank String subjectExternalId,
    /**
     * 批量校验项列表
     */
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<@NotNull @Valid AuthCheckItem> items,
    /**
     * 主资源类型码（可选，T-PERM-058）：与 parentResourceCode 成对提供
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

    /**
     * 批量校验中的单项
     * <p>
     * 表示单个资源操作的权限校验请求。
     * </p>
     */
    public record AuthCheckItem(
        /**
         * 资源类型码
         */
        @NotBlank String resourceTypeCode,
        /**
         * 资源码（可选）
         */
        String resourceCode,
        /**
         * 操作码
         */
        @NotBlank String operationCode,
        /**
         * 业务域码（可选）
         */
        String domainCode,
        /**
         * 编码类型（可选）
         */
        String codeType,
        /**
         * 继承模式（可选）
         */
        String inheritMode
    ) {}
}
