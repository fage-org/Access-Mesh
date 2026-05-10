package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * 用户更新请求记录类
 * <p>
 * 用于更新用户信息的请求参数。
 * 所有字段均为可选，仅更新提供的字段。
 * </p>
 *
 * @param id     用户ID（必填，用于定位用户）
 * @param name   姓名（可选）
 * @param phone  手机号（可选）
 * @param email  邮箱（可选）
 * @param status 状态（可选，0=正常，1=禁用）
 */
public record UserUpdateReq(
    /**
     * 用户ID
     */
    Long id,

    /**
     * 姓名（显示名称）
     */
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