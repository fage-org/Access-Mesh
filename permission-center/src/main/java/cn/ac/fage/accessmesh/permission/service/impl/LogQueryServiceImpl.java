package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.permission.service.LogQueryService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import org.springframework.stereotype.Service;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志查询服务实现类
 * <p>
 * 提供变更日志和操作日志的只读查询功能。
 * 所有查询均需要SYSTEM_CONFIG_VIEW权限。
 * 支持按实体类型、用户、角色、时间范围、事件类型等多维度过滤查询。
 * </p>
 */
@Service
public class LogQueryServiceImpl implements LogQueryService {

    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param changeLogMapper      权限变更日志数据访问层
     * @param operationLogMapper   操作日志数据访问层
     * @param engine               权限查询引擎
     */
    public LogQueryServiceImpl(PermissionChangeLogMapper changeLogMapper,
                                OperationLogMapper operationLogMapper,
                                PermQueryEngine engine) {
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.engine = engine;
    }

    // ===== 变更日志查询 =====

    /**
     * 查询变更日志列表
     * <p>
     * 根据实体类型和实体ID过滤查询变更日志。
     * 按创建时间倒序排列。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可选过滤条件
     * @param entityId   实体ID，可选过滤条件
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 变更日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.selectByTenantEntityTypeEntityId(tenantId, entityType, entityId, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    /**
     * 统计变更日志数量
     * <p>
     * 根据实体类型和实体ID过滤统计变更日志数量。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可选过滤条件
     * @param entityId   实体ID，可选过滤条件
     * @return 变更日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countChangeLogs(Long tenantId, String entityType, Long entityId) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.countByTenantEntityTypeEntityId(tenantId, entityType, entityId);
    }

    /**
     * 多条件过滤查询变更日志
     * <p>
     * 支持按用户、角色、时间范围、事件类型等多维度过滤查询。
     * 用于权限变更历史的高级查询。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param userId     用户ID，可选过滤条件
     * @param roleId     角色ID，可选过滤条件
     * @param since      开始时间，可选过滤条件
     * @param until      结束时间，可选过滤条件
     * @param eventTypes 事件类型列表，可选过滤条件
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 变更日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                                       LocalDateTime since, LocalDateTime until,
                                                       List<String> eventTypes, int offset, int limit) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.selectFiltered(tenantId, userId, roleId, since, until, eventTypes, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    /**
     * 多条件过滤统计变更日志数量
     * <p>
     * 支持按用户、角色、时间范围、事件类型等多维度过滤统计。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param userId     用户ID，可选过滤条件
     * @param roleId     角色ID，可选过滤条件
     * @param since      开始时间，可选过滤条件
     * @param until      结束时间，可选过滤条件
     * @param eventTypes 事件类型列表，可选过滤条件
     * @return 变更日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                         LocalDateTime since, LocalDateTime until,
                                         List<String> eventTypes) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return changeLogMapper.countFiltered(tenantId, userId, roleId, since, until, eventTypes);
    }

    

    // ===== 操作日志查询 =====

    /**
     * 查询操作日志列表
     * <p>
     * 根据模块和操作类型过滤查询操作日志。
     * 按创建时间倒序排列。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块名称，可选过滤条件
     * @param action   操作类型，可选过滤条件
     * @param offset   分页偏移量
     * @param limit    分页大小
     * @return 操作日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return operationLogMapper.selectByTenantModuleAction(tenantId, module, action, offset, limit)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    /**
     * 统计操作日志数量
     * <p>
     * 根据模块和操作类型过滤统计操作日志数量。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块名称，可选过滤条件
     * @param action   操作类型，可选过滤条件
     * @return 操作日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countOperationLogs(Long tenantId, String module, String action) {
        // 权限校验：查看系统配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        return operationLogMapper.countByTenantModuleAction(tenantId, module, action);
    }

    

    // ===== 实体转换方法 =====

    /**
     * 将PermissionChangeLog实体转换为响应对象
     *
     * @param c 权限变更日志实体
     * @return 变更日志响应对象
     */
    private ChangeLogResp toChangeLogResp(PermissionChangeLog c) {
        return new ChangeLogResp(
            c.getId(), c.getTenantId(), c.getEntityType(),
            c.getEntityId(), c.getOperation(), c.getOldSnapshot(), c.getNewSnapshot(),
            c.getDiffSnapshot(), c.getAffectedAbstractUserIds(), c.getAffectedAbstractRoleIds(),
            c.getChangeReason(), c.getChangeSource(), c.getRequestId(), c.getCreatedAt()
        );
    }

    /**
     * 将OperationLog实体转换为响应对象
     *
     * @param l 操作日志实体
     * @return 操作日志响应对象
     */
    private OperationLogResp toOperationLogResp(OperationLog l) {
        return new OperationLogResp(
            l.getId(), l.getTenantId(), l.getModule(), l.getAction(),
            l.getTargetType(), l.getTargetId(), l.getSummary(), l.getOperatorId(),
            l.getOperatorName(), l.getIpAddress(), l.getRequestId(), l.getCreatedAt()
        );
    }
}