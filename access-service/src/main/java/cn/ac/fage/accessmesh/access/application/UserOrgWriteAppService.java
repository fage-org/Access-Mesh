package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserOrgAssignReq;

/**
 * 成员关系跨域写编排：同一事务维护 sys_user_org 与 user_role 投影。
 */
public interface UserOrgWriteAppService {

    void assignUserToOrgs(UserOrgAssignReq req);

    void removeUserFromOrg(Long userId, Long orgId);
}
