package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;

/**
 * 组织跨域写编排：同一事务维护 sys_org 与本地权限投影。
 */
public interface OrgWriteAppService {

    Long createOrg(OrgCreateReq req);

    void updateOrg(OrgUpdateReq req);

    void deleteOrg(Long id);
}
