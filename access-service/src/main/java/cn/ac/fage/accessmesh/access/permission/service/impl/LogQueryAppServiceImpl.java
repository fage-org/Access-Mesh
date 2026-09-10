package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.permission.service.LogQueryAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
import org.springframework.stereotype.Service;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志查询服务实现类
 * <p>
 * 提供变更日志和操作日志的只读查询功能。
 * 门禁（审计分离）：变更日志页 listChangeLogs/countChangeLogs 需 PERMISSION_CHANGE_LOG:VIEW
 * （T-PERM-032），操作日志需 OPERATION_LOG:VIEW（T-PERM-025）。原 recent-changes 端点
 * 门禁=被查目标实例 USER:VIEW/ROLE:VIEW，该端点已随 T-PERM-059 删除（2026-09-10）。
 * 支持按实体、用户、角色、时间范围、事件类型、变更来源等多维度过滤查询。
 * </p>
 */
@Service
public class LogQueryAppServiceImpl implements LogQueryAppService {

    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final PermQueryEngine engine;
    private final TypeResolutionService typeResolutionService;

    /**
     * 构造函数注入依赖
     *
     * @param changeLogMapper         权限变更日志数据访问层
     * @param operationLogMapper      操作日志数据访问层
     * @param engine                  权限查询引擎
     * @param typeResolutionService   类型解析服务
     */
    public LogQueryAppServiceImpl(PermissionChangeLogMapper changeLogMapper,
                                OperationLogMapper operationLogMapper,
                                PermQueryEngine engine,
                                TypeResolutionService typeResolutionService) {
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.engine = engine;
        this.typeResolutionService = typeResolutionService;
    }

    // ===== 变更日志查询 =====

    /**
     * 查询变更日志列表（变更日志页，T-PERM-032 筛选全集）
     *
     * @param tenantId       租户ID
     * @param entityType     实体类型，可选
     * @param entityId       实体ID，可选
     * @param eventType      diff_snapshot.eventType，可选（单选）
     * @param changeSource   变更来源（MANUAL/SERVICE_SYNC），可选
     * @param affectedUserId 受影响用户ID，可选
     * @param affectedRoleId 受影响角色ID，可选
     * @param since          创建时间下界（含），可选
     * @param until          创建时间上界（含），可选
     * @param offset         分页偏移量
     * @param limit          分页大小
     * @return 变更日志列表
     * @throws SecurityException 无 PERMISSION_CHANGE_LOG:VIEW 权限时抛出
     */
    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId,
                                       String eventType, String changeSource,
                                       Long affectedUserId, Long affectedRoleId,
                                       LocalDateTime since, LocalDateTime until,
                                       int offset, int limit) {
        // T-PERM-032 审计分离：变更日志页切独立 PERMISSION_CHANGE_LOG:VIEW（对齐 OPERATION_LOG 先例）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.PERMISSION_CHANGE_LOG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on PERMISSION_CHANGE_LOG");
        }

        return changeLogMapper.selectPageByCondition(tenantId, normalize(entityType), entityId,
                affectedUserId, affectedRoleId, since, until,
                toEventTypeList(eventType), normalize(changeSource), offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }    /**
     * 统计变更日志数量（条件与 {@link #listChangeLogs} 一致）
     *
     * @return 变更日志数量
     * @throws SecurityException 无 PERMISSION_CHANGE_LOG:VIEW 权限时抛出
     */
    @Override
    public long countChangeLogs(Long tenantId, String entityType, Long entityId,
                         String eventType, String changeSource,
                         Long affectedUserId, Long affectedRoleId,
                         LocalDateTime since, LocalDateTime until) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.PERMISSION_CHANGE_LOG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on PERMISSION_CHANGE_LOG");
        }

        return changeLogMapper.countByCondition(tenantId, normalize(entityType), entityId,
                affectedUserId, affectedRoleId, since, until,
                toEventTypeList(eventType), normalize(changeSource));
    }

    // ===== 操作日志查询 =====

    /**
     * 查询操作日志列表
     * <p>
     * 查询系统的操作日志，支持按模块、操作、操作者、时间范围、目标类型过滤。
     * 需要OPERATION_LOG_VIEW权限（T-PERM-025 审计分离：不再复用 SYSTEM_CONFIG:VIEW）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param module     模块名称，可选过滤条件
     * @param action     操作类型，可选过滤条件（精确匹配）
     * @param operatorId 操作者用户ID，可选
     * @param since      创建时间下界（含），可选
     * @param until      创建时间上界（含），可选
     * @param targetType 目标类型，可选
     * @param offset     分页偏移量
     * @param limit      分页大小
     * @return 操作日志响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action,
                                                     Long operatorId, LocalDateTime since, LocalDateTime until,
                                                     String targetType, int offset, int limit) {
        Long operatorCtxId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorCtxId, ResourceTypeCode.OPERATION_LOG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION_LOG");
        }

        return operationLogMapper.selectPageByCondition(tenantId, normalize(module), normalize(action),
                operatorId, since, until, normalize(targetType), offset, limit)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    /**
     * 统计操作日志数量
     * <p>
     * 统计符合条件的操作日志总数。
     * 需要OPERATION_LOG_VIEW权限（T-PERM-025 审计分离）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param module     模块名称，可选过滤条件
     * @param action     操作类型，可选过滤条件
     * @param operatorId 操作者用户ID，可选
     * @param since      创建时间下界（含），可选
     * @param until      创建时间上界（含），可选
     * @param targetType 目标类型，可选
     * @return 操作日志总数
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public long countOperationLogs(Long tenantId, String module, String action,
                                    Long operatorId, LocalDateTime since, LocalDateTime until, String targetType) {
        Long operatorCtxId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorCtxId, ResourceTypeCode.OPERATION_LOG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION_LOG");
        }

        return operationLogMapper.countByCondition(tenantId, normalize(module), normalize(action),
            operatorId, since, until, normalize(targetType));
    }

    /**
     * 查询操作日志 action 字典
     * <p>
     * 返回 operation_log 当前实际存在的 action 去重集合（按 module 可选过滤），
     * 供前端筛选下拉动态拉取（T-PERM-025）。返回实际存在的事件码而非维护端枚举，
     * 避免与 @OperationLog 注解清单双轨漂移。
     * 需要OPERATION_LOG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块，可选过滤
     * @return 去重 action 集合（字典序）
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<String> listActionOptions(Long tenantId, String module) {
        Long operatorCtxId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorCtxId, ResourceTypeCode.OPERATION_LOG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION_LOG");
        }

        return operationLogMapper.selectDistinctActions(tenantId, normalize(module));
    }

    /**
     * 过滤参数规整：空白串归一为 null（与 SQL <if> 判空语义一致）
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ===== 实体转换方法 =====

    /** 页面单选 eventType 规整为单元素集合（空白归 null 不过滤） */
    private List<String> toEventTypeList(String eventType) {
        return eventType != null && !eventType.isBlank() ? List.of(eventType.trim()) : null;
    }

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
            c.getChangeReason(), c.getChangeSource(), c.getCreatedBy(), c.getRequestId(), c.getCreatedAt()
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
