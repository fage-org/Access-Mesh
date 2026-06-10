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
import cn.ac.fage.accessmesh.admin.service.OrgService;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgSyncHandler;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 组织管理服务实现类
 * <p>
 * 提供组织的CRUD操作、树形查询等功能。
 * 实现跨服务数据同步机制，通过Outbox Pattern确保组织创建与同步任务记录原子性。
 * 支持组织层级深度限制（最多10级）、组织编码唯一性校验。
 * 使用OrgDomainService处理组织数据查询。
 * 设计约束：组织/岗位在 permission-center 中有两类事实：
 * ADMIN_ORG resource_entity 用于实例级管理权限，
 * ORG/POSITION abstract_role 用于角色容器和 user_role 计算。
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
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param orgMapper 组织数据访问Mapper
     * @param orgDomainService 组织领域服务，处理组织数据查询和批量操作
     * @param orgSyncHandler 组织同步处理器，同步组织数据到permission-center
     * @param permissionValidator 权限校验器，校验组织操作权限
     * @param syncRetryService 同步重试服务，记录同步失败任务
     * @param objectMapper JSON序列化工具
     * @param userOrgMapper 用户组织关联Mapper，查询组织下用户关联
     * @param userDomainService 用户领域服务，批量查询用户信息
     */
    public OrgServiceImpl(SysOrgMapper orgMapper, OrgDomainService orgDomainService,
                          OrgSyncHandler orgSyncHandler, AdminPermissionValidator permissionValidator,
                          SyncRetryService syncRetryService, ObjectMapper objectMapper,
                          SysUserOrgMapper userOrgMapper,
                          UserDomainService userDomainService) {
        this.orgMapper = orgMapper;
        this.orgDomainService = orgDomainService;
        this.orgSyncHandler = orgSyncHandler;
        this.permissionValidator = permissionValidator;
        this.syncRetryService = syncRetryService;
        this.objectMapper = objectMapper;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
    }

    /**
     * 创建组织
     * <p>
     * 创建新组织，校验编码唯一性和组织层级深度（不超过10级）。
     * 创建成功后记录同步任务，异步同步到permission-center（Outbox Pattern）。
     * 执行类型级权限校验(CREATE)。
     * 后续实现组织同步时需同时落地 ADMIN_ORG resource_entity 与
     * ORG/POSITION abstract_role，并分别保存资源ID和角色ID。
     * </p>
     *
     * @param req 组织创建请求，包含组织名称、编码、类型、父组织ID等
     * @return 新组织ID
     * @throws BizException 组织编码已存在、组织层级超限、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrg(OrgCreateReq req) {
        // 权限检查 — 类型级 CREATE
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
        // 权限检查 — 实例级 UPDATE
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

        // 仅在指定新父级时验证父级变更
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
        // 权限检查 — 实例级 DELETE
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
     * 当 orgId 不为空时，仅返回 orgId 子树内的组织（含自身及所有子孙），
     * 实现岗位 Tab 按选中组织筛选的语义。
     * 按排序字段和创建时间排序。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页组织列表结果
     */
    @Override
    public PaginatedResult<OrgResp> pageOrgs(OrgPageReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        String orgType = req.orgType() != null ? String.valueOf(req.orgType()) : null;
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

        // orgId 子树语义：仅保留 orgId 子树内的组织（含自身及子孙）
        List<OrgResp> items = records.stream()
            .map(o -> toResp(o, List.of()))
            .collect(Collectors.toList());

        // orgId 过滤后总数需重新计算
        long total = result.getTotalRow();
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(total, pageNum, pageSize, (int) totalPages));
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
        String orgType = query != null && query.orgType() != null ? String.valueOf(query.orgType()) : null;
        Integer status = query != null ? query.status() : null;

        List<SysOrg> all = orgMapper.selectOrgsForTree(tenantId, orgType, status);
        return buildTree(all, 0L);
    }

    /**
     * 查询组织/岗位下的用户列表
     * <p>
     * 查询指定组织或岗位下通过 user-org 关联的用户。
     * 用于岗位卡片展开后展示已分配用户。
     * </p>
     *
     * @param orgId 组织或岗位ID
     * @return 用户简要信息列表
     */
    @Override
    public List<OrgUserItemResp> listOrgUsers(Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 按 orgId 查询用户组织关联（使用 SysUserOrgTableDef，不静态导入）
        cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef suo = cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;
        com.mybatisflex.core.query.QueryWrapper qw = com.mybatisflex.core.query.QueryWrapper.create()
            .where(suo.TENANT_ID.eq(tenantId))
            .where(suo.ORG_ID.eq(orgId))
            .where(suo.DELETE_FLAG.eq(0L));
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(qw);

        if (userOrgs.isEmpty()) {
            return List.of();
        }

        // 批量查询用户信息
        java.util.Set<Long> userIds = userOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, SysUser> userMap = userDomainService.selectValidByIds(tenantId, userIds).stream()
            .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));

        // 按关联顺序组装响应
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
            .collect(java.util.stream.Collectors.toList());
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
