package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.service.ConditionAppService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermissionConditionDomainServiceImpl;
import cn.ac.fage.accessmesh.permission.util.JsonValidationUtils;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限条件管理服务实现类
 * <p>
 * 提供权限条件（PermissionCondition）的CRUD操作。
 * 权限条件定义了权限生效的附加约束规则，如时间范围、数据属性等。
 * 条件规则存储为JSON格式，支持复杂的条件表达式。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 缓存失效操作在事务提交后执行，防止缓存被回滚数据污染。
 * 批量删除采用批量软删除策略，避免N+1查询问题。
 * </p>
 */
@Service
public class ConditionAppServiceImpl implements ConditionAppService {

    private final PermissionConditionMapper conditionMapper;
    private final PermQueryEngine engine;

    /**
     * 权限条件领域服务，用于缓存失效
     */
    private final PermissionConditionDomainServiceImpl conditionDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param conditionMapper           权限条件数据访问层
     * @param engine                    权限查询引擎
     * @param conditionDomainService    权限条件领域服务，用于缓存失效
     */
    public ConditionAppServiceImpl(PermissionConditionMapper conditionMapper,
                                       PermQueryEngine engine,
                                       PermissionConditionDomainServiceImpl conditionDomainService) {
        this.conditionMapper = conditionMapper;
        this.engine = engine;
        this.conditionDomainService = conditionDomainService;
    }

    /**
     * 创建权限条件
     * <p>
     * 创建新的权限条件定义。条件包含编码、名称、规则JSON、启用状态等。
     * 需要CONDITION_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含条件编码、名称、规则等
     * @param operatorId 操作者ID，可选
     * @return 创建的条件响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "permission-condition-create", targetType = "permission_condition", targetId = "#result.id()", summary = "'create permission condition ' + #req.code()")
    public ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.CONDITION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on CONDITION");
        }

        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(tenantId);
        condition.setCode(req.code());
        condition.setName(req.name());
        JsonValidationUtils.validateJson(req.conditionRules());
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

    /**
     * 获取权限条件详情
     * <p>
     * 根据条件ID查询权限条件的完整信息。
     * </p>
     *
     * @param tenantId   租户ID
     * @param conditionId 条件ID
     * @return 条件响应，不存在返回null
     */
    @Override
    public ConditionResp getCondition(Long tenantId, Long conditionId) {
        PermissionCondition condition = conditionMapper.selectValidById(conditionId, tenantId);
        return condition != null ? toConditionResp(condition) : null;
    }

    /**
     * 更新权限条件
     * <p>
     * 更新权限条件的名称、规则、启用状态、描述等属性。
     * 需要CONDITION_UPDATE权限。
     * 更新完成后在事务提交后失效相关缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含条件ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的条件响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 条件不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "permission-condition-update", targetType = "permission_condition", targetId = "#req.conditionId()", summary = "'update permission condition ' + #req.conditionId()")
    public ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.CONDITION, req.conditionId(), OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on CONDITION:" + req.conditionId());
        }

        PermissionCondition condition = conditionMapper.selectOneById(req.conditionId());
        if (condition == null || condition.getDeleteFlag() != 0L || !tenantId.equals(condition.getTenantId())) {
            throw new BizException(PermissionErrorCode.CONDITION_NOT_FOUND.getCode(), "Condition not found: " + req.conditionId());
        }
        if (req.name() != null) condition.setName(req.name());
        if (req.conditionRules() != null) {
            JsonValidationUtils.validateJson(req.conditionRules());
            condition.setConditionRules(req.conditionRules());
        }
        if (req.enabled() != null) condition.setEnabled(req.enabled());
        if (req.description() != null) condition.setDescription(req.description());
        condition.setUpdatedAt(LocalDateTime.now());
        conditionMapper.update(condition);

        // 更新后失效缓存（事务提交后执行）
        final Long conditionIdForCache = req.conditionId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    conditionDomainService.evictConditionCache(tenantId, conditionIdForCache);
                }
            });
        }
        return toConditionResp(condition);
    }

    /**
     * 查询权限条件列表
     * <p>
     * 查询租户下所有活跃的权限条件。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 条件响应列表
     */
    @Override
    public List<ConditionResp> listConditions(Long tenantId) {
        return conditionMapper.selectByTenantId(tenantId).stream().map(this::toConditionResp).collect(Collectors.toList());
    }

    /**
     * 删除单个权限条件
     * <p>
     * 软删除指定的权限条件。
     * 需要CONDITION_DELETE权限。
     * 删除完成后在事务提交后失效相关缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param conditionId 条件ID
     * @param operatorId  操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "permission-condition-remove", targetType = "permission_condition", targetId = "#conditionId", summary = "'remove permission condition ' + #conditionId")
    public void deleteCondition(Long tenantId, Long conditionId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.CONDITION, conditionId, OperationCodeConstants.DELETE)) {
            throw new SecurityException("Permission denied: DELETE on CONDITION:" + conditionId);
        }

        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition == null || condition.getDeleteFlag() != 0L || !condition.getTenantId().equals(tenantId)) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        condition.setDeleteFlag(condition.getId());
        condition.setDeletedAt(LocalDateTime.now());
        conditionMapper.update(condition);

        // 删除后失效缓存（事务提交后执行）
        final Long tenantIdForCache = tenantId;
        final Long conditionIdForCache = conditionId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    conditionDomainService.evictConditionCache(tenantIdForCache, conditionIdForCache);
                }
            });
        }
    }

    /**
     * 批量删除权限条件
     * <p>
     * 批量软删除权限条件。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要CONDITION_DELETE权限。
     * 删除完成后在事务提交后批量失效相关缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        条件ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "permission-condition-remove", targetType = "BATCH", targetId = "", summary = "'batch remove permission conditions'")
    public void deleteConditionsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.CONDITION, validInputIds, OperationCodeConstants.DELETE);

        List<PermissionCondition> entities = conditionMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream().map(PermissionCondition::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        conditionMapper.softDeleteBatch(tenantId, validIds.stream().toList(), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " permission_condition row(s)");

        // 批量删除后失效缓存（事务提交后执行）
        final Set<Long> validIdsForCache = validIds;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    conditionDomainService.evictConditionCacheBatch(tenantId, validIdsForCache);
                }
            });
        }
    }

    /**
     * 将PermissionCondition实体转换为响应对象
     *
     * @param c 权限条件实体
     * @return 条件响应对象
     */
    private ConditionResp toConditionResp(PermissionCondition c) {
        return new ConditionResp(
            c.getId(), c.getTenantId(), c.getCode(), c.getName(),
            c.getConditionRules(), c.getEnabled(), c.getDescription(), c.getCreatedAt()
        );
    }
}