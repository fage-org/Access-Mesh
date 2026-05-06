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
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.OperationLogTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.PermissionChangeLogTableDef;

/**
 * Log query service implementation.
 * Read-only service for change log and operation log retrieval.
 */
@Service
public class LogQueryServiceImpl implements LogQueryService {

    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final PermQueryEngine engine;

    public LogQueryServiceImpl(PermissionChangeLogMapper changeLogMapper,
                                OperationLogMapper operationLogMapper,
                                PermQueryEngine engine) {
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.engine = engine;
    }

    // ===== ChangeLog =====

    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        QueryWrapper qw = changeLogBaseQuery(tenantId, entityType, entityId);
        qw.orderBy(PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);
        return changeLogMapper.selectListByQuery(qw)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    @Override
    public long countChangeLogs(Long tenantId, String entityType, Long entityId) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        return changeLogMapper.selectCountByQuery(changeLogBaseQuery(tenantId, entityType, entityId));
    }

    @Override
    public List<ChangeLogResp> listChangeLogsForUser(Long tenantId, Long userId, int offset, int limit) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        return changeLogMapper.selectByAffectedUser(tenantId, userId, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    @Override
    public long countChangeLogsForUser(Long tenantId, Long userId) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        return changeLogMapper.countByAffectedUser(tenantId, userId);
    }

    @Override
    public List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                                       LocalDateTime since, LocalDateTime until,
                                                       List<String> eventTypes, int offset, int limit) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        return changeLogMapper.selectFiltered(tenantId, userId, roleId, since, until, eventTypes, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    @Override
    public long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                         LocalDateTime since, LocalDateTime until,
                                         List<String> eventTypes) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        return changeLogMapper.countFiltered(tenantId, userId, roleId, since, until, eventTypes);
    }

    private QueryWrapper changeLogBaseQuery(Long tenantId, String entityType, Long entityId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG.TENANT_ID.eq(tenantId));
        if (entityType != null) {
            qw.and(PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG.ENTITY_TYPE.eq(entityType));
        }
        if (entityId != null) {
            qw.and(PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG.ENTITY_ID.eq(entityId));
        }
        return qw;
    }

    // ===== OperationLog =====

    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        QueryWrapper qw = operationLogBaseQuery(tenantId, module, action);
        qw.orderBy(OperationLogTableDef.OPERATION_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);
        return operationLogMapper.selectListByQuery(qw)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    @Override
    public long countOperationLogs(Long tenantId, String module, String action) {
        // Permission check - VIEW operation on SYSTEM_CONFIG
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

        return operationLogMapper.selectCountByQuery(operationLogBaseQuery(tenantId, module, action));
    }

    private QueryWrapper operationLogBaseQuery(Long tenantId, String module, String action) {
        QueryWrapper qw = QueryWrapper.create()
            .where(OperationLogTableDef.OPERATION_LOG.TENANT_ID.eq(tenantId));
        if (module != null) {
            qw.and(OperationLogTableDef.OPERATION_LOG.MODULE.eq(module));
        }
        if (action != null) {
            qw.and(OperationLogTableDef.OPERATION_LOG.ACTION.eq(action));
        }
        return qw;
    }

    // ===== Converters =====

    private ChangeLogResp toChangeLogResp(PermissionChangeLog c) {
        return new ChangeLogResp(
            c.getId(), c.getTenantId(), c.getBizDomainId(), c.getEntityType(),
            c.getEntityId(), c.getOperation(), c.getOldSnapshot(), c.getNewSnapshot(),
            c.getDiffSnapshot(), c.getAffectedAbstractUserIds(), c.getAffectedAbstractRoleIds(),
            c.getChangeReason(), c.getChangeSource(), c.getRequestId(), c.getCreatedAt()
        );
    }

    private OperationLogResp toOperationLogResp(OperationLog l) {
        return new OperationLogResp(
            l.getId(), l.getTenantId(), l.getModule(), l.getAction(),
            l.getTargetType(), l.getTargetId(), l.getSummary(), l.getOperatorId(),
            l.getOperatorName(), l.getIpAddress(), l.getRequestId(), l.getCreatedAt()
        );
    }
}
