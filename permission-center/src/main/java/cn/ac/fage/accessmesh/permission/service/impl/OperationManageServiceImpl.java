package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.enums.ResourceType;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.OperationManageService;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;

@Service
public class OperationManageServiceImpl implements OperationManageService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final AuthorizationService authorizationService;
    private final OperationLogDomainService operationLogDomainService;
    private final ResourcePermissionValidator permissionValidator;

    public OperationManageServiceImpl(OperationPermissionMapper operationPermissionMapper,
                                      TypeResolutionService typeResolutionService,
                                      AuthorizationService authorizationService,
                                      OperationLogDomainService operationLogDomainService,
                                      ResourcePermissionValidator permissionValidator) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.authorizationService = authorizationService;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationPermissionResp createOperation(Long tenantId, String resourceTypeCode, String code, String name, Long binaryBit, Long inheritMask, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission check
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationType.CREATE)) {
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

    @Override
    public OperationPermissionResp getOperation(Long tenantId, Long operationId) {
        OperationPermission op = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.ID.eq(operationId))
                .and(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );
        return op != null ? toResp(op) : null;
    }

    @Override
    public List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
            .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceType));
        }
        return operationPermissionMapper.selectListByQuery(qw)
            .stream().map(this::toResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationPermissionResp updateOperation(Long tenantId, Long operationId, String name, Long binaryBit, Long inheritMask, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission check
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationType.MANAGE)) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOperation(Long tenantId, Long operationId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission check
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationType.MANAGE)) {
            throw new SecurityException("No permission to delete operation");
        }

        OperationPermission op = operationPermissionMapper.selectOneById(operationId);
        if (op != null && op.getDeleteFlag() == 0L && op.getTenantId().equals(tenantId)) {
            op.setDeleteFlag(op.getId());
            op.setDeletedAt(LocalDateTime.now());
            operationPermissionMapper.update(op);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOperations(Long tenantId, List<Long> operationIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission check (added - was missing)
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationType.MANAGE)) {
            throw new SecurityException("No permission to delete operations");
        }

        if (operationIds == null || operationIds.isEmpty()) {
            return;
        }

        // Filter out null IDs
        Set<Long> validInputIds = operationIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            return;
        }

        // Batch query (avoid N+1)
        List<OperationPermission> entities = operationPermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.ID.in(validInputIds))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (entities.isEmpty()) {
            return;
        }

        // Collect valid IDs
        Set<Long> validIds = entities.stream()
            .map(OperationPermission::getId)
            .collect(Collectors.toSet());

        // Batch soft delete (performance fix: use single SQL instead of loop)
        LocalDateTime now = LocalDateTime.now();
        operationPermissionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        // Log record
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

    private OperationPermissionResp toResp(OperationPermission op) {
        String resourceTypeName = ResourceType.safeGetLabel(op.getResourceType());

        return new OperationPermissionResp(
            op.getId(), op.getTenantId(), typeResolutionService.resolveTypeCode(op.getTenantId(), "resource_type", op.getResourceType()), resourceTypeName,
            op.getCode(), op.getName(), op.getBinaryBit(), op.getInheritMask(),
            op.getCreatedAt(), op.getUpdatedAt()
        );
    }
}
