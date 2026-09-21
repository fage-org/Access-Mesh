package cn.ac.fage.accessmesh.access.resource.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 角色自动授权来源解释请求（T-PERM-073，契约 §12.3.1）。
 *
 * <p>角色使用业务键；target 可选（缺省=全集视图）；maxDepth/maxNodes/maxEdges 只限制输出，
 * 不影响完整推导。target 的 conditionId=null 表示无条件变体（须显式传 null 与带条件区分）。</p>
 */
public record AutoGrantExplainReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    @Valid Target target,
    @Min(1) @Max(50) Integer maxDepth,
    @Min(1) @Max(2000) Integer maxNodes,
    @Min(1) @Max(10000) Integer maxEdges
) {

    public int resolvedMaxDepth() {
        return maxDepth == null ? 20 : maxDepth;
    }

    public int resolvedMaxNodes() {
        return maxNodes == null ? 500 : maxNodes;
    }

    public int resolvedMaxEdges() {
        return maxEdges == null ? 1000 : maxEdges;
    }

    /**
     * 目标事实（资源业务键 + 操作码 + 条件身份）。
     */
    public record Target(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "资源类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String resourceTypeCode,
        @NotBlank String resourceCode,
        String codeType,
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "操作编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String operationCode,
        Long conditionId
    ) {}
}
