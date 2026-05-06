package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTreeConfigTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.OrgTreeConfigService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.mybatis.TenantSafeQuery;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class OrgTreeConfigServiceImpl implements OrgTreeConfigService {

    private final SysOrgTreeConfigMapper orgTreeConfigMapper;
    private final AdminPermissionValidator permissionValidator;

    public OrgTreeConfigServiceImpl(SysOrgTreeConfigMapper orgTreeConfigMapper, AdminPermissionValidator permissionValidator) {
        this.orgTreeConfigMapper = orgTreeConfigMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    public Long createOrgTreeConfig(SysOrgTreeConfig config) {
        // Permission check - type-level CREATE on ORG_TREE_CONFIG
        permissionValidator.checkTypeLevel(AdminResourceType.ORG_TREE_CONFIG, AdminOperationCode.CREATE);

        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault();
        }
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setDeleteFlag(0L);
        orgTreeConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    @Transactional
    public void updateOrgTreeConfig(SysOrgTreeConfig config) {
        // Permission check - instance-level UPDATE on ORG_TREE_CONFIG
        permissionValidator.checkInstanceLevel(AdminResourceType.ORG_TREE_CONFIG, config.getId().toString(), AdminOperationCode.UPDATE);

        SysOrgTreeConfig existing = TenantSafeQuery.selectOneByIdSafe(
            orgTreeConfigMapper, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.ID, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG,
            TenantContextHolder.getTenantId(), config.getId());
        if (existing == null) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(), AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            clearDefault();
        }
        config.setUpdatedAt(LocalDateTime.now());
        orgTreeConfigMapper.update(config);
    }

    @Override
    @Transactional
    public void deleteOrgTreeConfigs(IdsReq req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return;
        }
        // Permission check - batch instance-level DELETE on ORG_TREE_CONFIG
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.ORG_TREE_CONFIG, resourceCodes, AdminOperationCode.DELETE);

        // Performance fix: use batch soft delete instead of loop updates
        LocalDateTime now = LocalDateTime.now();
        orgTreeConfigMapper.softDeleteBatch(null, req.ids(), now);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        // Permission check - instance-level UPDATE on ORG_TREE_CONFIG
        permissionValidator.checkInstanceLevel(AdminResourceType.ORG_TREE_CONFIG, id.toString(), AdminOperationCode.TOGGLE);

        SysOrgTreeConfig config = TenantSafeQuery.selectOneByIdSafe(
            orgTreeConfigMapper, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.ID, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG,
            TenantContextHolder.getTenantId(), id);
        if (config == null) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(), AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }
        clearDefault();
        config.setIsDefault(true);
        config.setUpdatedAt(LocalDateTime.now());
        orgTreeConfigMapper.update(config);
    }

    @Override
    public SysOrgTreeConfig getOrgTreeConfig(Long id) {
        return TenantSafeQuery.selectOneByIdSafe(
            orgTreeConfigMapper, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.ID, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID, SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG,
            TenantContextHolder.getTenantId(), id);
    }

    @Override
    public PaginatedResult<SysOrgTreeConfig> pageOrgTreeConfigs(PageReq pageReq) {
        Page<SysOrgTreeConfig> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysOrgTreeConfig> result = orgTreeConfigMapper.paginate(page,
            QueryWrapper.create()
                .where(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
                .orderBy(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.CREATED_AT.desc()));

        List<SysOrgTreeConfig> items = result.getRecords();
        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    private void clearDefault() {
        // Performance optimization opportunity: could use custom batch update SQL
        // For now, loop update is used due to MyBatis-Flex API limitations
        List<SysOrgTreeConfig> configs = orgTreeConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.IS_DEFAULT.eq(true))
        );
        LocalDateTime now = LocalDateTime.now();
        for (SysOrgTreeConfig config : configs) {
            config.setIsDefault(false);
            config.setUpdatedAt(now);
            orgTreeConfigMapper.update(config);
        }
    }
}
