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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;

/**
 * 组织管理服务实现类
 * <p>
 * 提供组织的CRUD操作、树形查询、批量操作等功能。
 * 实现跨服务数据同步机制，通过Outbox Pattern确保组织创建与同步任务记录原子性。
 * 支持组织层级深度限制（最多10级）、组织编码唯一性校验。
 * 使用OrgDomainService处理组织数据查询和批量操作。
 * 批量删除时自动删除所有子组织。
 * </p>
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceImpl.class);

    private final SysOrgMapper orgMapper;
    private final OrgDomainService orgDomainService;
    private final OrgSyncHandler orgSyncHandler;
    private final AdminPermissionValidator permissionValidator;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param orgMapper 组织数据访问Mapper
     * @param orgDomainService 组织领域服务，处理组织数据查询和批量操作
     * @param orgSyncHandler 组织同步处理器，同步组织数据到permission-center
     * @param permissionValidator 权限校验器，校验组织操作权限
     * @param syncRetryService 同步重试服务，记录同步失败任务
     * @param objectMapper JSON序列化工具
     */
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

    /**
     * 创建组织
     * <p>
     * 创建新组织，校验编码唯一性和组织层级深度（不超过10级）。
     * 创建成功后记录同步任务，异步同步到permission-center（Outbox Pattern）。
     * 执行类型级权限校验(CREATE)。
     * </p>
     *
     * @param req 组织创建请求，包含组织名称、编码、类型、父组织ID等
     * @return 新组织ID
     * @throws BizException 组织编码已存在、组织层级超限、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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
            Long parentId = req.parentOrgId();
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
        org.setParentId(req.parentOrgId() != null ? req.parentOrgId() : 0L);
        org.setOrgType(String.valueOf(req.orgType()));
        org.setCode(req.code());
        org.setName(req.orgName());
        org.setStatus(req.status() != null ? req.status() : 1);
        org.setSortOrder(req.sort());
        org.setLevel(level);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        org.setDeleteFlag(0L);
        // 事务内：插入组织 + 记录同步任务（原子性，Outbox Pattern）
        orgMapper.insert(org);

        // 同一事务内记录同步任务，确保组织创建与任务记录原子性
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "orgId", org.getId(),
                "orgName", org.getName(),
                "tenantId", tenantId
            ));
            syncRetryService.recordSyncFailure(
                "org:create:" + org.getId(),
                "permission-center",
                "abstract_org",
                String.valueOf(org.getId()),
                "create",
                payload,
                null
            );
            log.info("Recorded sync task for org creation: orgId={}", org.getId());
        } catch (Exception e) {
            log.error("Failed to serialize sync payload for org creation: orgId={}", org.getId(), e);
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "组织同步任务记录失败");
        }

        return org.getId();
    }

    /**
     * 更新组织
     * <p>
     * 更新组织的名称、编码、父组织、状态等属性。
     * 执行实例级权限校验(UPDATE)。
     * 校验编码唯一性和组织层级深度。
     * 如果组织已同步到permission-center，记录更新同步任务（Outbox Pattern）。
     * </p>
     *
     * @param req 组织更新请求，包含组织ID和新属性值
     * @throws BizException 组织不存在、编码已存在、层级超限、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // Validate parent change only if a new parent is specified
        if (req.parentOrgId() != null) {
            long newParentId = req.parentOrgId();
            if (newParentId != org.getParentId()) {
                SysOrg newParent = orgDomainService.selectValidById(tenantId, newParentId);
                int newLevel = newParent != null ? (newParent.getLevel() != null ? newParent.getLevel() + 1 : 1) : 1;
                if (newLevel > 10) {
                    throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
                }
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
        org.setParentId(req.parentOrgId() != null ? req.parentOrgId() : org.getParentId());
        org.setCode(req.code());
        org.setStatus(req.status());
        org.setUpdatedAt(LocalDateTime.now());
        orgMapper.update(org);

        // 同步更新到权限中心 - 同一事务内记录同步任务（Outbox Pattern）
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
                log.error("Failed to serialize sync payload for org update: orgId={}", org.getId(), e);
                throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "组织同步任务记录失败");
            }
        }
    }

    /**
     * 删除组织
     * <p>
     * 软删除组织，不允许删除有子组织的组织。
     * 执行实例级权限校验(DELETE)。
     * 先本地软删除再记录同步任务（Outbox Pattern）。
     * </p>
     *
     * @param id 组织ID
     * @throws BizException 组织不存在、有子组织、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 事务内：软删除组织 + 记录同步任务（原子性，Outbox Pattern）
        orgDomainService.softDeleteBatch(tenantId, List.of(id));

        // 同一事务内记录同步任务，确保删除与任务记录原子性
        syncRetryService.recordSyncFailure(
            "org:delete:" + id,
            "permission-center",
            "abstract_org",
            String.valueOf(id),
            "delete",
            null,
            null
        );
        log.info("Recorded delete sync task for org: orgId={}", id);
    }

    /**
     * 获取组织详情
     * <p>
     * 根据组织ID查询组织完整信息。
     * </p>
     *
     * @param id 组织ID
     * @return 组织详情响应
     * @throws BizException 组织不存在
     */
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

    /**
     * 分页查询组织列表
     * <p>
     * 支持按组织名称、类型、状态过滤。
     * 按排序字段和创建时间排序。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页组织列表结果
     */
    @Override
    public PaginatedResult<OrgResp> pageOrgs(OrgPageReq req) {
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

    /**
     * 查询组织树
     * <p>
     * 获取当前租户的所有组织，构建树形结构返回。
     * 支持按组织类型和状态过滤。
     * 按排序字段和创建时间排序。
     * </p>
     *
     * @param query 组织查询条件，可选
     * @return 组织树列表
     */
    @Override
    public List<OrgResp> treeOrgs(OrgQuery query) {
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

    /**
     * 批量创建组织
     * <p>
     * 批量创建多个组织，校验编码唯一性和组织层级深度。
     * 使用批量查询检查编码和父组织（优化性能）。
     * 返回部分成功结果，包含成功ID列表和失败消息列表。
     * 创建成功后记录同步任务（Outbox Pattern）。
     * </p>
     *
     * @param req 批量创建请求，包含多个组织创建请求
     * @return 批量操作结果，包含成功ID列表和失败消息列表
     * @throws BizException 同步任务记录失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchResultResp batchCreateOrgs(OrgBatchCreateReq req) {
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
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // 批量查询：1次查询编码 + 1次查询父组织（优化前需要 N 次查询）
        Set<String> existingCodes = orgDomainService.findExistingCodes(tenantId, allCodes);
        Map<Long, SysOrg> parentOrgMap = orgDomainService.batchSelectValidByIdsMap(tenantId, allParentIds);

        // 构建待插入的组织列表
        List<SysOrg> orgsToInsert = new ArrayList<>();
        List<OrgCreateReq> validOrgReqs = new ArrayList<>();  // 记录有效的请求，用于后续同步任务
        LocalDateTime now = LocalDateTime.now();

        for (OrgCreateReq orgReq : req.orgs()) {
            // 检查编码重复（使用批量查询结果）
            if (orgReq.code() != null && !orgReq.code().isBlank() && existingCodes.contains(orgReq.code())) {
                failedMessages.add("组织编码已存在: " + orgReq.code());
                continue;
            }

            // 检查父组织并计算层级
            int level = 1;
            Long parentId = 0L;
            if (orgReq.parentOrgId() != null) {
                Long parsedParentId = orgReq.parentOrgId();
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

            // 记录同步任务（Outbox Pattern：确保原子性）
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
                        "abstract_org",
                        String.valueOf(org.getId()),
                        "create",
                        payload,
                        null
                    );
                    successIds.add(org.getId());
                } catch (Exception syncEx) {
                    log.error("Failed to record sync task for batch org creation: orgId={}, error={}",
                        org.getId(), syncEx.getMessage());
                    failedMessages.add("同步任务记录失败: " + org.getName());
                    throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "组织同步任务记录失败");
                }
            }
        }

        return BatchResultResp.partial(req.orgs().size(), successIds.size(), successIds, failedMessages);
    }

    /**
     * 批量删除组织
     * <p>
     * 批量软删除组织及其所有子组织。
     * 执行批量实例级权限校验，使用批量查询获取子组织ID。
     * 先本地软删除再记录同步任务（Outbox Pattern）。
     * </p>
     *
     * @param req ID集合请求，包含待删除的组织ID列表
     * @throws BizException 同步任务记录失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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
        }

        // 事务内：批量软删除 + 记录同步任务（原子性，Outbox Pattern）
        if (!allIdsToDelete.isEmpty()) {
            orgDomainService.softDeleteBatch(tenantId, List.copyOf(allIdsToDelete));
        }

        // 同一事务内记录同步任务，确保删除与任务记录原子性
        for (SysOrg org : orgs) {
            syncRetryService.recordSyncFailure(
                "org:delete:" + org.getId(),
                "permission-center",
                "abstract_org",
                String.valueOf(org.getId()),
                "delete",
                null,
                null
            );
            log.info("Recorded delete sync task for org: orgId={}", org.getId());
        }
    }

    /**
     * 获取组织所有子组织ID（包含自身）
     * <p>
     * 递归查询组织的所有后代组织ID，用于级联删除等操作。
     * </p>
     *
     * @param orgId 组织ID
     * @return 子组织ID列表（包含自身）
     */
    @Override
    public List<Long> getDescendantOrgIds(Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return orgDomainService.getDescendantIdsIncludingSelf(tenantId, orgId);
    }

    /**
     * 将组织实体转换为响应对象
     * <p>
     * 转换组织实体为API响应格式，包含子组织列表。
     * </p>
     *
     * @param org 组织实体
     * @param children 子组织响应列表
     * @return 组织响应对象
     */
    private OrgResp toResp(SysOrg org, List<OrgResp> children) {
        return new OrgResp(
            org.getId(), Integer.parseInt(org.getOrgType()), org.getName(),
            org.getParentId(), org.getCode(), null, null,
            org.getStatus(), org.getSortOrder(), org.getCreatedAt(), org.getUpdatedAt(), children
        );
    }

    /**
     * 构建组织树
     * <p>
     * 将组织列表转换为树形结构，递归构建子组织。
     * </p>
     *
     * @param all 所有组织列表
     * @param parentId 当前层级父组织ID（0表示根级）
     * @return 组织树列表
     */
    private List<OrgResp> buildTree(List<SysOrg> all, Long parentId) {
        return all.stream()
            .filter(o -> parentId.equals(o.getParentId()))
            .map(o -> new OrgResp(
                o.getId(), Integer.parseInt(o.getOrgType()), o.getName(),
                o.getParentId(), o.getCode(), null, null,
                o.getStatus(), o.getSortOrder(), o.getCreatedAt(), o.getUpdatedAt(),
                buildTree(all, o.getId())
            ))
            .collect(Collectors.toList());
    }
}