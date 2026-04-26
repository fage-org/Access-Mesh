package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.*;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

public interface OrgService {

    Long createOrg(OrgCreateReq req);

    void updateOrg(OrgUpdateReq req);

    void deleteOrg(Long id);

    OrgResp getOrg(Long id);

    PaginatedResult<OrgResp> pageOrgs(PageReq pageReq, OrgQuery query);

    List<OrgResp> treeOrgs(OrgQuery query);
}
