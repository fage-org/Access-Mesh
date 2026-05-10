package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户详情响应记录类
 * <p>
 * 包含用户的完整信息，包括基本属性、所属组织、创建时间等。
 * 用于用户详情查询和列表展示。
 * </p>
 *
 * @param id        用户ID
 * @param username  用户名
 * @param name      姓名
 * @param phone     手机号
 * @param email     邮箱
 * @param status    状态
 * @param orgs      所属组织列表
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UserResp(
    /**
     * 用户ID
     */
    Long id,

    /**
     * 用户名（登录账号）
     */
    String username,

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
    Integer status,

    /**
     * 所属组织列表
     */
    List<OrgBrief> orgs,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
    /**
     * 组织简要信息记录类
     * <p>
     * 用户所属组织的简要信息，用于列表展示。
     * </p>
     *
     * @param orgId    组织ID
     * @param orgName  组织名称
     * @param orgType  组织类型
     * @param isPrimary 是否主要组织
     */
    public record OrgBrief(
        /**
         * 组织ID
         */
        Long orgId,

        /**
         * 组织名称
         */
        String orgName,

        /**
         * 组织类型
         */
        String orgType,

        /**
         * 是否主要组织
         */
        boolean isPrimary
    ) {}
}