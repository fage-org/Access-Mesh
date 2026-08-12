package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 业务域更新请求体
 * <p>
 * 用于更新业务域的信息，包括名称和描述。
 * </p>
 *
 * @param domainId   业务域ID，必填
 * @param name       业务域名称，可选
 * @param description 业务域描述，可选
 */
public record BizDomainUpdateReq(
    @NotNull Long domainId,
    String name,
    String description
) {}