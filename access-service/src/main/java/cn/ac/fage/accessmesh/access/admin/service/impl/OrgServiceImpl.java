package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgUserItemResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.admin.security.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.admin.service.OrgService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.application.OrgWriteAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Org management service implementation.
 * <p>
 * Provides CRUD and tree query for orgs/positions. Writes delegate to
 * {@code OrgWriteAppService} which maintains the ORG/POSITION projection (abstract_role +
 * ORG resource_entity) in the same transaction, keyed by business keys.
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
    private final SysOrgTreeConfigMapper treeConfigMapper;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final OrgWriteAppService orgWriteAppService;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;

    public OrgServiceImpl(SysOrgMapper orgMapper, SysOrgTreeConfigMapper treeConfigMapper,
                          OrgDomainService orgDomainService,
                          AdminPermissionValidator permissionValidator,
                          OrgWriteAppService orgWriteAppService,
                          SysUserOrgMapper userOrgMapper,
                          UserDomainService userDomainService) {
        this.orgMapper = orgMapper;
        this.treeConfigMapper = treeConfigMapper;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.orgWriteAppService = orgWriteAppService;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
    }

    @Override
    public Long createOrg(OrgCreateReq req) {
        return orgWriteAppService.createOrg(req);
    }

    @Override
    public void updateOrg(OrgUpdateReq req) {
        orgWriteAppService.updateOrg(req);
    }

    @Override
    public void deleteOrg(Long id) {
        orgWriteAppService.deleteOrg(id);
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
            ResourceTypeCode.ORG,
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
            ResourceTypeCode.ORG,
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

        // XML 分页统一 offset/limit + count 双查询（MyBatis-Flex Page 参数在 XML 映射下不生效）
        long total = orgMapper.countOrgsByCondition(tenantId, req.orgName(), orgType, req.status(), orgIds);
        List<SysOrg> records = total == 0 ? List.of()
            : orgMapper.selectOrgsByCondition(tenantId, req.orgName(), orgType, req.status(), orgIds,
                (pageNum - 1) * pageSize, pageSize);

        List<OrgResp> items = records.stream()
            .map(o -> toResp(o, List.of()))
            .collect(Collectors.toList());
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(total, pageNum, pageSize, (int) totalPages));
    }

    @Override
    public List<OrgResp> treeOrgs(OrgQuery query) {
        // 控制器 @RequestBody 保证非 null，此处归一化防御直接调用（测试/内部复用）
        OrgQuery q = query == null
            ? new OrgQuery(null, null, null, null, null, null, null) : query;
        Long tenantId = TenantContextHolder.getTenantId();
        boolean mixed = Boolean.TRUE.equals(q.includePositions());
        String operationCode = q.operationCode() == null
            ? AdminOperationCode.VIEW : q.operationCode();
        boolean createSemantics = AdminOperationCode.CREATE.equals(operationCode);

        // 参数校验：非法 operationCode / 冲突组合 → 10008（枚举缝隙 fail-closed）
        if (!AdminOperationCode.VIEW.equals(operationCode) && !createSemantics) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "operationCode 仅支持 VIEW / CREATE，实际: " + operationCode);
        }
        if (mixed && createSemantics) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "includePositions=true 仅支持 VIEW（CREATE+混合树语义互斥，P2-1）");
        }
        if (createSemantics && q.treeConfigId() != null) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "operationCode=CREATE 限默认树，禁止传 treeConfigId");
        }

        // 门禁：混合树组织轨固定 ORG:VIEW，岗位轨独立 hasTypeLevel 后端裁剪（P2-1）；
        // 单类型按 orgType 分发（D2=B，OrgOperationCodeMapper 单一事实源）
        boolean includePositionNodes = false;
        if (mixed) {
            permissionValidator.checkTypeLevel(ResourceTypeCode.ORG, AdminOperationCode.VIEW);
            includePositionNodes = permissionValidator.hasTypeLevel(
                ResourceTypeCode.ORG, AdminOperationCode.VIEW_POSITION);
        } else {
            // D2=B 强制要求显式 orgType，否则无法分发 VIEW / VIEW_POSITION 做细粒度门控
            if (q.orgType() == null) {
                throw new BizException(AdminErrorCode.ORG_TYPE_REQUIRED.getCode(),
                    AdminErrorCode.ORG_TYPE_REQUIRED.getMessage());
            }
            permissionValidator.checkTypeLevel(
                ResourceTypeCode.ORG,
                OrgOperationCodeMapper.resolve(String.valueOf(q.orgType()), operationCode)
            );
        }

        // 树范围：CREATE 强制默认树（新增用户挂载点限默认树）；
        // 不传 treeConfigId = 默认树子树（契约字面，T-ADMIN-021 用户决策）
        SysOrgTreeConfig config = resolveTreeScope(tenantId, createSemantics ? null : q.treeConfigId());

        List<SysOrg> all = orgMapper.selectOrgsForTree(
            tenantId, mixed ? null : String.valueOf(q.orgType()), q.status());

        // 配置子树裁剪（内存祖先链判定；根组织缺失=配置漂移 fail-closed，禁止静默空树）
        Map<Long, SysOrg> byId = all.stream()
            .collect(Collectors.toMap(SysOrg::getId, o -> o, (a, b) -> a));
        SysOrg root = byId.get(config.getRootOrgId());
        if (root == null) {
            throw new BizException(AdminErrorCode.ORG_TREE_ROOT_NOT_RESOLVED.getCode(),
                "树配置根组织不存在或已删除: configId=" + config.getId() + ", rootOrgId=" + config.getRootOrgId());
        }
        List<SysOrg> scoped = scopeToSubtree(all, byId, config.getRootOrgId());

        // 岗位裁剪先于名称过滤：仅 ORG:VIEW 的调用者不返回任何岗位节点（前端隐藏不是安全边界）
        if (mixed && !includePositionNodes) {
            scoped = scoped.stream()
                .filter(o -> !isPositionOrg(o.getOrgType()))
                .collect(Collectors.toList());
        }

        // 名称剪枝：保留自身或后代命中的节点（修正原死参数——契约 orgName 模糊匹配）
        String keyword = q.orgName();
        if (keyword != null && !keyword.isBlank()) {
            scoped = pruneByName(scoped, byId, config.getRootOrgId(), keyword.trim());
        }

        // 顶层：parentOrgId 给定时取该节点子树（不在配置子树内=空结果，过滤语义）；否则配置根为单根
        Long topId = q.parentOrgId() != null ? q.parentOrgId() : config.getRootOrgId();
        Map<Long, SysOrg> scopedById = scoped.stream()
            .collect(Collectors.toMap(SysOrg::getId, o -> o, (a, b) -> a));
        SysOrg top = scopedById.get(topId);
        if (top == null) {
            return List.of();
        }
        return List.of(toResp(top, buildTree(scoped, top.getId())));
    }

    /**
     * 解析树范围配置：treeConfigId 非空按 id 查（缺失 11001）；否则取默认树（无默认 11001——
     * fail-closed，对齐 OrgTreeConfigDomainService「禁止 fallback 静默默认值」既有口径）。
     */
    private SysOrgTreeConfig resolveTreeScope(Long tenantId, Long treeConfigId) {
        if (treeConfigId != null) {
            SysOrgTreeConfig config = treeConfigMapper.selectByIdSafe(tenantId, treeConfigId);
            if (config == null) {
                throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                    "组织树配置不存在: " + treeConfigId);
            }
            return config;
        }
        List<SysOrgTreeConfig> defaults = treeConfigMapper.selectDefaultConfigs(tenantId);
        if (defaults.isEmpty()) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                "默认组织树配置不存在（treeConfigId 未传时按默认树裁剪）");
        }
        return defaults.get(0);
    }

    /**
     * 内存祖先链裁剪：保留自身或祖先链命中 rootId 的节点（游离节点父链断裂自然排除）。
     * 步数上限防御异常父环（move 并发成环窗口统一加固归 T-PERM-044，此处仅防死循环）。
     */
    private static List<SysOrg> scopeToSubtree(List<SysOrg> all, Map<Long, SysOrg> byId, Long rootId) {
        int maxDepth = all.size() + 1;
        List<SysOrg> result = new ArrayList<>();
        for (SysOrg o : all) {
            Long cur = o.getId();
            for (int step = 0; cur != null && step <= maxDepth; step++) {
                if (cur.equals(rootId)) {
                    result.add(o);
                    break;
                }
                SysOrg node = byId.get(cur);
                cur = node == null ? null : node.getParentId();
            }
        }
        return result;
    }

    /**
     * 名称剪枝：仅保留「自身名称命中或存在命中后代」的节点（与前端树过滤语义一致，
     * 避免命中节点因祖先被滤而整支消失）。
     */
    private static List<SysOrg> pruneByName(List<SysOrg> scoped, Map<Long, SysOrg> byId, Long rootId, String keyword) {
        Map<Long, List<SysOrg>> childrenMap = new HashMap<>();
        for (SysOrg o : scoped) {
            if (o.getParentId() != null) {
                childrenMap.computeIfAbsent(o.getParentId(), k -> new ArrayList<>()).add(o);
            }
        }
        Set<Long> kept = new HashSet<>();
        keepMatching(byId.get(rootId), childrenMap, keyword, kept);
        return scoped.stream()
            .filter(o -> kept.contains(o.getId()))
            .collect(Collectors.toList());
    }

    /** 名称剪枝递归：返回该节点（自身命中或存在保留后代）是否保留。 */
    private static boolean keepMatching(SysOrg node, Map<Long, List<SysOrg>> childrenMap,
                                        String keyword, Set<Long> kept) {
        if (node == null) {
            return false;
        }
        boolean selfKept = node.getName() != null && node.getName().contains(keyword);
        boolean childKept = false;
        for (SysOrg child : childrenMap.getOrDefault(node.getId(), List.of())) {
            childKept = keepMatching(child, childrenMap, keyword, kept) || childKept;
        }
        if (selfKept || childKept) {
            kept.add(node.getId());
            return true;
        }
        return false;
    }

    @Override
    public List<OrgUserItemResp> listOrgUsers(Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        // D3=A 组织成员列表归属 USER:VIEW（契约 §4 B 区：「看成员 = 看用户」），
        // 与组织实例的 VIEW / VIEW_POSITION 解耦
        permissionValidator.checkTypeLevel(ResourceTypeCode.USER, AdminOperationCode.VIEW);

        cn.ac.fage.accessmesh.access.admin.entity.table.SysUserOrgTableDef suo = cn.ac.fage.accessmesh.access.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;
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
            org.getParentId(), org.getCode(),
            org.getStatus(), org.getSortOrder(), org.getCreatedAt(), org.getUpdatedAt(), children
        );
    }

    private List<OrgResp> buildTree(List<SysOrg> all, Long parentId) {
        return all.stream()
            .filter(o -> parentId.equals(o.getParentId()))
            .map(o -> new OrgResp(
                o.getId(), Integer.parseInt(o.getOrgType()), o.getName(),
                o.getParentId(), o.getCode(),
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
