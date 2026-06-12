package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.domain.OrgSyncHandler;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.ResourceResp;

/**
 * 组织同步处理器实现类
 * <p>
 * 将admin-service的组织同步到permission-center的resource_entity表。
 * 使用组织编码作为资源编码。
 * 设计约束：组织/岗位需要双同步：
 * 1) resource_entity(ADMIN_ORG, code=sys_org.id)，用于组织实例级管理权限；
 * 2) abstract_role(ORG/POSITION, externalId=sys_org.id)，用于组织/岗位角色容器。
 * 均使用业务键定位，不存 permission-center 内部 ID。
 * 当前实现仍是旧口径，后续必须按 default-org-tree-user-lifecycle.md 调整。
 * </p>
 */
@Service
public class OrgSyncHandlerImpl implements OrgSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(OrgSyncHandlerImpl.class);
    private static final String RESOURCE_TYPE_ORG = AdminResourceType.ORG;

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
     * 注意：父节点通过业务键（resourceTypeCode=ADMIN_ORG + resourceCode=父sys_org.id）
     * 定位，permission-center 内部解析为 parentId。当前实现保留旧行为，
     * 仅标记待改造点。
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
        Long parentOrgId = org.getParentId();
        boolean hasParent = parentOrgId != null && parentOrgId != 0L;
        ResourceCreateReq req = new ResourceCreateReq(
            null,
            hasParent ? RESOURCE_TYPE_ORG : null,
            hasParent ? String.valueOf(parentOrgId) : null,
            null,
            null,
            RESOURCE_TYPE_ORG,
            code,
            null, // codeType
            org.getName(),
            null, // path
            org.getStatus() != null ? org.getStatus() : 1,
            org.getSortOrder() != null ? org.getSortOrder() : 0,
            buildOrgExtra(org)
        );

        PermResult<ResourceResp> result = permissionFeignClient.createResource(req);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            log.warn("同步组织到权限中心失败: orgId={}, orgName={}",
                org.getId(), org.getName());
            return null;
        }

        Long permResourceId = result.getData().id();
        log.info("同步组织到权限中心成功: orgId={}, permResourceId={}",
            org.getId(), permResourceId);
        return permResourceId;
    }

    /**
     * 生成组织资源编码
     * <p>
     * 使用组织编码作为资源编码，如果没有编码则使用ID生成。
     * 目标设计要求 ADMIN_ORG resourceCode 固定为 sys_org.id 字符串，
     * 与 AdminPermissionValidator 的业务键调用保持一致。
     * </p>
     *
     * @param org 组织实体
     * @return 资源编码
     */
    @Override
    public String generateResourceCode(SysOrg org) {
        return String.valueOf(org.getId());
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
