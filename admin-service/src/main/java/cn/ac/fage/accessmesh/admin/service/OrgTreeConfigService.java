package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface OrgTreeConfigService {

    Long createOrgTreeConfig(SysOrgTreeConfig config);

    void updateOrgTreeConfig(SysOrgTreeConfig config);

    void deleteOrgTreeConfigs(IdsReq req);

    void setDefault(Long id);

    SysOrgTreeConfig getOrgTreeConfig(Long id);

    PaginatedResult<SysOrgTreeConfig> pageOrgTreeConfigs(PageReq pageReq);
}
