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
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgSyncHandler;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;

@Service
public class OrgServiceImpl implements OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceImpl.class);

    private final SysOrgMapper orgMapper;
    private final OrgDomainService orgDomainService;
    private final OrgSyncHandler orgSyncHandler;
    private final AdminPermissionValidator permissionValidator;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;

    public OrgServiceImpl(SysOrgMapper orgMapper, OrgDomainService orgDomainService,
                          OrgSyncHandler orgSyncHandler, AdminPermissionValidator permissionValidator,
                          SyncRetryService syncRetryService, ObjectMapper objectMapper) {
        this.orgMapper = orgMapper;
        this.orgDomainService = orgDomainService;
        this.orgSyncHandler = orgSyncHandler;
        this.permissionValidator = permissionValidator;
        this.syncRetryService = syncRetryService;
        this.objectMapper = objectMapper;
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

        // TODO: 跨服务数据一致性改进
        // 当前采用"记录同步任务"模式，本地事务提交后异步同步
        // 建议：完整方案应使用消息队列 + 补偿机制，参见 plan/architecture.md 分布式事务章节
        // 优先级：P1（架构债务）

        // 记录同步任务，异步同步到权限中心
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "orgId", org.getId(),
                "orgName", org.getName(),
                "tenantId", tenantId
            ));
            syncRetryService.recordSyncFailure(
                "org:create:" + org.getId(),
                "permission-center",
                "abstract_user",
                String.valueOf(org.getId()),
                "create",
                payload,
                null  // 不记录错误，只是记录待同步任务
            );
            log.info("Recorded sync task for org creation: orgId={}", org.getId());
        } catch (Exception e) {
            log.error("Failed to record sync task for org creation: orgId={}, error={}", org.getId(), e.getMessage());
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

        // TODO: 跨服务数据一致性改进 - 更新操作改为异步同步
        // 同步更新到权限中心 - 记录同步任务
        if (org.getPermOrgId() != null) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                    "permOrgId", org.getPermOrgId(),
                    "name", org.getName(),
                    "code", org.getCode(),
                    "parentId", org.getParentId(),
                    "level", org.getLevel(),
                    "sortOrder", org.getSortOrder()
                ));
                syncRetryService.recordSyncFailure(
                    "org:update:" + org.getId(),
                    "permission-center",
                    "abstract_org",
                    String.valueOf(org.getPermOrgId()),
                    "update",
                    payload,
                    null
                );
                log.info("Recorded update sync task for org: orgId={}", org.getId());
            } catch (Exception e) {
                log.error("Failed to record update sync task for org: orgId={}", org.getId(), e);
            }
        }
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

        // TODO: 跨服务数据一致性改进
        // 当前采用"先本地软删除，后记录同步任务"模式，确保本地数据优先删除
        // 建议：完整方案应使用消息队列 + 补偿机制，参见 plan/architecture.md 分布式事务章节
        // 优先级：P1（架构债务）

        // 1. 先执行本地软删除
        orgDomainService.softDeleteBatch(tenantId, List.of(id));

        // 2. 记录删除同步任务
        try {
            syncRetryService.recordSyncFailure(
                "org:delete:" + id,
                "permission-center",
                "abstract_user",
                String.valueOf(id),
                "delete",
                null,
                null
            );
            log.info("Recorded delete sync task for org: orgId={}", id);
        } catch (Exception e) {
            log.error("Failed to record delete sync task for org: orgId={}, error={}", id, e.getMessage());
        }
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
            .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
            .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0));
        if (req.orgName() != null) qw.and(SysOrgTableDef.SYS_ORG.NAME.like(req.orgName()));
        if (req.orgType() != null) qw.and(SysOrgTableDef.SYS_ORG.ORG_TYPE.eq(String.valueOf(req.orgType())));
        if (req.status() != null) qw.and(SysOrgTableDef.SYS_ORG.STATUS.eq(req.status()));
        qw.orderBy(SysOrgTableDef.SYS_ORG.SORT_ORDER.asc(), SysOrgTableDef.SYS_ORG.CREATED_AT.asc());

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
            .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
            .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0));
        if (query != null) {
            if (query.orgType() != null) qw.and(SysOrgTableDef.SYS_ORG.ORG_TYPE.eq(String.valueOf(query.orgType())));
            if (query.status() != null) qw.and(SysOrgTableDef.SYS_ORG.STATUS.eq(query.status()));
        }
        qw.orderBy(SysOrgTableDef.SYS_ORG.SORT_ORDER.asc(), SysOrgTableDef.SYS_ORG.CREATED_AT.asc());

        List<SysOrg> all = orgMapper.selectListByQuery(qw);
        return buildTree(all, 0L);
    }

    @Override
    @Transactional
    public BatchResultResp batchCreateOrgs(OrgBatchCreateReq req) {
        // TODO: 跨服务数据一致性风险
        // 问题：本地事务与远程 Feign 调用无法协调，可能导致数据不一致
        // 建议：采用"本地事务 + 异步同步 + 补偿机制"模式
        // 参考：plan/architecture.md 分布式事务章节
        // 优先级：P1（架构债务）

        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.ORG, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> successIds = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        // 批量优化：收集所有需要查询的编码和父组织ID
        Set<String> allCodes = req.orgs().stream()
            .map(OrgCreateReq::code)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());
        Set<Long> allParentIds = req.orgs().stream()
            .map(OrgCreateReq::parentOrgId)
            .filter(id -> id != null && !id.isBlank())
            .map(Long::parseLong)
            .collect(Collectors.toSet());

        // 批量查询：1次查询编码 + 1次查询父组织（优化前需要 N 次查询）
        Set<String> existingCodes = orgDomainService.findExistingCodes(tenantId, allCodes);
        Map<Long, SysOrg> parentOrgMap = orgDomainService.batchSelectValidByIdsMap(tenantId, allParentIds);

        // 构建待插入的组织列表
        List<SysOrg> orgsToInsert = new ArrayList<>();
        List<OrgCreateReq> validOrgReqs = new ArrayList<>();  // 记录有效的请求，用于后续同步任务
        LocalDateTime now = LocalDateTime.now();

        for (OrgCreateReq orgReq : req.orgs()) {
            // 检查编码重复
            if (orgReq.code() != null && !orgReq.code().isBlank() && existingCodes.contains(orgReq.code())) {
                failedMessages.add("组织编码已存在: " + orgReq.code());
                continue;
            }

            // 检查父组织并计算层级
            int level = 1;
            Long parentId = 0L;
            if (orgReq.parentOrgId() != null && !orgReq.parentOrgId().isBlank()) {
                Long parsedParentId = Long.parseLong(orgReq.parentOrgId());
                SysOrg parent = parentOrgMap.get(parsedParentId);
                if (parent == null) {
                    failedMessages.add("父组织不存在: " + orgReq.parentOrgId());
                    continue;
                }
                level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;
                parentId = parent.getId();
            }
            if (level > 10) {
                failedMessages.add("组织层级超过限制: " + orgReq.orgName());
                continue;
            }

            // 构建组织实体
            SysOrg org = new SysOrg();
            org.setTenantId(tenantId);
            org.setParentId(parentId);
            org.setOrgType(String.valueOf(orgReq.orgType()));
            org.setCode(orgReq.code());
            org.setName(orgReq.orgName());
            org.setStatus(orgReq.status() != null ? orgReq.status() : 1);
            org.setSortOrder(orgReq.sort());
            org.setLevel(level);
            org.setCreatedAt(now);
            org.setUpdatedAt(now);
            org.setDeleteFlag(0L);
            orgsToInsert.add(org);
            validOrgReqs.add(orgReq);
        }

        // 批量插入：1次数据库操作（优化前需要 N 次插入）
        if (!orgsToInsert.isEmpty()) {
            orgDomainService.insertBatch(orgsToInsert);

            // 记录同步任务（保持原有逻辑）
            for (int i = 0; i < orgsToInsert.size(); i++) {
                SysOrg org = orgsToInsert.get(i);
                try {
                    String payload = objectMapper.writeValueAsString(Map.of(
                        "orgId", org.getId(),
                        "orgName", org.getName(),
                        "tenantId", tenantId
                    ));
                    syncRetryService.recordSyncFailure(
                        "org:create:" + org.getId(),
                        "permission-center",
                        "abstract_user",
                        String.valueOf(org.getId()),
                        "create",
                        payload,
                        null
                    );
                } catch (Exception syncEx) {
                    log.error("Failed to record sync task for batch org creation: orgId={}, error={}",
                        org.getId(), syncEx.getMessage());
                }
                successIds.add(org.getId());
            }
        }

        return BatchResultResp.partial(req.orgs().size(), successIds.size(), successIds, failedMessages);
    }

    @Override
    @Transactional
    public void batchDeleteOrgs(IdsReq req) {
        // TODO: 跨服务数据一致性改进
        // 当前采用"先本地软删除，后记录同步任务"模式，确保本地数据优先删除
        // 建议：完整方案应使用消息队列 + 补偿机制，参见 plan/architecture.md 分布式事务章节
        // 优先级：P1（架构债务）

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
        }

        // 1. 先执行本地批量软删除
        if (!allIdsToDelete.isEmpty()) {
            orgDomainService.softDeleteBatch(tenantId, List.copyOf(allIdsToDelete));
        }

        // 2. 记录删除同步任务
        for (SysOrg org : orgs) {
            try {
                syncRetryService.recordSyncFailure(
                    "org:delete:" + org.getId(),
                    "permission-center",
                    "abstract_user",
                    String.valueOf(org.getId()),
                    "delete",
                    null,
                    null
                );
                log.info("Recorded delete sync task for org: orgId={}", org.getId());
            } catch (Exception e) {
                log.error("Failed to record delete sync task for org: orgId={}, error={}", org.getId(), e.getMessage());
            }
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