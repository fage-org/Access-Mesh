package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户创建请求记录类
 * <p>
 * 用于创建新用户的请求参数。
 * 包含用户名、姓名、手机号、邮箱、状态等基本信息，
 * 以及可选的组织分配信息（创建时一步完成组织关联）。
 * </p>
 *
 * @param username    用户名（必填，登录账号）
 * @param name        姓名（必填，显示名称）
 * @param phone       手机号（可选）
 * @param email       邮箱（可选）
 * @param status      状态（可选，默认 1=启用；1=启用，0=停用——与 DDL sys_user.status 语义一致）
 * @param orgId       所属组织ID（可选，创建时一步完成组织分配）
 * @param primaryOrg  是否设为主组织（可选，orgId存在时默认true）
 */
public record UserCreateReq(
    /**
     * 用户名（登录账号）
     */
    @NotBlank(message = "用户名不能为空")
    String username,

    /**
     * 姓名（显示名称）
     */
    @NotBlank(message = "姓名不能为空")
    String name,

    /**
     * 手机号
     */
    String phone,

    /**
     * 邮箱
     */
    String email,

    /**
     * 状态（1=启用，0=停用；与 DDL sys_user.status 一致，缺省 1）
     */
    Integer status,

    /**
     * 所属组织ID（可选）
     * <p>
     * 提供时，创建用户的同时建立组织关联，无需再调 /user-org/assign。
     * 该组织必须属于默认组织树，否则返回参数错误。
     * </p>
     */
    Long orgId,

    /**
     * 是否设为主组织（可选）
     * <p>
     * 仅在 orgId 不为空时生效。默认为 true。
     * </p>
     */
    Boolean primaryOrg
) {}