package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.ConfigResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface ConfigService {

    PaginatedResult<ConfigResp> pageConfigs(PageReq pageReq);

    ConfigResp getConfig(Long id);

    void updateConfig(ConfigUpdateReq req);

    void deleteConfig(IdsReq req);
}
