package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.admin.service.ConfigService;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import com.mybatisflex.core.paginate.Page;
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

    private final SystemConfigMapper configMapper;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param configMapper 配置数据访问Mapper（T-ACCESS-002 归并后为 SystemConfigMapper）
     * @param permissionValidator 权限校验器，校验配置操作权限
     */
    public ConfigServiceImpl(SystemConfigMapper configMapper, AdminPermissionValidator permissionValidator) {
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
    public PageResp<ConfigResp> pageConfigs(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<SystemConfig> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SystemConfig> result = configMapper.selectPageByTenantId(page, tenantId);

        var items = result.getRecords().stream()
            .map(c -> new ConfigResp(c.getId(), c.getConfigName(), c.getConfigKey(), c.getConfigValue(), c.getRemark(), c.getCreatedAt(), c.getUpdatedAt()))
            .toList();

        return new PageResp<>(items, result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), result.hasNext());
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
        SystemConfig config = configMapper.selectOneByIdAndTenantId(TenantContextHolder.getTenantId(), id);
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
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "CONFIG_UPDATE", targetType = "system_config",
        targetId = "#req.id()", summary = "'update config ' + #req.id()")
    public void updateConfig(ConfigUpdateReq req) {
        // 权限检查 — 实例级 UPDATE
        permissionValidator.checkInstanceLevel(
            ResourceTypeCode.SYSTEM_CONFIG,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        SystemConfig config = configMapper.selectOneByIdAndTenantId(TenantContextHolder.getTenantId(), req.id());
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
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "CONFIG_DELETE", targetType = "system_config",
        targetId = "", summary = "'batch delete configs'")
    public void deleteConfig(IdsReq req) {
        // 权限检查 — 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(ResourceTypeCode.SYSTEM_CONFIG, resourceCodes, AdminOperationCode.DELETE);

        // 批量查询检查系统配置并过滤有效ID
        List<SystemConfig> configs = configMapper.selectListByIdsAndTenantId(TenantContextHolder.getTenantId(), req.ids());

        // 检查是否有系统内置配置（不可修改）
        for (SystemConfig config : configs) {
            if (Boolean.TRUE.equals(config.getIsSystem())) {
                throw new BizException(AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getCode(),
                    AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getMessage());
            }
        }

        // 批量软删除
        if (!configs.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = configs.stream().map(SystemConfig::getId).collect(java.util.stream.Collectors.toList());
            configMapper.softDeleteBatch(TenantContextHolder.getTenantId(), validIds, now);
        }
    }
}