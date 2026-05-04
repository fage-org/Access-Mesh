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
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysConfigTableDef.SYS_CONFIG;

@Service
public class ConfigServiceImpl implements ConfigService {

    private final SysConfigMapper configMapper;
    private final AdminPermissionValidator permissionValidator;

    public ConfigServiceImpl(SysConfigMapper configMapper, AdminPermissionValidator permissionValidator) {
        this.configMapper = configMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
    public PaginatedResult<ConfigResp> pageConfigs(PageReq pageReq) {
        Page<SysConfig> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysConfig> result = configMapper.paginate(page,
            QueryWrapper.create()
                .where(SYS_CONFIG.DELETE_FLAG.eq(0))
                .orderBy(SYS_CONFIG.CREATED_AT.asc()));

        var items = result.getRecords().stream()
            .map(c -> new ConfigResp(c.getId(), c.getConfigName(), c.getConfigKey(), c.getConfigValue(), c.getRemark(), c.getCreatedAt(), c.getUpdatedAt()))
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    @Override
    public ConfigResp getConfig(Long id) {
        SysConfig config = configMapper.selectOneById(id);
        if (config == null || config.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.CONFIG_NOT_FOUND.getCode(), AdminErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        return new ConfigResp(config.getId(), config.getConfigName(), config.getConfigKey(), config.getConfigValue(), config.getRemark(), config.getCreatedAt(), config.getUpdatedAt());
    }

    @Override
    @Transactional
    public void updateConfig(ConfigUpdateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.CONFIG,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        SysConfig config = configMapper.selectOneById(req.id());
        if (config == null || config.getDeleteFlag() != 0L) {
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

    @Override
    @Transactional
    public void deleteConfig(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.CONFIG, resourceCodes, AdminOperationCode.DELETE);

        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            SysConfig config = configMapper.selectOneById(id);
            if (config == null || config.getDeleteFlag() != 0L) continue;
            if (Boolean.TRUE.equals(config.getIsSystem())) {
                throw new BizException(AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getCode(),
                    AdminErrorCode.CONFIG_SYSTEM_IMMUTABLE.getMessage());
            }
            config.setDeleteFlag(1L);
            config.setDeletedAt(now);
            configMapper.update(config);
        }
    }
}
