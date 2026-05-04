package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.service.domain.OrgSyncHandler;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdsReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrgSyncHandlerImpl implements OrgSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(OrgSyncHandlerImpl.class);
    private static final String RESOURCE_TYPE_ORG = "ORG";

    private final PermissionFeignClient permissionFeignClient;

    public OrgSyncHandlerImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    @Override
    public Long syncOrgToPermissionCenter(Long tenantId, SysOrg org) {
        if (org == null || org.getId() == null) {
            return null;
        }

        String code = generateResourceCode(org);
        ResourceCreateReq req = new ResourceCreateReq(
            null, // bizDomainId
            org.getParentId() != null && org.getParentId() != 0L ? org.getParentId() : 0L,
            RESOURCE_TYPE_ORG,
            code,
            null, // codeType
            org.getName(),
            null, // path
            org.getStatus() != null ? org.getStatus() : 1,
            org.getSortOrder() != null ? org.getSortOrder() : 0,
            buildOrgExtra(org)
        );

        PermResult<Map<String, Object>> result = permissionFeignClient.createResource(req);
        if (result == null || result.code() != 200 || result.data() == null) {
            log.warn("Failed to sync org to permission-center: orgId={}, orgName={}",
                org.getId(), org.getName());
            return null;
        }

        Object idObj = result.data().get("id");
        if (idObj != null) {
            Long permResourceId = Long.valueOf(idObj.toString());
            log.info("Synced org to permission-center: orgId={}, permResourceId={}",
                org.getId(), permResourceId);
            return permResourceId;
        }
        return null;
    }

    @Override
    public int batchSyncOrgs(Long tenantId, Iterable<SysOrg> orgs) {
        int successCount = 0;
        for (SysOrg org : orgs) {
            Long permResourceId = syncOrgToPermissionCenter(tenantId, org);
            if (permResourceId != null) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public boolean deleteOrgFromPermissionCenter(Long tenantId, Long permResourceId) {
        if (permResourceId == null) {
            return true; // 未同步过，视为成功
        }

        IdsReq req = new IdsReq(List.of(permResourceId));
        PermResult<Void> result = permissionFeignClient.deleteResources(req);
        if (result == null || result.code() != 200) {
            log.warn("Failed to delete org from permission-center: permResourceId={}", permResourceId);
            return false;
        }

        log.info("Deleted org from permission-center: permResourceId={}", permResourceId);
        return true;
    }

    @Override
    public String generateResourceCode(SysOrg org) {
        // 使用组织编码作为资源编码
        return org.getCode() != null ? org.getCode() : "ORG_" + org.getId();
    }

    private String buildOrgExtra(SysOrg org) {
        // 将额外信息打包为 JSON 字符串
        return String.format("{\"orgType\":\"%s\",\"level\":%d,\"leaderId\":%s}",
            org.getOrgType() != null ? org.getOrgType() : "",
            org.getLevel() != null ? org.getLevel() : 0,
            org.getLeaderId() != null ? org.getLeaderId().toString() : "null"
        );
    }
}