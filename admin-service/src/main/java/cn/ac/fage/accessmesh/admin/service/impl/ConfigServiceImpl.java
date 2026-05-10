package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.ConfigResp;
import cn.ac.fage.accessmesh.admin.entity.SysConfig;
import cn.ac.fage.accessmesh.admin.entity.table.SysConfigTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysConfigMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.ConfigService;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.mybatis.TenantSafeQuery;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 系统配置管理服务实现类
 * <p>
 * 提供系统配置的分页查询、更新、删除功能。
 * 系统配置存储系统运行参数，如超时时间、开关设置等。
 * 系统内置配置(isSystem=true)不可修改和删除，保护系统核心参数。
 * 使用租户安全查询确保配置数据隔离。
 * </p>
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    private final SysConfigMapper configMapper;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param configMapper 配置数据访问Mapper
     * @param permissionValidator 权限校验器，校验配置操作权限
     */
    public ConfigServiceImpl(SysConfigMapper configMapper, AdminPermissionValidator permissionValidator) {
        this.configMapper = configMapper;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 分页查询系统配置列表
     * <p>
     * 获取当前租户的所有系统配置，按创建时间正序排列。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页配置列表结果
     */
    @Override
    public PaginatedResult<ConfigResp> pageConfigs(PageReq pageReq) {
        Page<SysConfig> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysConfig> result = configMapper.paginate(page,
            QueryWrapper.create()
                .where(SysConfigTableDef.SYS_CONFIG.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysConfigTableDef.SYS_CONFIG.DELETE_FLAG.eq(0))
                .orderBy(SysConfigTableDef.SYS_CONFIG.CREATED_AT.asc()));

        var items = result.getRecords().stream()
            .map(c -> new ConfigResp(c.getId(), c.getConfigName(), c.getConfigKey(), c.getConfigValue(), c.getRemark(), c.getCreatedAt(), c.getUpdatedAt()))
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 获取配置详情
     * <p>
     * 根据配置ID查询配置完整信息。
     * 使用租户安全查询确保数据隔离。
     * </p>
     *
     * @param id 配置ID
     * @return 配置详情响应
     * @throws BizException 配置不存在
     */
    @Override
    public ConfigResp getConfig(Long id) {
        SysConfig config = TenantSafeQuery.selectOneByIdSafe(
            configMapper, SysConfigTableDef.SYS_CONFIG.ID, SysConfigTableDef.SYS_CONFIG.TENANT_ID, SysConfigTableDef.SYS_CONFIG.DELETE_FLAG,
            TenantContextHolder.getTenantId(), id);
        if (config == null) {
            throw new BizException(AdminErrorCode.CONFIG_NOT_FOUND.getCode(), AdminErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        return new ConfigResp(config.getId(), config.getConfigName(), config.getConfigKey(), config.getConfigValue(), config.getRemark(), config.getCreatedAt(), config.getUpdatedAt());
    }

    /**
     * 更新配置
     * <p>
     * 更新配置的值和备注，不允许修改配置名称和键。
     * 执行实例级权限校验。
     * 系统内置配置(isSystem=true)不可修改。
     * </p>
     *
     * @param req 配置更新请求，包含配置ID和新值、备注
     * @throws BizException 配置不存在、系统内置配置不可修改
     */
    @Override
    @Transactional
    public void updateConfig(ConfigUpdateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.CONFIG,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        SysConfig config = TenantSafeQuery.selectOneByIdSafe(
            configMapper, SysConfigTableDef.SYS_CONFIG.ID, SysConfigTableDef.SYS_CONFIG.TENANT_ID, SysConfigTableDef.SYS_CONFIG.DELETE_FLAG,
            TenantContextHolder.getTenantId(), req.id());
        if (config == null) {
            throw new BizException(AdminErrorCode.CONFIG_NOT_FOUND.getCode(), AdminErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        if (Boolean.TRUE.equals(config.getIsSystem())) {
            throw new BizException(AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getCode(),
                AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getMessage());
        }
        config.setConfigValue(req.configValue());
        config.setRemark(req.remark());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.update(config);
    }

    /**
     * 批量删除配置
     * <p>
     * 执行批量实例级权限校验后软删除配置。
     * 使用批量查询检查是否存在系统内置配置，如有则拒绝删除。
     * 使用单条批量SQL提高性能。
     * </p>
     *
     * @param req ID集合请求，包含待删除的配置ID列表
     * @throws BizException 系统内置配置不可删除
     */
    @Override
    @Transactional
    public void deleteConfig(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.CONFIG, resourceCodes, AdminOperationCode.DELETE);

        // Batch query to check system config and filter valid IDs (performance fix: avoid N+1 queries)
        List<SysConfig> configs = configMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysConfigTableDef.SYS_CONFIG.ID.in(req.ids()))
                .and(SysConfigTableDef.SYS_CONFIG.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysConfigTableDef.SYS_CONFIG.DELETE_FLAG.eq(0))
        );

        // Check if any config is system config (immutable)
        for (SysConfig config : configs) {
            if (Boolean.TRUE.equals(config.getIsSystem())) {
                throw new BizException(AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getCode(),
                    AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getMessage());
            }
        }

        // Batch soft delete (performance fix: use single SQL instead of loop)
        if (!configs.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = configs.stream().map(SysConfig::getId).collect(java.util.stream.Collectors.toList());
            configMapper.softDeleteBatch(validIds, now);
        }
    }
}