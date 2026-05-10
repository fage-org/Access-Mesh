package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 业务域详情查询请求体
 * <p>
 * 用于查询业务域的详细信息，使用业务域编码标识。
 * </p>
 *
 * @param domainCode 业务域编码，必填，最大64字符
 */
public record BizDomainDetailReq(
    @NotBlank(message = "业务域编码不能为空") @Size(max = 64) String domainCode
) {}