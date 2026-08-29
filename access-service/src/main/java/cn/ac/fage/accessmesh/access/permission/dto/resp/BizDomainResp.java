package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 业务域响应体
 * <p>
 * 返回业务域的详细信息，包括编码、名称、描述等。
 * 用于业务域查询接口的响应。
 * </p>
 *
 * @param id          业务域ID
 * @param tenantId    租户ID
 * @param code        业务域编码，唯一标识
 * @param name        业务域名称，用于显示
 * @param description 业务域描述
 * @param global      是否全局域（每租户仅一个，范围隐式包含未被其他域认领的资源类型；全局域不可删）
 * @param createdAt   创建时间
 */
public record BizDomainResp(
    Long id,
    Long tenantId,
    String code,
    String name,
    String description,
    Boolean global,
    LocalDateTime createdAt
) {}