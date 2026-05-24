package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.permission.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 类型定义应用服务实现类
 * <p>
 * 提供类型定义的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class TypeDefinitionAppServiceImpl implements TypeDefinitionAppService {

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final PermQueryEngine engine;

    public TypeDefinitionAppServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                         PermQueryEngine engine) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "type-definition-create", targetType = "type_definition", targetId = "#result.id()", summary = "'create type definition ' + #req.typeKey() + ':' + #req.typeValue()")
    public TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on TYPE_DEFINITION");
        }

        TypeDefinition type = new TypeDefinition();
        type.setTenantId(tenantId);
        type.setTypeKey(req.typeKey());
        type.setTypeValue(req.typeValue());
        type.setName(req.name());
        type.setDescription(req.description());
        type.setIsSystem(req.isSystem() != null ? req.isSystem() : false);
        type.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        type.setExtra(req.extra());
        type.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        type.setCreatedAt(now);
        type.setUpdatedAt(now);
        type.setDeleteFlag(0L);
        typeDefinitionMapper.insert(type);
        return toTypeResp(type);
    }

    @Override
    @Transactional(readOnly = true)
    public TypeDefinitionResp getType(Long tenantId, Long typeId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, typeId, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION:" + typeId);
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, typeId);
        return type != null ? toTypeResp(type) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeDefinitionResp> listTypes(Long tenantId, String domainCode) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on TYPE_DEFINITION");
        }

        return typeDefinitionMapper.selectByTenantId(tenantId)
            .stream().map(this::toTypeResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "type-definition-update", targetType = "type_definition", targetId = "#req.typeId()", summary = "'update type definition ' + #req.typeId()")
    public TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, req.typeId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + req.typeId());
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, req.typeId());
        if (type == null) throw new IllegalArgumentException("Type not found: " + req.typeId());
        if (req.name() != null) type.setName(req.name());
        if (req.description() != null) type.setDescription(req.description());
        if (req.sortOrder() != null) type.setSortOrder(req.sortOrder());
        if (req.extra() != null) type.setExtra(req.extra());
        type.setUpdatedAt(LocalDateTime.now());
        typeDefinitionMapper.update(type);
        return toTypeResp(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "type-definition-remove", targetType = "BATCH", targetId = "", summary = "'batch remove type definitions'")
    public void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, validInputIds, OperationCodeConstants.MANAGE);

        List<TypeDefinition> entities = typeDefinitionMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream()
            .filter(e -> !Boolean.TRUE.equals(e.getIsSystem()))
            .map(TypeDefinition::getId)
            .collect(Collectors.toSet());

        if (validIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        typeDefinitionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " type_definition row(s)");
    }

    private TypeDefinitionResp toTypeResp(TypeDefinition t) {
        return new TypeDefinitionResp(
            t.getId(), t.getTenantId(),
            t.getTypeKey(), t.getTypeCode(), t.getTypeValue(), t.getName(),
            t.getDescription(), t.getIsSystem(), t.getSortOrder(),
            t.getExtra(), t.getCreatedAt()
        );
    }
}
