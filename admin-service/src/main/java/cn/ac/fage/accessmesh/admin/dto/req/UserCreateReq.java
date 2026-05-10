package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户创建请求记录类
 * <p>
 * 用于创建新用户的请求参数。
 * 包含用户名、姓名、手机号、邮箱、状态等基本信息。
 * </p>
 *
 * @param username 用户名（必填，登录账号）
 * @param name     姓名（必填，显示名称）
 * @param phone    手机号（可选）
 * @param email    邮箱（可选）
 * @param status   状态（可选，默认0=正常）
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
     * 状态（0=正常，1=禁用）
     */
    Integer status
) {}