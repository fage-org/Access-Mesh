package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 批量权限校验请求
 * <p>
 * 在单次调用中校验多个资源/操作的权限，减少网络往返开销。
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
    List<AuthCheckItem> items,
    /**
     * 条件评估上下文（可选）
     */
    Map<String, Object> context
) {

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