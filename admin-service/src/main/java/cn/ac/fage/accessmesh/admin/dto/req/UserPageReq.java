package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 用户分页查询请求记录类
 * <p>
 * 用于用户列表的分页查询参数。
 * 合并分页参数和查询条件，支持按用户名、姓名、手机号、邮箱、状态过滤。
 * </p>
 *
 * @param pageNum  页码（可选，默认1，最小1）
 * @param pageSize 每页大小（可选，默认20，范围1-100）
 * @param sort     排序字段（可选）
 * @param username 用户名（可选，模糊匹配）
 * @param name     姓名（可选，模糊匹配）
 * @param phone    手机号（可选，模糊匹配）
 * @param email    邮箱（可选，模糊匹配）
 * @param status   状态（可选，0=正常，1=禁用）
 */
public record UserPageReq(
    /**
     * 页码（最小1）
     */
    @Min(1) Integer pageNum,

    /**
     * 每页大小（范围1-100）
     */
    @Min(1) @Max(100) Integer pageSize,

    /**
     * 排序字段
     */
    String sort,

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
    Integer status,

    /**
     * Organization id used for subtree member filtering.
     */
    Long orgId
) {
    /**
     * 获取页码（默认1）
     *
     * @return 页码
     */
    public int getPageNum() { return pageNum != null ? pageNum : 1; }

    /**
     * 获取每页大小（默认20）
     *
     * @return 每页大小
     */
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}
