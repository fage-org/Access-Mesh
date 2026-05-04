package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.OrgService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgSyncHandler;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef.SYS_ORG;

@Service
public class OrgServiceImpl implements OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceImpl.class);

    private final SysOrgMapper orgMapper;
    private final OrgDomainService orgDomainService;
    private final OrgSyncHandler orgSyncHandler;
    private final AdminPermissionValidator permissionValidator;

    public OrgServiceImpl(SysOrgMapper orgMapper, OrgDomainService orgDomainService,
                          OrgSyncHandler orgSyncHandler, AdminPermissionValidator permissionValidator) {
        this.orgMapper = orgMapper;
        this.orgDomainService = orgDomainService;
        this.orgSyncHandler = orgSyncHandler;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    public Long createOrg(OrgCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.ORG, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 检查编码重复
        SysOrg existing = orgDomainService.findByCode(tenantId, req.code());
        if (existing != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(), AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }

        int level = 1;
        if (req.parentOrgId() != null) {
            Long parentId = Long.parseLong(req.parentOrgId());
            SysOrg parent = orgDomainService.selectValidById(tenantId, parentId);
            if (parent != null) {
                level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;
            }
        }
        if (level > 10) {
            throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
        }

        SysOrg org = new SysOrg();
        org.setTenantId(tenantId);
        org.setParentId(req.parentOrgId() != null ? Long.parseLong(req.parentOrgId()) : 0L);
        org.setOrgType(String.valueOf(req.orgType()));
        org.setCode(req.code());
        org.setName(req.orgName());
        org.setStatus(req.status() != null ? req.status() : 1);
        org.setSortOrder(req.sort());
        org.setLevel(level);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        org.setDeleteFlag(0L);
        orgMapper.insert(org);

        // 同步到权限中心
        Long permResourceId = orgSyncHandler.syncOrgToPermissionCenter(tenantId, org);
        if (permResourceId != null) {
            org.setPermRoleId(permResourceId); // 复用 permRoleId 字段存储资源ID
            orgMapper.update(org);
            log.info("Org synced to permission-center: orgId={}, permResourceId={}", org.getId(), permResourceId);
        }

        return org.getId();
    }

    @Override
    @Transactional
    public void updateOrg(OrgUpdateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取组织
        SysOrg org = orgDomainService.selectValidById(tenantId, req.id());
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        long newParentId = Long.parseLong(req.parentOrgId());
        if (newParentId != org.getParentId()) {
            SysOrg newParent = orgDomainService.selectValidById(tenantId, newParentId);
            int newLevel = newParent != null ? (newParent.getLevel() != null ? newParent.getLevel() + 1 : 1) : 1;
            if (newLevel > 10) {
                throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
            }
        }

        // 如果修改了编码，检查新编码是否重复
        if (!req.code().equals(org.getCode())) {
            SysOrg codeExisting = orgDomainService.findByCode(tenantId, req.code());
            if (codeExisting != null) {
                throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(), AdminErrorCode.ORG_CODE_EXISTS.getMessage());
            }
        }

        org.setName(req.orgName());
        org.setParentId(req.parentOrgId() != null ? Long.parseLong(req.parentOrgId()) : org.getParentId());
        org.setCode(req.code());
        org.setStatus(req.status());
        org.setUpdatedAt(LocalDateTime.now());
        orgMapper.update(org);
    }

    @Override
    @Transactional
    public void deleteOrg(Long id) {
        // Permission check - instance-level DELETE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(id),
            AdminOperationCode.DELETE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取组织
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        // 使用 DomainService 检查是否有子组织
        if (orgDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.ORG_HAS_CHILDREN.getCode(), AdminErrorCode.ORG_HAS_CHILDREN.getMessage());
        }

        // 从权限中心删除
        if (org.getPermRoleId() != null) {
            orgSyncHandler.deleteOrgFromPermissionCenter(tenantId, org.getPermRoleId());
        }

        // 使用 DomainService 批量软删除（包含自身）
        orgDomainService.softDeleteBatch(tenantId, List.of(id));
    }

    @Override
    public OrgResp getOrg(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取组织
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        return toResp(org, List.of());
    }

    @Override
    public PaginatedResult<OrgResp> pageOrgs(OrgPageReq req) {
        // FIX #7: Add tenantId filter for security
        Long tenantId = TenantContextHolder.getTenantId();
        QueryWrapper qw = QueryWrapper.create()
            .where(SYS_ORG.TENANT_ID.eq(tenantId))
            .and(SYS_ORG.DELETE_FLAG.eq(0));
        if (req.orgName() != null) qw.and(SYS_ORG.NAME.like(req.orgName()));
        if (req.orgType() != null) qw.and(SYS_ORG.ORG_TYPE.eq(String.valueOf(req.orgType())));
        if (req.status() != null) qw.and(SYS_ORG.STATUS.eq(req.status()));
        qw.orderBy(SYS_ORG.SORT_ORDER.asc(), SYS_ORG.CREATED_AT.asc());

        Page<SysOrg> page = Page.of(req.getPageNum(), req.getPageSize());
        Page<SysOrg> result = orgMapper.paginate(page, qw);

        List<OrgResp> items = result.getRecords().stream()
            .map(o -> toResp(o, List.of()))
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + req.getPageSize() - 1) / req.getPageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), req.getPageNum(), req.getPageSize(), (int) totalPages));
    }

    @Override
    public List<OrgResp> treeOrgs(OrgQuery query) {
        // FIX #8: Add tenantId filter for security
        Long tenantId = TenantContextHolder.getTenantId();
        QueryWrapper qw = QueryWrapper.create()
            .where(SYS_ORG.TENANT_ID.eq(tenantId))
            .and(SYS_ORG.DELETE_FLAG.eq(0));
        if (query != null) {
            if (query.orgType() != null) qw.and(SYS_ORG.ORG_TYPE.eq(String.valueOf(query.orgType())));
            if (query.status() != null) qw.and(SYS_ORG.STATUS.eq(query.status()));
        }
        qw.orderBy(SYS_ORG.SORT_ORDER.asc(), SYS_ORG.CREATED_AT.asc());

        List<SysOrg> all = orgMapper.selectListByQuery(qw);
        return buildTree(all, 0L);
    }

    @Override
    @Transactional
    public BatchResultResp batchCreateOrgs(OrgBatchCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.ORG, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> successIds = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        for (OrgCreateReq orgReq : req.orgs()) {
            try {
                // 检查编码重复
                if (orgReq.code() != null && orgDomainService.findByCode(tenantId, orgReq.code()) != null) {
                    failedMessages.add("组织编码已存在: " + orgReq.code());
                    continue;
                }

                int level = 1;
                if (orgReq.parentOrgId() != null) {
                    Long parentId = Long.parseLong(orgReq.parentOrgId());
                    SysOrg parent = orgDomainService.selectValidById(tenantId, parentId);
                    if (parent != null) {
                        level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;
                    }
                }
                if (level > 10) {
                    failedMessages.add("组织层级超过限制: " + orgReq.orgName());
                    continue;
                }

                SysOrg org = new SysOrg();
                org.setTenantId(tenantId);
                org.setParentId(orgReq.parentOrgId() != null ? Long.parseLong(orgReq.parentOrgId()) : 0L);
                org.setOrgType(String.valueOf(orgReq.orgType()));
                org.setCode(orgReq.code());
                org.setName(orgReq.orgName());
                org.setStatus(orgReq.status() != null ? orgReq.status() : 1);
                org.setSortOrder(orgReq.sort());
                org.setLevel(level);
                org.setCreatedAt(LocalDateTime.now());
                org.setUpdatedAt(LocalDateTime.now());
                org.setDeleteFlag(0L);
                orgMapper.insert(org);

                // 同步到权限中心
                orgSyncHandler.syncOrgToPermissionCenter(tenantId, org);

                successIds.add(org.getId());
            } catch (Exception e) {
                log.error("Failed to create org: orgName={}", orgReq.orgName(), e);
                failedMessages.add("创建失败: " + orgReq.orgName() + " - " + e.getMessage());
            }
        }

        return BatchResultResp.partial(req.orgs().size(), successIds.size(), successIds, failedMessages);
    }

    @Override
    @Transactional
    public void batchDeleteOrgs(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.ORG, resourceCodes, AdminOperationCode.DELETE);

        Long tenantId = TenantContextHolder.getTenantId();

        // Performance fix: Batch load orgs and descendants
        Set<Long> orgIdSet = new java.util.HashSet<>(req.ids());
        List<SysOrg> orgs = orgDomainService.selectValidByIds(tenantId, orgIdSet);

        // Get all descendant IDs in batch
        Map<Long, List<Long>> descendantMap = orgDomainService.batchGetDescendantIds(tenantId, orgIdSet);

        // Collect all IDs to delete (including self)
        Set<Long> allIdsToDelete = new java.util.HashSet<>();
        for (SysOrg org : orgs) {
            Long orgId = org.getId();
            // Add self
            allIdsToDelete.add(orgId);
            // Add descendants
            List<Long> descendants = descendantMap.getOrDefault(orgId, List.of());
            allIdsToDelete.addAll(descendants);

            // Delete from permission center
            if (org.getPermRoleId() != null) {
                orgSyncHandler.deleteOrgFromPermissionCenter(tenantId, org.getPermRoleId());
            }
        }

        // Batch soft delete
        if (!allIdsToDelete.isEmpty()) {
            orgDomainService.softDeleteBatch(tenantId, List.copyOf(allIdsToDelete));
        }
    }

    @Override
    public List<Long> getDescendantOrgIds(Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return orgDomainService.getDescendantIdsIncludingSelf(tenantId, orgId);
    }

    private OrgResp toResp(SysOrg org, List<OrgResp> children) {
        return new OrgResp(
            org.getId(), Integer.parseInt(org.getOrgType()), org.getName(),
            String.valueOf(org.getParentId()), org.getCode(), null, null,
            org.getStatus(), org.getSortOrder(), org.getCreatedAt(), org.getUpdatedAt(), children
        );
    }

    private List<OrgResp> buildTree(List<SysOrg> all, Long parentId) {
        return all.stream()
            .filter(o -> parentId.equals(o.getParentId()))
            .map(o -> new OrgResp(
                o.getId(), Integer.parseInt(o.getOrgType()), o.getName(),
                String.valueOf(o.getParentId()), o.getCode(), null, null,
                o.getStatus(), o.getSortOrder(), o.getCreatedAt(), o.getUpdatedAt(),
                buildTree(all, o.getId())
            ))
            .collect(Collectors.toList());
    }
}