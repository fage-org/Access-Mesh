package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.service.ConditionManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermissionConditionDomainServiceImpl;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConditionTableDef.PERMISSION_CONDITION;

@Service
public class ConditionManageServiceImpl implements ConditionManageService {

    private final PermissionConditionMapper conditionMapper;
    private final OperationLogDomainService operationLogDomainService;
    private final ResourcePermissionValidator permissionValidator;

    /**
     * 问题6：注入 PermissionConditionDomainServiceImpl 用于缓存失效
     */
    private final PermissionConditionDomainServiceImpl conditionDomainService;

    public ConditionManageServiceImpl(PermissionConditionMapper conditionMapper,
                                       OperationLogDomainService operationLogDomainService,
                                       ResourcePermissionValidator permissionValidator,
                                       PermissionConditionDomainServiceImpl conditionDomainService) {
        this.conditionMapper = conditionMapper;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionValidator = permissionValidator;
        this.conditionDomainService = conditionDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.CONDITION, null, OperationType.CREATE);

        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(tenantId);
        condition.setCode(req.code());
        condition.setName(req.name());
        condition.setConditionRules(req.conditionRules());
        condition.setEnabled(req.enabled() != null ? req.enabled() : true);
        condition.setDescription(req.description());
        condition.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        condition.setCreatedAt(now);
        condition.setUpdatedAt(now);
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
    @Transactional(rollbackFor = Exception.class)
    public ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.CONDITION, req.conditionId(), OperationType.UPDATE);

        PermissionCondition condition = conditionMapper.selectOneById(req.conditionId());
        if (condition == null || condition.getDeleteFlag() != 0L || !tenantId.equals(condition.getTenantId())) {
            throw new IllegalArgumentException("Condition not found: " + req.conditionId());
        }
        if (req.name() != null) condition.setName(req.name());
        if (req.conditionRules() != null) condition.setConditionRules(req.conditionRules());
        if (req.enabled() != null) condition.setEnabled(req.enabled());
        if (req.description() != null) condition.setDescription(req.description());
        condition.setUpdatedAt(LocalDateTime.now());
        conditionMapper.update(condition);

        // 问题6：更新后失效缓存
        conditionDomainService.evictConditionCache(tenantId, req.conditionId());

        return toConditionResp(condition);
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteCondition(Long tenantId, Long conditionId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.CONDITION, conditionId, OperationType.DELETE);

        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition != null && condition.getDeleteFlag() == 0L && condition.getTenantId().equals(tenantId)) {
            condition.setDeleteFlag(condition.getId());
            condition.setDeletedAt(LocalDateTime.now());
            conditionMapper.update(condition);

            // 问题6：删除后失效缓存
            conditionDomainService.evictConditionCache(tenantId, conditionId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConditionsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) return;

        Set<Long> validInputIds = ids.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) return;

        permissionValidator.validateBatch(tenantId, operatorId, ResourceTypeCode.CONDITION, validInputIds, OperationType.DELETE);

        List<PermissionCondition> entities = conditionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.ID.in(validInputIds))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        );
        if (entities.isEmpty()) return;

        Set<Long> validIds = entities.stream().map(PermissionCondition::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        conditionMapper.softDeleteBatch(tenantId, validIds.stream().toList(), now);

        // 问题6：批量删除后失效缓存
        conditionDomainService.evictConditionCacheBatch(tenantId, validIds);

        operationLogDomainService.asyncRecord(
            "perm", "permission-condition-remove", "BATCH", tenantId,
            "soft-deleted " + validIds.size() + " permission_condition row(s), ids=" + validIds,
            operatorId, null, null, tenantId
        );
    }

    private ConditionResp toConditionResp(PermissionCondition c) {
        return new ConditionResp(
            c.getId(), c.getTenantId(), c.getCode(), c.getName(),
            c.getConditionRules(), c.getEnabled(), c.getDescription(), c.getCreatedAt()
        );
    }
}