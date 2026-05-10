package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.Set;

/**
 * 权限树查询请求体
 * <p>
 * 用于从起始资源节点查询权限树，返回祖先和子孙方向的可达资源。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param resourceTypeCode  资源类型编码，必填
 * @param resourceCode      起始资源编码，必填
 * @param codeType          编码类型，可选
 * @param operationCodes    操作编码集合，必填且不能为空
 * @param direction         查询方向，必填（ANCESTORS向上/DESCENDANTS向下/BOTH双向）
 * @param maxDepth          最大遍历深度，可选
 * @param domainCode        业务域编码，可选
 * @param context           评估上下文，可选
 */
public record PermissionTreeReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,
    String codeType,
    @NotEmpty Set<String> operationCodes,
    @Pattern(regexp = "ANCESTORS|DESCENDANTS|BOTH")
    @NotBlank String direction,
    Integer maxDepth,
    String domainCode,
    Map<String, Object> context
) {}