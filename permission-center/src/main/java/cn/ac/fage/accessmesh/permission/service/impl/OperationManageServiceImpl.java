package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.enums.ResourceType;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.OperationManageService;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef;

/**
 * 操作权限管理服务实现类
 * <p>
 * 提供操作权限（OperationPermission）的CRUD操作。
 * 操作权限定义了系统支持的各种操作类型，如查看、编辑、删除、管理等。
 * 每个操作权限有二进制位用于位运算权限匹配，继承掩码用于权限继承。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量删除操作采用批量软删除策略，避免N+1查询问题。
 * </p>
 */
@Service
public class OperationManageServiceImpl implements OperationManageService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final AuthorizationService authorizationService;
    private final OperationLogDomainService operationLogDomainService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     * @param typeResolutionService     类型解析服务
     * @param authorizationService      授权服务
     * @param operationLogDomainService 操作日志领域服务
     * @param engine                    权限查询引擎
     */
    public OperationManageServiceImpl(OperationPermissionMapper operationPermissionMapper,
                                      TypeResolutionService typeResolutionService,
                                      AuthorizationService authorizationService,
                                      OperationLogDomainService operationLogDomainService,
                                      PermQueryEngine engine) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.authorizationService = authorizationService;
        this.operationLogDomainService = operationLogDomainService;
        this.engine = engine;
    }

    /**
     * 创建操作权限
     * <p>
     * 创建新的操作权限定义。操作权限与资源类型绑定，
     * 定义了该资源类型支持的操作。需要OPERATION_CREATE权限。
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceTypeCode 资源类型编码
     * @param code            操作码（如VIEW、EDIT、DELETE）
     * @param name            操作名称
     * @param binaryBit       二进制位，用于位运算权限匹配
     * @param inheritMask     继承掩码，用于权限继承计算
     * @param operatorId      操作者ID，可选
     * @return 创建的操作权限响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 资源类型不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationPermissionResp createOperation(Long tenantId, String resourceTypeCode, String code, String name, Long binaryBit, Long inheritMask, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("No permission to create operation");
        }

        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceType == null) {
            throw new IllegalArgumentException("Unknown resourceTypeCode: " + resourceTypeCode);
        }
        OperationPermission op = new OperationPermission();
        op.setTenantId(tenantId);
        op.setResourceType(resourceType);
        op.setCode(code);
        op.setName(name);
        op.setBinaryBit(binaryBit);
        op.setInheritMask(inheritMask);
        op.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        op.setCreatedAt(now);
        op.setUpdatedAt(now);
        op.setDeleteFlag(0L);
        operationPermissionMapper.insert(op);
        return toResp(op);
    }

    /**
     * 获取操作权限详情
     * <p>
     * 根据操作权限ID查询操作权限的完整信息。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @return 操作权限响应，不存在返回null
     */
    @Override
    public OperationPermissionResp getOperation(Long tenantId, Long operationId) {
        OperationPermission op = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OperationPermissionTableDef.OPERATION_PERMISSION.ID.eq(operationId))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );
        return op != null ? toResp(op) : null;
    }

    /**
     * 查询操作权限列表
     * <p>
     * 根据资源类型过滤查询操作权限列表。
     * 用于获取某资源类型支持的所有操作。
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceTypeCode 资源类型编码，可选过滤条件
     * @param domainCode      业务域编码，可选（当前未使用）
     * @return 操作权限响应列表
     */
    @Override
    public List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
            .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(OperationPermissionTableDef.OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceType));
        }
        return operationPermissionMapper.selectListByQuery(qw)
            .stream().map(this::toResp).collect(Collectors.toList());
    }

    /**
     * 更新操作权限
     * <p>
     * 更新操作权限的名称、二进制位、继承掩码等属性。
     * 需要OPERATION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @param name       操作名称，可选
     * @param binaryBit  二进制位，可选
     * @param inheritMask 继承掩码，可选
     * @param operatorId 操作者ID，可选
     * @return 更新后的操作权限响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 操作权限不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationPermissionResp updateOperation(Long tenantId, Long operationId, String name, Long binaryBit, Long inheritMask, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update operation");
        }

        OperationPermission op = operationPermissionMapper.selectOneById(operationId);
        if (op == null || op.getDeleteFlag() != 0L || !op.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Operation not found: " + operationId);
        }
        if (name != null) op.setName(name);
        if (binaryBit != null) op.setBinaryBit(binaryBit);
        if (inheritMask != null) op.setInheritMask(inheritMask);
        op.setUpdatedAt(LocalDateTime.now());
        op.setUpdatedBy(operatorId);
        operationPermissionMapper.update(op);
        return toResp(op);
    }

    /**
     * 删除单个操作权限
     * <p>
     * 软删除指定的操作权限。需要OPERATION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOperation(Long tenantId, Long operationId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete operation");
        }

        OperationPermission op = operationPermissionMapper.selectOneById(operationId);
        if (op != null && op.getDeleteFlag() == 0L && op.getTenantId().equals(tenantId)) {
            op.setDeleteFlag(op.getId());
            op.setDeletedAt(LocalDateTime.now());
            operationPermissionMapper.update(op);
        }
    }

    /**
     * 批量删除操作权限
     * <p>
     * 批量软删除操作权限。使用批量查询和批量软删除避免N+1问题。
     * 需要OPERATION_MANAGE权限。
     * </p>
     *
     * @param tenantId     租户ID
     * @param operationIds 操作权限ID列表
     * @param operatorId   操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOperations(Long tenantId, List<Long> operationIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete operations");
        }

        if (operationIds == null || operationIds.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validInputIds = operationIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        // 批量查询（避免N+1）
        List<OperationPermission> entities = operationPermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.ID.in(validInputIds))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (entities.isEmpty()) {
            return;
        }

        // 收集有效ID
        Set<Long> validIds = entities.stream()
            .map(OperationPermission::getId)
            .collect(Collectors.toSet());

        // 批量软删除（性能修复：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        operationPermissionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "operation-permission-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + validIds.size() + " operation_permission row(s), ids=" + validIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 将OperationPermission实体转换为响应对象
     * <p>
     * 转换时解析资源类型编码和名称。
     * </p>
     *
     * @param op 操作权限实体
     * @return 操作权限响应对象
     */
    private OperationPermissionResp toResp(OperationPermission op) {
        String resourceTypeName = ResourceType.safeGetLabel(op.getResourceType());

        return new OperationPermissionResp(
            op.getId(), op.getTenantId(), typeResolutionService.resolveTypeCode(op.getTenantId(), "resource_type", op.getResourceType()), resourceTypeName,
            op.getCode(), op.getName(), op.getBinaryBit(), op.getInheritMask(),
            op.getCreatedAt(), op.getUpdatedAt()
        );
    }
}