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

/**
 * 组织同步处理器实现类
 * <p>
 * 将admin-service的组织同步到permission-center的resource_entity表。
 * 使用组织编码作为资源编码。
 * </p>
 */
@Service
public class OrgSyncHandlerImpl implements OrgSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(OrgSyncHandlerImpl.class);
    private static final String RESOURCE_TYPE_ORG = "ORG";

    private final PermissionFeignClient permissionFeignClient;

    /**
     * 构造函数
     *
     * @param permissionFeignClient 权限中心Feign客户端
     */
    public OrgSyncHandlerImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    /**
     * 同步组织到权限中心
     * <p>
     * 在权限中心创建组织资源实体（资源类型ORG）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param org      组织实体
     * @return permission-center的resource_entity.id，失败返回null
     */
    @Override
    public Long syncOrgToPermissionCenter(Long tenantId, SysOrg org) {
        if (org == null || org.getId() == null) {
            return null;
        }

        String code = generateResourceCode(org);
        ResourceCreateReq req = new ResourceCreateReq(
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
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            log.warn("同步组织到权限中心失败: orgId={}, orgName={}",
                org.getId(), org.getName());
            return null;
        }

        Object idObj = result.getData().get("id");
        if (idObj != null) {
            Long permResourceId = Long.valueOf(idObj.toString());
            log.info("同步组织到权限中心成功: orgId={}, permResourceId={}",
                org.getId(), permResourceId);
            return permResourceId;
        }
        return null;
    }

    /**
     * 批量同步组织到权限中心
     *
     * @param tenantId 租户ID
     * @param orgs     组织列表
     * @return 同步成功数量
     */
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

    /**
     * 从权限中心删除组织资源
     * <p>
     * 通过Feign调用权限中心删除指定的资源实体。
     * </p>
     *
     * @param tenantId       租户ID
     * @param permResourceId 权限中心的资源ID
     * @return 是否成功
     */
    @Override
    public boolean deleteOrgFromPermissionCenter(Long tenantId, Long permResourceId) {
        if (permResourceId == null) {
            return true; // 未同步过，视为成功
        }

        IdsReq req = new IdsReq(List.of(permResourceId));
        PermResult<Void> result = permissionFeignClient.deleteResources(req);
        if (result == null || result.getCode() != 200) {
            log.warn("从权限中心删除组织失败: permResourceId={}", permResourceId);
            return false;
        }

        log.info("从权限中心删除组织成功: permResourceId={}", permResourceId);
        return true;
    }

    /**
     * 生成组织资源编码
     * <p>
     * 使用组织编码作为资源编码，如果没有编码则使用ID生成。
     * </p>
     *
     * @param org 组织实体
     * @return 资源编码
     */
    @Override
    public String generateResourceCode(SysOrg org) {
        return org.getCode() != null ? org.getCode() : "ORG_" + org.getId();
    }

    /**
     * 构建组织扩展信息JSON
     * <p>
     * 将组织的额外信息打包为JSON字符串格式。
     * </p>
     *
     * @param org 组织实体
     * @return JSON字符串
     */
    private String buildOrgExtra(SysOrg org) {
        return String.format("{\"orgType\":\"%s\",\"level\":%d,\"leaderId\":%s}",
            org.getOrgType() != null ? org.getOrgType() : "",
            org.getLevel() != null ? org.getLevel() : 0,
            org.getLeaderId() != null ? org.getLeaderId().toString() : "null"
        );
    }
}