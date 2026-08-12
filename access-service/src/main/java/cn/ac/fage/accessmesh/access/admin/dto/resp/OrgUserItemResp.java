package cn.ac.fage.accessmesh.access.admin.dto.resp;

/**
 * 组织下用户项响应记录类
 * <p>
 * 用于 /org/users 接口返回某组织/岗位下的用户简要信息。
 * </p>
 *
 * @param userId    用户ID
 * @param username  登录账号
 * @param name      用户姓名
 * @param avatar    头像URL（可选）
 * @param isPrimary 是否主组织
 */
public record OrgUserItemResp(
    /**
     * 用户ID
     */
    Long userId,

    /**
     * 登录账号
     */
    String username,

    /**
     * 用户姓名
     */
    String name,

    /**
     * 头像URL
     */
    String avatar,

    /**
     * 是否主组织
     */
    Boolean isPrimary
) {}
