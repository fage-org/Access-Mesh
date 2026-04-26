package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;

import java.util.List;

public interface UserOrgService {

    void assignUserToOrgs(UserOrgAssignReq req);

    void removeUserFromOrg(Long userId, Long orgId);

    void setPrimaryOrg(Long userId, Long orgId);

    List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId);
}
