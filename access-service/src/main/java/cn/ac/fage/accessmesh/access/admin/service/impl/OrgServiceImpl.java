package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgUserItemResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.security.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.admin.service.OrgService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.application.OrgWriteAppService;
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
 * Provides CRUD and tree query for orgs/positions. Writes delegate to
 * {@code OrgWriteAppService} which maintains the ORG/POSITION projection (abstract_role +
 * ADMIN_ORG resource_entity) in the same transaction, keyed by business keys.
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
    private final OrgWriteAppService orgWriteAppService;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;

    public OrgServiceImpl(SysOrgMapper orgMapper, OrgDomainService orgDomainService,
                          AdminPermissionValidator permissionValidator,
                          OrgWriteAppService orgWriteAppService,
                          SysUserOrgMapper userOrgMapper,
                          UserDomainService userDomainService) {
        this.orgMapper = orgMapper;
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
