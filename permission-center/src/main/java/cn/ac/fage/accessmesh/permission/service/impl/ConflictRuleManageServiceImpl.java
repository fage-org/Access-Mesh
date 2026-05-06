package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.permission.service.ConflictRuleManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.PermissionConflictRuleTableDef;

@Service
public class ConflictRuleManageServiceImpl implements ConflictRuleManageService {

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final OperationLogDomainService operationLogDomainService;
    private final PermQueryEngine engine;

    public ConflictRuleManageServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                          OperationLogDomainService operationLogDomainService,
                                          PermQueryEngine engine) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.operationLogDomainService = operationLogDomainService;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        engine.validate(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, null, OperationCodeConstants.CREATE);

        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setTenantId(tenantId);
        rule.setBizDomainId(req.bizDomainId());
        rule.setConflictType(req.conflictType());
        rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        rule.setResourceTypeValue(req.resourceTypeValue());
        rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        rule.setDescription(req.description());
        rule.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setDeleteFlag(0L);
        conflictRuleMapper.insert(rule);
        return toConflictRuleResp(rule);
    }

    @Override
    public ConflictRuleResp getConflictRule(Long tenantId, Long ruleId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.ID.eq(ruleId))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        return rule != null ? toConflictRuleResp(rule) : null;
    }

    @Override
    public List<ConflictRuleResp> listConflictRules(Long tenantId) {
        return conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        ).stream().map(this::toConflictRuleResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        engine.validate(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, req.id(), OperationCodeConstants.UPDATE);

        PermissionConflictRule rule = conflictRuleMapper.selectOneById(req.id());
        if (rule == null || rule.getDeleteFlag() != 0L || !tenantId.equals(rule.getTenantId())) {
            throw new IllegalArgumentException("Conflict rule not found: " + req.id());
        }
        if (req.bizDomainId() != null) rule.setBizDomainId(req.bizDomainId());
        if (req.conflictType() != null) rule.setConflictType(req.conflictType());
        if (req.firstOperationPermissionId() != null) rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        if (req.secondOperationPermissionId() != null) rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        if (req.resourceTypeValue() != null) rule.setResourceTypeValue(req.resourceTypeValue());
        if (req.firstAbstractRoleId() != null) rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        if (req.secondAbstractRoleId() != null) rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        if (req.description() != null) rule.setDescription(req.description());
        rule.setUpdatedAt(LocalDateTime.now());
        conflictRuleMapper.update(rule);
        return toConflictRuleResp(rule);
    }

    @Override
    public ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req) {
        List<PermissionConflictRule> rules = conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(req.resourceTypeValue() == null
                    ? PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.ID.isNotNull()
                    : PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.RESOURCE_TYPE_VALUE.eq(req.resourceTypeValue()))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        List<ConflictRuleResp> matched = rules.stream().filter(rule ->
            (Objects.equals(rule.getFirstOperationPermissionId(), req.firstOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.secondOperationPermissionId()))
                || (Objects.equals(rule.getFirstOperationPermissionId(), req.secondOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.firstOperationPermissionId()))
        ).map(this::toConflictRuleResp).collect(Collectors.toList());
        return new ConflictDetectResp(!matched.isEmpty(), matched);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        engine.validate(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, ruleId, OperationCodeConstants.DELETE);

        PermissionConflictRule rule = conflictRuleMapper.selectOneById(ruleId);
        if (rule != null && rule.getDeleteFlag() == 0L && rule.getTenantId().equals(tenantId)) {
            rule.setDeleteFlag(rule.getId());
            rule.setDeletedAt(LocalDateTime.now());
            conflictRuleMapper.update(rule);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) return;

        Set<Long> validInputIds = ids.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) return;

        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.CONFLICT_RULE, validInputIds, OperationCodeConstants.DELETE);

        List<PermissionConflictRule> entities = conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.ID.in(validInputIds))
                .and(PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        if (entities.isEmpty()) return;

        Set<Long> validIds = entities.stream().map(PermissionConflictRule::getId).collect(Collectors.toSet());
        // Batch soft delete (performance fix: use single SQL instead of loop)
        LocalDateTime now = LocalDateTime.now();
        conflictRuleMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        operationLogDomainService.asyncRecord(
            "perm", "conflict-rule-remove", "BATCH", tenantId,
            "soft-deleted " + validIds.size() + " permission_conflict_rule row(s), ids=" + validIds,
            operatorId, null, null, tenantId
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
}
