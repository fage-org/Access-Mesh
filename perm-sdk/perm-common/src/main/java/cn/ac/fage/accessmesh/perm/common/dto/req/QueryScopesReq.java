package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 权限范围查询请求体（T-API-002 自 access-service 迁入 SDK 公共包）
 * <p>
 * 用于查询主资源上下文内的范围资源权限。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param subjectTypeCode       用户类型编码，必填
 * @param subjectExternalId     用户外部标识，必填
 * @param parentResourceTypeCode 主资源类型编码，必填
 * @param parentResourceCode    主资源编码，必填
 * @param parentCodeType        主编码类型，可选
 * @param parentOperationCodes  主操作编码列表，必填且不能为空
 * @param scopeResourceTypeCodes 范围资源类型编码列表，必填且不能为空
 * @param scopeOperationCodes   范围操作编码列表，必填且不能为空
 * @param scopeCodeType         范围编码类型，可选
 * @param domainCode            业务域编码，可选
 * @param context               评估上下文，可选
 */
public record QueryScopesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String parentResourceTypeCode,
    @NotBlank String parentResourceCode,
    String parentCodeType,
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<String> parentOperationCodes,
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<String> scopeResourceTypeCodes,
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    List<String> scopeOperationCodes,
    String scopeCodeType,
    String domainCode,
    Map<String, Object> context
) {}
