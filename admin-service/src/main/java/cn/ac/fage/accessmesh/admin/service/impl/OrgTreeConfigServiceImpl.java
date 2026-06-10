package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgTreeConfigCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgTreeConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgTreeConfigResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.OrgTreeConfigService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


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
public class OrgTreeConfigServiceImpl implements OrgTreeConfigService {

    private static final String POSITION_TREE_TYPE = "POSITION";

    private final SysOrgTreeConfigMapper orgTreeConfigMapper;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param orgTreeConfigMapper 组织树配置数据访问Mapper
     * @param permissionValidator 权限校验器，校验配置操作权限
     */
    public OrgTreeConfigServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper, AdminPermissionValidator permissionValidator) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
        this.permissionValidator = permissionValidator;
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
    public Long createOrgTreeConfig(OrgTreeConfigCreateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        permissionValidator.checkTypeLevel(AdminResourceType.ORG_TREE_CONFIG, AdminOperationCode.CREATE);

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
    public void updateOrgTreeConfig(OrgTreeConfigUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 权限检查 — ORG_TREE_CONFIG 实例级 UPDATE
        permissionValidator.checkInstanceLevel(AdminResourceType.ORG_TREE_CONFIG, req.id().toString(), AdminOperationCode.UPDATE);

        SysOrgTreeConfig existing = orgTreeConfigMapper.selectByIdSafe(tenantId, req.id());
        if (existing == null) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(), AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
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
    public void deleteOrgTreeConfigs(IdsReq req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return;
        }
        // 权限检查 — ORG_TREE_CONFIG 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.ORG_TREE_CONFIG, resourceCodes, AdminOperationCode.DELETE);

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
     * 后续实现必须在已有用户、默认树根变更或树根重叠时增加迁移保护。
     * </p>
     *
     * @param id 配置ID
     * @throws BizException 组织树配置不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        // 权限检查 — ORG_TREE_CONFIG 实例级 UPDATE
        permissionValidator.checkInstanceLevel(AdminResourceType.ORG_TREE_CONFIG, id.toString(), AdminOperationCode.TOGGLE);

        SysOrgTreeConfig config = orgTreeConfigMapper.selectByIdSafe(TenantContextHolder.getTenantId(), id);
        if (config == null) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(), AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }
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
    public PaginatedResult<OrgTreeConfigResp> pageOrgTreeConfigs(PageReq pageReq) {
        Page<SysOrgTreeConfig> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysOrgTreeConfig> result = orgTreeConfigMapper.paginateAll(page, TenantContextHolder.getTenantId());

        List<OrgTreeConfigResp> items = result.getRecords().stream()
            .map(OrgTreeConfigResp::from)
            .toList();
        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    private boolean resolveSingleAssoc(String treeType, Boolean requestedValue, boolean defaultValue) {
        if (treeType != null && POSITION_TREE_TYPE.equalsIgnoreCase(treeType)) {
            return false;
        }
        return requestedValue != null ? requestedValue : defaultValue;
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
