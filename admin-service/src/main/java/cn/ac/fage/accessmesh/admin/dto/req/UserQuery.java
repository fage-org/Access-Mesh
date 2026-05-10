package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * 用户查询请求记录类
 * <p>
 * 用于用户列表的条件查询参数。
 * 支持按用户名、姓名、手机号、邮箱、状态过滤。
 * </p>
 *
 * @param username 用户名（可选，模糊匹配）
 * @param name     姓名（可选，模糊匹配）
 * @param phone    手机号（可选，模糊匹配）
 * @param email    邮箱（可选，模糊匹配）
 * @param status   状态（可选，0=正常，1=禁用）
 */
public record UserQuery(
    /**
     * 用户名（模糊匹配）
     */
    String username,

    /**
     * 姓名（模糊匹配）
     */
    String name,

    /**
     * 手机号（模糊匹配）
     */
    String phone,

    /**
     * 邮箱（模糊匹配）
     */
    String email,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status
) {}