package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgUserItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.security.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.admin.service.OrgService;
import cn.ac.fage.accessmesh.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Org management service implementation.
 * <p>
 * Provides CRUD and tree query for orgs/positions. Cross-service sync uses Outbox pattern:
 * each org change writes 2 sync envelopes (ABSTRACT_ROLE + ADMIN_ORG resource_entity) into
 * sys_sync_task within the same business transaction. Both use business keys, no internal IDs.
 *
 * @implNote v1.4 起所有读接口（{@link #getOrg}、{@link #pageOrgs}、{@link #treeOrgs}、{@link #listOrgUsers}）
 *           必须经过 {@code permissionValidator} 门禁；按 orgType 分发 VIEW / VIEW_POSITION，
 *           普通组织成员列表归属 USER:VIEW。前端隐藏不是安全边界，禁止在新增读接口时省略。
 *           契约依据：{@code docs/design/org-user-permission-contract.md} v1.4 §4。
 * </p>
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceImpl.class);

    private final SysOrgMapper orgMapper;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final SyncTaskDomainService syncTaskDomainService;
    private final SyncTaskBuilder syncTaskBuilder;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;

    public OrgServiceImpl(SysOrgMapper orgMapper, OrgDomainService orgDomainService,
                          AdminPermissionValidator permissionValidator,
                          SyncTaskDomainService syncTaskDomainService,
                          SyncTaskBuilder syncTaskBuilder,
                          SysUserOrgMapper userOrgMapper,
                          UserDomainService userDomainService) {
        this.orgMapper = orgMapper;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.syncTaskDomainService = syncTaskDomainService;
        this.syncTaskBuilder = syncTaskBuilder;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrg(OrgCreateReq req) {
        // 按 orgType 分发操作码（声明式映射，见 OrgOperationCodeMapper）
        String orgType = req.orgType() != null ? String.valueOf(req.orgType()) : null;
        permissionValidator.checkTypeLevel(
            AdminResourceType.ORG,
            OrgOperationCodeMapper.resolve(orgType, AdminOperationCode.CREATE)
        );

        Long tenantId = TenantContextHolder.getTenantId();

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

        orgMapper.insert(org);

        // Outbox: enqueue abstract_role + ADMIN_ORG resource_entity envelopes within same tx
        syncTaskDomainService.enqueueAll(tenantId, syncTaskBuilder.orgUpsert(org));
        log.info("Enqueued org upsert sync envelopes: orgId={}", org.getId());

        return org.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrg(OrgUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 先加载实例确定 orgType，再按类型分发操作码（声明式映射，见 OrgOperationCodeMapper）
        SysOrg org = orgDomainService.selectValidById(tenantId, req.id());
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(req.id()),
            OrgOperationCodeMapper.resolve(org.getOrgType(), AdminOperationCode.UPDATE)
        );

        // 注：OrgUpdateReq 不含 orgType 字段，orgType 由 API 契约保证不可变（普通组织/岗位互转），
        // 操作码门禁始终基于已存实例类型 org.getOrgType() 决策。

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

        // Outbox: enqueue abstract_role + ADMIN_ORG resource_entity envelopes within same tx
        if (org.getId() != null) {
            syncTaskDomainService.enqueueAll(tenantId, syncTaskBuilder.orgUpsert(org));
            log.info("Enqueued org update sync envelopes: orgId={}", org.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrg(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 先加载快照确定 orgType，再按类型分发操作码（声明式映射，见 OrgOperationCodeMapper）
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(id),
            OrgOperationCodeMapper.resolve(org.getOrgType(), AdminOperationCode.DELETE)
        );

        if (orgDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.ORG_HAS_CHILDREN.getCode(), AdminErrorCode.ORG_HAS_CHILDREN.getMessage());
        }

        orgDomainService.softDeleteBatch(tenantId, List.of(id));

        // Outbox: enqueue abstract_role DELETE + ADMIN_ORG resource_entity DELETE envelopes
        // 传入删除前快照 orgType，确保 abstract_role business_key 与创建时一致（POSITION 类型 org 删除路径不再错配为 ORG）
        syncTaskDomainService.enqueueAll(tenantId, syncTaskBuilder.orgDelete(id, String.valueOf(id), org.getOrgType()));
        log.info("Enqueued org delete sync envelopes: orgId={}", id);
    }

    @Override
    public OrgResp getOrg(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 先加载实例确定 orgType（不存在直接抛 NotFound，避免门禁前的存在性泄漏：
        // 若先门禁后查存在，无 VIEW 权和不存在两种场景返回不同语义，可被用作存在性探测）
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        // D1=A 实例级 VIEW，按 orgType 分发到 VIEW / VIEW_POSITION
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(id),
            OrgOperationCodeMapper.resolve(org.getOrgType(), AdminOperationCode.VIEW)
        );

        return toResp(org, List.of());
    }

    @Override
    public PaginatedResult<OrgResp> pageOrgs(OrgPageReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        // D2=B 强制要求显式 orgType，否则无法分发 VIEW / VIEW_POSITION 做细粒度门控
        if (req.orgType() == null) {
            throw new BizException(AdminErrorCode.ORG_TYPE_REQUIRED.getCode(), AdminErrorCode.ORG_TYPE_REQUIRED.getMessage());
        }
        String orgType = String.valueOf(req.orgType());

        // 类型级 VIEW，按 orgType 分发
        permissionValidator.checkTypeLevel(
            AdminResourceType.ORG,
            OrgOperationCodeMapper.resolve(orgType, AdminOperationCode.VIEW)
        );

        int pageNum = req.getPageNum();
        int pageSize = req.getPageSize();
        Set<Long> orgIds = null;
        if (req.orgId() != null) {
            List<Long> subtreeIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, req.orgId());
            if (subtreeIds.isEmpty()) {
                return new PaginatedResult<>(
                    List.of(),
                    new PaginatedResult.PaginationMeta(0, pageNum, pageSize, 0)
                );
            }
            orgIds = Set.copyOf(subtreeIds);
        }

        Page<SysOrg> result = orgMapper.paginateOrgs(Page.of(pageNum, pageSize), tenantId, req.orgName(), orgType, req.status(), orgIds);

        List<SysOrg> records = result.getRecords();

        List<OrgResp> items = records.stream()
            .map(o -> toResp(o, List.of()))
            .collect(Collectors.toList());

        long total = result.getTotalRow();
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(total, pageNum, pageSize, (int) totalPages));
    }

    @Override
    public List<OrgResp> treeOrgs(OrgQuery query) {
        Long tenantId = TenantContextHolder.getTenantId();
        // D2=B 强制要求显式 orgType，否则无法分发 VIEW / VIEW_POSITION 做细粒度门控
        if (query == null || query.orgType() == null) {
            throw new BizException(AdminErrorCode.ORG_TYPE_REQUIRED.getCode(), AdminErrorCode.ORG_TYPE_REQUIRED.getMessage());
        }
        String orgType = String.valueOf(query.orgType());
        Integer status = query.status();

        // 类型级 VIEW，按 orgType 分发
        permissionValidator.checkTypeLevel(
            AdminResourceType.ORG,
            OrgOperationCodeMapper.resolve(orgType, AdminOperationCode.VIEW)
        );

        List<SysOrg> all = orgMapper.selectOrgsForTree(tenantId, orgType, status);
        return buildTree(all, 0L);
    }

    @Override
    public List<OrgUserItemResp> listOrgUsers(Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        // D3=A 组织成员列表归属 USER:VIEW（契约 §4 B 区：「看成员 = 看用户」），
        // 与组织实例的 VIEW / VIEW_POSITION 解耦
        permissionValidator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.VIEW);

        cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef suo = cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;
        com.mybatisflex.core.query.QueryWrapper qw = com.mybatisflex.core.query.QueryWrapper.create()
            .where(suo.TENANT_ID.eq(tenantId))
            .where(suo.ORG_ID.eq(orgId))
            .where(suo.DELETE_FLAG.eq(0L));
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(qw);

        if (userOrgs.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = userOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userDomainService.selectValidByIds(tenantId, userIds).stream()
            .collect(Collectors.toMap(SysUser::getId, u -> u));

        return userOrgs.stream()
            .map(uo -> {
                SysUser user = userMap.get(uo.getUserId());
                if (user == null) return null;
                return new OrgUserItemResp(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    user.getAvatar(),
                    Boolean.TRUE.equals(uo.getIsPrimary())
                );
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    private OrgResp toResp(SysOrg org, List<OrgResp> children) {
        return new OrgResp(
            org.getId(), Integer.parseInt(org.getOrgType()), org.getName(),
            org.getParentId(), org.getCode(), null, null,
            org.getStatus(), org.getSortOrder(), org.getCreatedAt(), org.getUpdatedAt(), children
        );
    }

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

    /**
     * 判断 sys_org.orgType 是否为岗位类型。
     * <p>
     * 委托 {@link OrgOperationCodeMapper#isPositionOrg}。
     * </p>
     */
    private static boolean isPositionOrg(String orgType) {
        return OrgOperationCodeMapper.isPositionOrg(orgType);
    }
}
