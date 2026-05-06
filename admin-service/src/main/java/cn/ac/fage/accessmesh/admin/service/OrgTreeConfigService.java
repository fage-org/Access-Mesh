package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgTreeConfigResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface OrgTreeConfigService {

    Long createOrgTreeConfig(SysOrgTreeConfig config);

    void updateOrgTreeConfig(SysOrgTreeConfig config);

    void deleteOrgTreeConfigs(IdsReq req);

    void setDefault(Long id);

    OrgTreeConfigResp getOrgTreeConfig(Long id);

    PaginatedResult<OrgTreeConfigResp> pageOrgTreeConfigs(PageReq pageReq);
}
