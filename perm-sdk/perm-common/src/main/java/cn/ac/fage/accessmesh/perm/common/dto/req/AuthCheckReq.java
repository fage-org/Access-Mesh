package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 单条权限校验请求
 * <p>
 * 使用稳定的业务键进行权限校验（主体类型码、主体外部ID、资源类型码、资源码、操作码）。
 * 租户ID通过 X-Tenant-Id 请求头传递，不在请求体中包含。
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
     * 条件评估上下文（可选）
     */
    Map<String, Object> context
) {}