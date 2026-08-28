package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 角色详情查询请求体
 * <p>
 * 用业务键二元组定位角色（tenantId 走上下文）：schema 唯一索引
 * {@code uk_abstract_role_external (tenant_id, role_type, external_id)} 保证
 * 租户内 (role_type, external_id) 唯一；不含 domainCode（abstract_role 无域字段，
 * 编码规范 §18，T-PERM-022 前旧版带 domainCode 为 bizDomainId 旧时代遗留）。
 * </p>
 *
 * @param roleTypeCode   角色类型编码，必填，最大64字符
 * @param roleExternalId 角色外部标识，必填，最大128字符
 */
public record RoleDetailReq(
    @NotBlank(message = "角色类型编码不能为空") @Size(max = 64) String roleTypeCode,
    @NotBlank(message = "角色外部标识不能为空") @Size(max = 128) String roleExternalId
) {}
