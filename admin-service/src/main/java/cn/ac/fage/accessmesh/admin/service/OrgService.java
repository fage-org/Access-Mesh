package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

public interface OrgService {

    Long createOrg(OrgCreateReq req);

    BatchResultResp batchCreateOrgs(OrgBatchCreateReq req);

    void updateOrg(OrgUpdateReq req);

    void deleteOrg(Long id);

    void batchDeleteOrgs(IdsReq req);

    OrgResp getOrg(Long id);

    PaginatedResult<OrgResp> pageOrgs(OrgPageReq req);

    List<OrgResp> treeOrgs(OrgQuery query);

    List<Long> getDescendantOrgIds(Long orgId);
}
