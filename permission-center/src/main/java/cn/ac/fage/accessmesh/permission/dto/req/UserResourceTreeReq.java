package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 用户资源树查询请求体
 * <p>
 * 用于查询用户可访问的资源树结构，支持按资源类型和操作过滤。
 * </p>
 *
 * @param subjectTypeCode    用户类型编码，必填
 * @param subjectExternalId  用户外部标识，必填
 * @param domainCode         业务域编码，可选
 * @param resourceTypeCodes  资源类型编码列表，可选，用于过滤
 * @param operationCodes     操作编码列表，可选，用于过滤
 * @param resourceKeyword    资源关键词，可选，用于名称模糊搜索
 */
public record UserResourceTreeReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    String domainCode,
    List<String> resourceTypeCodes,
    List<String> operationCodes,
    String resourceKeyword
) {}