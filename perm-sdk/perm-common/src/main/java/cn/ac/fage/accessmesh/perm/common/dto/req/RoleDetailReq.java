package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 角色详情查询请求
 * <p>
 * 用业务键二元组 (roleTypeCode, roleExternalId) 定位角色（租户经 X-Tenant-Id 传递）；
 * schema {@code uk_abstract_role_external} 保证租户内唯一（T-PERM-028 同批业务键定稿）。
 * perm-common 单源契约——服务端 Controller 与 SDK 消费方共用本类（T-PERM-065）。
 * </p>
 */
public record RoleDetailReq(
    /**
     * 角色类型编码，必填，最大64字符
     */
    @NotBlank(message = "角色类型编码不能为空") @Size(max = 64) String roleTypeCode,
    /**
     * 角色外部标识，必填，最大128字符
     */
    @NotBlank(message = "角色外部标识不能为空") @Size(max = 128) String roleExternalId
) {}
