package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 服务API列表查询请求体
 * <p>
 * 用于查询指定服务的API接口列表。
 * </p>
 *
 * @param serviceCode 服务编码，必填
 */
public record ServiceConfigApisReq(
    @NotBlank String serviceCode
) {}