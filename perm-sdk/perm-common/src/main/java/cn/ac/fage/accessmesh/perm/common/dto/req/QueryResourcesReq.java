package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 资源查询请求体（T-API-002 自 access-service 迁入 SDK 公共包）
 * <p>
 * 用于查询用户可访问的资源列表，支持按资源类型和操作过滤。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param subjectTypeCode    用户类型编码，必填
 * @param subjectExternalId  用户外部标识，必填
 * @param resourceTypeCodes  资源类型编码列表，必填且不能为空
 * @param operationCodes     操作编码列表，必填且不能为空
 * @param domainCode         业务域编码，可选
 * @param codeType           编码类型，可选
 * @param includeInherited   是否包含继承权限，可选
 * @param includeChildren    是否包含子资源，可选
 * @param context            评估上下文，可选
 */
public record QueryResourcesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotEmpty List<String> resourceTypeCodes,
    @NotEmpty List<String> operationCodes,
    String domainCode,
    String codeType,
    Boolean includeInherited,
    Boolean includeChildren,
    Map<String, Object> context
) {}
