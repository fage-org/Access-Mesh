package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;

import java.util.List;

/**
 * 用户组织关系服务接口
 * <p>
 * 提供用户与组织的关联管理功能。
 * 支持用户加入多个组织、设置主组织、查询用户的组织列表等。
 * </p>
 */
public interface UserOrgService {

    /**
     * 批量分配用户到组织
     * <p>
     * 将指定用户添加到多个组织，建立用户与组织的关联关系。
     * </p>
     *
     * @param req 用户组织分配请求，包含用户ID和组织ID列表
     */
    void assignUserToOrgs(UserOrgAssignReq req);

    /**
     * 移除用户与组织的关联
     * <p>
     * 将用户从指定组织中移除。
     * </p>
     *
     * @param userId 用户ID
     * @param orgId  组织ID
     */
    void removeUserFromOrg(Long userId, Long orgId);

    /**
     * 设置用户的主组织
     * <p>
     * 将指定组织设置为用户的主组织。
     * 主组织用于确定用户的默认权限范围。
     * </p>
     *
     * @param userId 用户ID
     * @param orgId  组织ID
     */
    void setPrimaryOrg(Long userId, Long orgId);

    /**
     * 获取用户所属的组织列表
     * <p>
     * 查询用户所属的所有组织信息，包括组织名称、是否为主组织等。
     * </p>
     *
     * @param userId 用户ID
     * @return 组织简要信息列表
     */
    List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId);
}
