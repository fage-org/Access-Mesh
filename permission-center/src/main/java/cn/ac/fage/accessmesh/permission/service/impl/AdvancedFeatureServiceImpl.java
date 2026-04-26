package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationLogTableDef.OPERATION_LOG;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConditionTableDef.PERMISSION_CONDITION;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE;

@Service
public class AdvancedFeatureServiceImpl implements AdvancedFeatureService {

    private final PermissionConditionMapper conditionMapper;
    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;

    public AdvancedFeatureServiceImpl(PermissionConditionMapper conditionMapper,
                                      PermissionConflictRuleMapper conflictRuleMapper,
                                      PermissionChangeLogMapper changeLogMapper,
                                      OperationLogMapper operationLogMapper) {
        this.conditionMapper = conditionMapper;
        this.conflictRuleMapper = conflictRuleMapper;
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
    }

    // ===== PermissionCondition =====

    @Override
    @Transactional
    public ConditionResp createCondition(ConditionCreateReq req, Long operatorId) {
        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(req.tenantId());
        condition.setCode(req.code());
        condition.setName(req.name());
        condition.setConditionRules(req.conditionRules());
        condition.setEnabled(req.enabled() != null ? req.enabled() : true);
        condition.setDescription(req.description());
        condition.setCreatedBy(operatorId);
        condition.setCreatedAt(LocalDateTime.now());
        condition.setUpdatedAt(LocalDateTime.now());
        condition.setDeleteFlag(0L);
        conditionMapper.insert(condition);
        return toConditionResp(condition);
    }

    @Override
    public ConditionResp getCondition(Long tenantId, Long conditionId) {
        PermissionCondition condition = conditionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.ID.eq(conditionId))
                .and(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        );
        return condition != null ? toConditionResp(condition) : null;
    }

    @Override
    public List<ConditionResp> listConditions(Long tenantId) {
        return conditionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        ).stream().map(this::toConditionResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCondition(Long tenantId, Long conditionId, Long operatorId) {
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition != null && condition.getDeleteFlag() == 0L && condition.getTenantId().equals(tenantId)) {
            condition.setDeleteFlag(condition.getId());
            condition.setDeletedAt(LocalDateTime.now());
            conditionMapper.update(condition);
        }
    }

    @Override
    @Transactional
    public void setConditionEnabled(Long tenantId, Long conditionId, boolean enabled, Long operatorId) {
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition != null && condition.getDeleteFlag() == 0L && condition.getTenantId().equals(tenantId)) {
            condition.setEnabled(enabled);
            condition.setUpdatedAt(LocalDateTime.now());
            conditionMapper.update(condition);
        }
    }

    // ===== PermissionConflictRule =====

    @Override
    @Transactional
    public ConflictRuleResp createConflictRule(ConflictRuleReq req, Long operatorId) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setTenantId(req.tenantId());
        rule.setBizDomainId(req.bizDomainId());
        rule.setConflictType(req.conflictType());
        rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        rule.setResourceTypeValue(req.resourceTypeValue());
        rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        rule.setDescription(req.description());
        rule.setCreatedBy(operatorId);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        rule.setDeleteFlag(0L);
        conflictRuleMapper.insert(rule);
        return toConflictRuleResp(rule);
    }

    @Override
    public ConflictRuleResp getConflictRule(Long tenantId, Long ruleId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.ID.eq(ruleId))
                .and(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        return rule != null ? toConflictRuleResp(rule) : null;
    }

    @Override
    public List<ConflictRuleResp> listConflictRules(Long tenantId) {
        return conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        ).stream().map(this::toConflictRuleResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneById(ruleId);
        if (rule != null && rule.getDeleteFlag() == 0L && rule.getTenantId().equals(tenantId)) {
            rule.setDeleteFlag(rule.getId());
            rule.setDeletedAt(LocalDateTime.now());
            conflictRuleMapper.update(rule);
        }
    }

    // ===== PermissionChangeLog =====

    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit) {
        QueryWrapper qw = QueryWrapper.create()
            .where(PERMISSION_CHANGE_LOG.TENANT_ID.eq(tenantId));
        if (entityType != null) {
            qw.and(PERMISSION_CHANGE_LOG.ENTITY_TYPE.eq(entityType));
        }
        if (entityId != null) {
            qw.and(PERMISSION_CHANGE_LOG.ENTITY_ID.eq(entityId));
        }
        qw.orderBy(PERMISSION_CHANGE_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);

        return changeLogMapper.selectListByQuery(qw)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    // ===== OperationLog =====

    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit) {
        QueryWrapper qw = QueryWrapper.create()
            .where(OPERATION_LOG.TENANT_ID.eq(tenantId));
        if (module != null) {
            qw.and(OPERATION_LOG.MODULE.eq(module));
        }
        if (action != null) {
            qw.and(OPERATION_LOG.ACTION.eq(action));
        }
        qw.orderBy(OPERATION_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);

        return operationLogMapper.selectListByQuery(qw)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    // ===== Converters =====

    private ConditionResp toConditionResp(PermissionCondition c) {
        return new ConditionResp(
            c.getId(), c.getTenantId(), c.getCode(), c.getName(),
            c.getConditionRules(), c.getEnabled(), c.getDescription(), c.getCreatedAt()
        );
    }

    private ConflictRuleResp toConflictRuleResp(PermissionConflictRule r) {
        return new ConflictRuleResp(
            r.getId(), r.getTenantId(), r.getBizDomainId(), r.getConflictType(),
            r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId(),
            r.getResourceTypeValue(), r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId(),
            r.getDescription(), r.getCreatedAt()
        );
    }

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
