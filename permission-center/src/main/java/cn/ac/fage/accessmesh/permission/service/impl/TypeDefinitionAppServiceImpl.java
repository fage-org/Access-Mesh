package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
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

    /**
     * 构造函数注入依赖
     *
     * @param typeDefinitionMapper 类型定义数据访问层
     * @param engine               权限查询引擎
     */
    public TypeDefinitionAppServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                         PermQueryEngine engine) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.engine = engine;
    }

    /**
     * 创建类型定义
     * <p>
     * 创建新的类型定义实体，设置类型键、类型值、名称、描述等属性。
     * 类型定义用于系统中的各类枚举值映射，如资源类型、角色类型、用户类型等。
     * 需要TYPE_DEFINITION_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含类型键、类型值、名称等
     * @param operatorId 操作者ID，可选
     * @return 创建的类型定义响应
     * @throws SecurityException 无权限时抛出
     */
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

    /**
     * 获取类型定义详情
     * <p>
     * 根据类型定义ID查询类型的完整信息。
     * 需要TYPE_DEFINITION_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeId   类型定义ID
     * @return 类型定义响应，不存在返回null
     * @throws SecurityException 无权限时抛出
     */
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

    /**
     * 查询类型定义列表
     * <p>
     * 查询租户下所有活跃的类型定义。
     * 需要TYPE_DEFINITION_VIEW权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码，可选（当前未使用）
     * @return 类型定义响应列表
     * @throws SecurityException 无权限时抛出
     */
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

    /**
     * 更新类型定义
     * <p>
     * 更新类型定义的名称、描述、排序顺序、扩展属性等。
     * 需要TYPE_DEFINITION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含类型ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的类型定义响应
     * @throws SecurityException     无权限时抛出
     * @throws BizException          类型定义不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "type-definition-update", targetType = "type_definition", targetId = "#req.typeId()", summary = "'update type definition ' + #req.typeId()")
    public TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.TYPE_DEFINITION, req.typeId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on TYPE_DEFINITION:" + req.typeId());
        }

        TypeDefinition type = typeDefinitionMapper.selectValidById(tenantId, req.typeId());
        if (type == null) throw new BizException(PermissionErrorCode.TYPE_DEFINITION_NOT_FOUND.getCode(), "Type not found: " + req.typeId());
        if (req.name() != null) type.setName(req.name());
        if (req.description() != null) type.setDescription(req.description());
        if (req.sortOrder() != null) type.setSortOrder(req.sortOrder());
        if (req.extra() != null) type.setExtra(req.extra());
        type.setUpdatedAt(LocalDateTime.now());
        typeDefinitionMapper.update(type);
        return toTypeResp(type);
    }

    /**
     * 批量删除类型定义
     * <p>
     * 批量软删除类型定义。系统内置类型（isSystem=true）不可删除。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要TYPE_DEFINITION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        类型定义ID列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
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

    /**
     * 将TypeDefinition实体转换为响应对象
     *
     * @param t 类型定义实体
     * @return 类型定义响应对象
     */
    private TypeDefinitionResp toTypeResp(TypeDefinition t) {
        return new TypeDefinitionResp(
            t.getId(), t.getTenantId(),
            t.getTypeKey(), t.getTypeCode(), t.getTypeValue(), t.getName(),
            t.getDescription(), t.getIsSystem(), t.getSortOrder(),
            t.getExtra(), t.getCreatedAt()
        );
    }
}
