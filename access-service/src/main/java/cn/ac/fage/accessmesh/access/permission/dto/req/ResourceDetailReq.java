package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 资源详情查询请求体
 * <p>
 * 用于查询资源的详细信息，使用稳定的业务键标识资源。
 * </p>
 *
 * @param domainCode        业务域编码，可选，最大64字符
 * @param resourceTypeCode  资源类型编码，必填，最大64字符
 * @param resourceCode      资源编码，必填，最大256字符
 * @param codeType          编码类型，可选，最大32字符
 */
public record ResourceDetailReq(
    @Size(max = 64) String domainCode,
    @NotBlank(message = "资源类型编码不能为空") @Size(max = 64) String resourceTypeCode,
    @NotBlank(message = "资源编码不能为空") @Size(max = 256) String resourceCode,
    @Size(max = 32) String codeType
) {}