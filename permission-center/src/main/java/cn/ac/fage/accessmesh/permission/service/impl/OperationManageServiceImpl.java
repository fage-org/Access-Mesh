package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.enums.ResourceType;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.service.OperationManageService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;

@Service
public class OperationManageServiceImpl implements OperationManageService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final TypeResolutionService typeResolutionService;

    public OperationManageServiceImpl(OperationPermissionMapper operationPermissionMapper,
                                      TypeResolutionService typeResolutionService) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
    }

    @Override
    @Transactional
    public OperationPermissionResp createOperation(Long tenantId, String resourceTypeCode, String code, String name, Long binaryBit, Long inheritMask, Long operatorId) {
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
        op.setCreatedAt(LocalDateTime.now());
        op.setUpdatedAt(LocalDateTime.now());
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
    public List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode) {
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
    @Transactional
    public OperationPermissionResp updateOperation(Long tenantId, Long operationId, String name, Long binaryBit, Long inheritMask, Long operatorId) {
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
    @Transactional
    public void deleteOperation(Long tenantId, Long operationId, Long operatorId) {
        OperationPermission op = operationPermissionMapper.selectOneById(operationId);
        if (op != null && op.getDeleteFlag() == 0L && op.getTenantId().equals(tenantId)) {
            op.setDeleteFlag(op.getId());
            op.setDeletedAt(LocalDateTime.now());
            operationPermissionMapper.update(op);
        }
    }

    @Override
    @Transactional
    public void deleteOperations(Long tenantId, List<Long> operationIds, Long operatorId) {
        for (Long operationId : operationIds) {
            deleteOperation(tenantId, operationId, operatorId);
        }
    }

    private OperationPermissionResp toResp(OperationPermission op) {
        String resourceTypeName = "";
        try {
            resourceTypeName = ResourceType.fromValue(op.getResourceType() != null ? op.getResourceType() : 0).getLabel();
        } catch (IllegalArgumentException ignored) {}

        return new OperationPermissionResp(
            op.getId(), op.getTenantId(), typeResolutionService.resolveTypeCode(op.getTenantId(), "resource_type", op.getResourceType()), resourceTypeName,
            op.getCode(), op.getName(), op.getBinaryBit(), op.getInheritMask(),
            op.getCreatedAt(), op.getUpdatedAt()
        );
    }
}
