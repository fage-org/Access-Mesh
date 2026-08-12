package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 域配置获取请求体
 * <p>
 * 用于根据业务域编码和配置类型查询域配置详情。
 * </p>
 *
 * @param domainCode 业务域编码，必填
 * @param configType 配置类型编码，必填
 */
public record DomainConfigGetReq(
    @NotBlank String domainCode,
    @NotBlank String configType
) {}