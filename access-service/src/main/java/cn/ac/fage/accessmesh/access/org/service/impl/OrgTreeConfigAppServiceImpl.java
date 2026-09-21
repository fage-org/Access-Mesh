package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.dto.IdsReq;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgTreeConfigCreateReq;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgTreeConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.org.dto.resp.OrgTreeConfigResp;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.org.service.OrgTreeConfigAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * 组织树配置管理服务实现类
 * <p>
 * 提供组织树配置的CRUD操作、设置默认配置等功能。
 * 组织树配置用于定义不同场景下组织树的展示规则，如过滤条件、排序方式等。
 * 可配置多个组织树方案，在不同业务场景使用不同的组织树配置。
 * 系统只有一个默认配置，设置新默认时会自动清除旧默认。
 * 设计约束：默认配置不是展示偏好，而是身份目录语义（详见 @see）。
 * @see docs/design/default-org-tree-user-lifecycle.md
 * </p>
 */
@Service
public class OrgTreeConfigAppServiceImpl implements OrgTreeConfigAppService {

    private static final String POSITION_TREE_TYPE = "POSITION";

    private final SysOrgTreeConfigMapper orgTreeConfigMapper;
    private final AdminPermissionValidator permissionValidator;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final TreeWriteLockSupport treeWriteLockSupport;

    /**
     * 构造函数注入依赖
     *
     * @param orgTreeConfigMapper 组织树配置数据访问Mapper
     * @param permissionValidator 权限校验器，校验配置操作权限
     * @param orgTreeConfigDomainService 组织树配置领域服务（默认树身份目录守卫，T-ORG-002）
     * @param orgDomainService 组织领域服务（树根存在性与子树解析）
     * @param treeWriteLockSupport 树写锁（守卫读取与组织结构写串行）
     */
    public OrgTreeConfigAppServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper,
                                       AdminPermissionValidator permissionValidator,
                                       OrgTreeConfigDomainService orgTreeConfigDomainService,
                                       OrgDomainService orgDomainService,
                                       TreeWriteLockSupport treeWriteLockSupport) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
        this.permissionValidator = permissionValidator;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.treeWriteLockSupport = treeWriteLockSupport;
    }

    /**
     * 创建组织树配置
     * <p>
     * 创建新的组织树配置方案，定义组织树的展示规则。
     * 如果设置为默认配置，会自动清除其他配置的默认标记。
     * 执行类型级权限校验(CREATE)。
     * </p>
     *
     * @param config 组织树配置实体，包含配置名称、规则定义等
     * @return 创建成功的配置ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_TREE_CONFIG_CREATE", targetType = "sys_org_tree_config",
        targetId = "#result", summary = "'create org tree config'")
    public Long createOrgTreeConfig(OrgTreeConfigCreateReq req) {
        PermissionChangeContext.markVisibility(TenantContextHolder.getTenantId());
        Long tenantId = TenantContextHolder.getTenantId();

        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_ORG_TREE_CONFIG, OperationCode.CREATE);

        SysOrgTreeConfig config = new SysOrgTreeConfig();
        config.setTenantId(tenantId);
        config.setRootOrgId(req.orgId());
        config.setTreeName(req.treeName());
        config.setTreeType(req.treeType());
        config.setSingleAssoc(resolveSingleAssoc(req.treeType(), req.singleAssoc(), true));
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setDeleteFlag(0L);
        orgTreeConfigMapper.insert(config);
        return config.getId();
    }

    /**
     * 更新组织树配置
     * <p>
     * 更新组织树配置的名称、规则定义等属性。
     * 执行实例级权限校验(UPDATE)。
     * 如果设置为默认配置，会自动清除其他配置的默认标记。
     * </p>
     *
     * @param config 组织树配置实体，包含配置ID和新属性值
     * @throws BizException 组织树配置不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_TREE_CONFIG_UPDATE", targetType = "sys_org_tree_config",
        targetId = "#req.id()", summary = "'update org tree config ' + #req.id()")
    public void updateOrgTreeConfig(OrgTreeConfigUpdateReq req) {
        PermissionChangeContext.markVisibility(TenantContextHolder.getTenantId());
        Long tenantId = TenantContextHolder.getTenantId();

        // 权限检查 — ORG_TREE_CONFIG 实例级 UPDATE
        permissionValidator.checkInstanceLevel(ResourceTypeCode.ADMIN_ORG_TREE_CONFIG, req.id().toString(), OperationCode.UPDATE);

        SysOrgTreeConfig existing = orgTreeConfigMapper.selectByIdSafe(tenantId, req.id());
        if (existing == null) {
            throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(), AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }

        // 默认配置的 rootOrgId 变更 = 默认身份目录范围变化（T-ORG-002，拍板最小面守卫）：
        // 挂 SYS_ORG 树锁后按共享守卫判定——任一用户将失去默认树最后归属则拒绝；
        // 安全扩围（新根子树 ⊇ 旧根子树，如根改到自己的祖先）不损失任何归属，放行。
        // 锁前快照仅作「是否可能需要锁」的乐观判定，锁内重读为准（评审 P3-1：并发
        // setDefault 可使配置在快照读取后转为默认，锁前快照会漏判守卫）
        // tenant 1 固定图根业务键漂移由 bootstrap 重启检测兜底（与菜单根 code 漂移同口径），
        // 写入口不重复挡
        if (req.orgId() != null && !req.orgId().equals(existing.getRootOrgId())) {
            treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
            existing = orgTreeConfigMapper.selectByIdSafe(tenantId, req.id());
            if (existing == null) {
                // 锁前快照存在、锁内被并发删除（deleteConfigs 已挂同锁串行，此为兜底）
                throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                    AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
            }
            if (Boolean.TRUE.equals(existing.getIsDefault())) {
                guardDefaultTreeRescope(tenantId, req.orgId(), "修改默认组织树根", false);
            }
        }

        String treeType = req.treeType() != null ? req.treeType() : existing.getTreeType();
        if (req.orgId() != null) existing.setRootOrgId(req.orgId());
        if (req.treeName() != null) existing.setTreeName(req.treeName());
        if (req.treeType() != null) existing.setTreeType(req.treeType());
        if (req.singleAssoc() != null || req.treeType() != null) {
            boolean defaultSingleAssoc = existing.getSingleAssoc() == null ? true : existing.getSingleAssoc();
            existing.setSingleAssoc(resolveSingleAssoc(treeType, req.singleAssoc(), defaultSingleAssoc));
        }
        existing.setUpdatedAt(LocalDateTime.now());
        orgTreeConfigMapper.update(existing);
    }

    /**
     * 批量删除组织树配置
     * <p>
     * 执行批量实例级权限校验后软删除配置方案。
     * 使用单条批量SQL提高性能。
     * </p>
     *
     * @param req ID集合请求，包含待删除的配置ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_TREE_CONFIG_DELETE", targetType = "sys_org_tree_config",
        targetId = "", summary = "'batch delete org tree configs'")
    public void deleteOrgTreeConfigs(IdsReq req) {
        PermissionChangeContext.markVisibility(TenantContextHolder.getTenantId());
        if (req.ids() == null || req.ids().isEmpty()) {
            return;
        }
        // 权限检查 — ORG_TREE_CONFIG 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(ResourceTypeCode.ADMIN_ORG_TREE_CONFIG, resourceCodes, OperationCode.DELETE);

        // 默认配置行删除拒绝（T-ORG-002，拍板最小面）：默认树是身份目录的结构性存在，
        // 删行后租户无默认树（用户列表恒空），tenant 1 固定图重启失败；调整默认树走
        // set-default/更新入口（受归属守卫约束）。无默认配置的租户删任何行放行。
        // 守卫读取挂 SYS_ORG 树锁（评审 P3-1：与并发 set-default 串行，防「守卫读到旧
        // 默认后并发切换、本次误删新默认行」窗口）
        treeWriteLockSupport.lockTreeWrites(TenantContextHolder.getTenantId(),
            TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        List<SysOrgTreeConfig> defaultConfigs =
            orgTreeConfigDomainService.findDefaultConfigs(TenantContextHolder.getTenantId());
        for (SysOrgTreeConfig defaultConfig : defaultConfigs) {
            if (req.ids().contains(defaultConfig.getId())) {
                throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_DEFAULT_PROTECTED.getCode(),
                    "默认组织树配置不允许删除（身份目录结构性存在），请先切换默认: configId=" + defaultConfig.getId());
            }
        }

        // 性能优化：使用批量软删除替代循环更新
        LocalDateTime now = LocalDateTime.now();
        orgTreeConfigMapper.softDeleteBatch(TenantContextHolder.getTenantId(), req.ids(), now);
    }

    /**
     * 设置默认组织树配置
     * <p>
     * 将指定配置设置为默认的组织树展示方案。
     * 系统将使用默认配置作为身份目录树（见类 @see）。
     * 执行实例级权限校验(TOGGLE)。
     * 设置新默认时会自动清除其他配置的默认标记。
     * 身份目录守卫（T-ORG-002，拍板最小面 + 评审拍板 A 收紧）：旧默认树上存在任一
     * 用户归属时拒绝切换——按拍板字面「存在归属即拒」（扩围树同样拒绝，宁严勿松；
     * 对齐 default-org-tree-user-lifecycle §7「已存在用户时切换默认树属高危迁移动作，
     * 不应作为普通配置开关」）；空租户（旧默认树无用户归属）可切换。
     * </p>
     *
     * @param id 配置ID
     * @throws BizException 组织树配置不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_TREE_CONFIG_SET_DEFAULT", targetType = "sys_org_tree_config",
        targetId = "#id", summary = "'set default org tree config ' + #id")
    public void setDefault(Long id) {
        PermissionChangeContext.markVisibility(TenantContextHolder.getTenantId());
        Long tenantId = TenantContextHolder.getTenantId();
        // 权限检查 — ORG_TREE_CONFIG 实例级 UPDATE
        permissionValidator.checkInstanceLevel(ResourceTypeCode.ADMIN_ORG_TREE_CONFIG, id.toString(), OperationCode.TOGGLE);

        SysOrgTreeConfig config = orgTreeConfigMapper.selectByIdSafe(tenantId, id);
        if (config == null) {
            throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(), AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }
        // 挂 SYS_ORG 树锁（守卫读取默认树结构与成员归属，与组织结构写串行）后
        // 重读配置（评审 P3-1：并发 update 改根后锁前快照的 rootOrgId 已过期）、判定切树影响；
        // 重读判空与 update 分支同款兜底（claude 外评 P3：锁前读与取锁间被并发
        // deleteOrgTreeConfigs 软删时，11001 而非 NPE 500）
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        config = orgTreeConfigMapper.selectByIdSafe(tenantId, id);
        if (config == null) {
            throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }
        guardDefaultTreeRescope(tenantId, config.getRootOrgId(), "切换默认组织树", true);
        clearDefault();
        config.setIsDefault(true);
        config.setUpdatedAt(LocalDateTime.now());
        orgTreeConfigMapper.update(config);
    }

    /**
     * 获取组织树配置详情
     * <p>
     * 根据配置ID查询组织树配置的完整信息。
     * </p>
     *
     * @param id 配置ID
     * @return 组织树配置详情信息
     */
    @Override
    public OrgTreeConfigResp getOrgTreeConfig(Long id) {
        SysOrgTreeConfig config = orgTreeConfigMapper.selectByIdSafe(TenantContextHolder.getTenantId(), id);
        return OrgTreeConfigResp.from(config);
    }

    /**
     * 分页查询组织树配置列表
     * <p>
     * 查询系统中所有的组织树配置方案，支持分页。
     * 按创建时间倒序排列。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页组织树配置列表结果
     */
    @Override
    public PageResp<OrgTreeConfigResp> pageOrgTreeConfigs(PageReq pageReq) {
        // XML 分页统一 offset/limit + count 双查询（MyBatis-Flex Page 参数在 XML 映射下不生效）
        long total = orgTreeConfigMapper.countAllByTenant(TenantContextHolder.getTenantId());
        List<OrgTreeConfigResp> items = total == 0 ? List.of()
            : orgTreeConfigMapper.selectAllByTenant(TenantContextHolder.getTenantId(),
                (pageReq.pageNum() - 1) * pageReq.pageSize(), pageReq.pageSize()).stream()
                .map(OrgTreeConfigResp::from)
                .toList();
        return new PageResp<>(items, total, pageReq.pageNum(), pageReq.pageSize(),
            (pageReq.pageNum() - 1) * pageReq.pageSize() + items.size() < total);
    }

    private boolean resolveSingleAssoc(String treeType, Boolean requestedValue, boolean defaultValue) {
        if (treeType != null && POSITION_TREE_TYPE.equalsIgnoreCase(treeType)) {
            return false;
        }
        return requestedValue != null ? requestedValue : defaultValue;
    }

    /**
     * 默认身份目录范围变更守卫（切默认/改默认配置根共用；调用方须已持 SYS_ORG 树锁）。
     * <p>
     * 新根必须有效存在；strict=true（切默认，评审拍板 A 收紧）按拍板字面「旧默认树
     * 存在任一用户归属即拒」——借共享守卫以空保留集判定存在性（findUsersLosingDefaultHome
     * (old, ∅) 非空 ⇔ 旧树内存在归属用户），扩围树同样拒绝；strict=false（改默认配置根）
     * 为损失判定——范围收窄将使任一用户失去默认树最后归属则整体拒绝（共享守卫
     * {@link OrgTreeConfigDomainService#findUsersLosingDefaultHome}，与组织删除/成员移除
     * 同语义）；无旧默认配置或根未变化时放行（含改根场景的安全扩围：新子树 ⊇ 旧子树）。
     * </p>
     */
    private void guardDefaultTreeRescope(Long tenantId, Long newRootOrgId, String action, boolean strict) {
        SysOrg newRoot = orgDomainService.selectValidById(tenantId, newRootOrgId);
        if (newRoot == null) {
            throw new BizException(AccessErrorCode.ORG_NOT_FOUND.getCode(),
                "目标根组织不存在: orgId=" + newRootOrgId);
        }
        List<SysOrgTreeConfig> oldDefaults = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (oldDefaults.isEmpty() || oldDefaults.get(0).getRootOrgId() == null
            || oldDefaults.get(0).getRootOrgId().equals(newRootOrgId)) {
            return;
        }
        Set<Long> oldTreeOrgIds = new HashSet<>(orgDomainService.getDescendantIdsIncludingSelf(
            tenantId, oldDefaults.get(0).getRootOrgId()));
        Set<Long> newTreeOrgIds = strict
            ? Set.of()
            : new HashSet<>(orgDomainService.getDescendantIdsIncludingSelf(tenantId, newRootOrgId));
        Set<Long> losingUserIds = orgTreeConfigDomainService.findUsersLosingDefaultHome(
            tenantId, oldTreeOrgIds, newTreeOrgIds);
        if (!losingUserIds.isEmpty()) {
            throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_DEFAULT_PROTECTED.getCode(),
                strict
                    ? action + "被拒绝：旧默认树存在 " + losingUserIds.size() + " 名用户归属，请先迁移成员后切换"
                    : action + "将使 " + losingUserIds.size() + " 名用户失去默认组织树身份目录归属，请先迁移成员");
        }
    }

    /**
     * 清除默认配置标记
     * <p>
     * 使用单条SQL批量清除所有配置的默认标记，确保只有一个默认配置。
     * </p>
     */
    private void clearDefault() {
        orgTreeConfigMapper.clearAllDefaults(TenantContextHolder.getTenantId(), LocalDateTime.now());
    }
}
