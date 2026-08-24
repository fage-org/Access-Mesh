package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceType;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.OperationAppService;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public class OperationAppServiceImpl implements OperationAppService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     * @param typeResolutionService     类型解析服务
     * @param engine                    权限查询引擎
     */
    public OperationAppServiceImpl(OperationPermissionMapper operationPermissionMapper,
                                      TypeResolutionService typeResolutionService,
                                      PermQueryEngine engine) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
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
    @OperationLog(module = "PERMISSION", action = "OPERATION_PERMISSION_CREATE", targetType = "operation_permission", targetId = "#result.id()", summary = "'create operation permission ' + #resourceTypeCode + ':' + #code")
    public OperationPermissionResp createOperation(Long tenantId, String resourceTypeCode, String code, String name, Long binaryBit, Long inheritMask, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("No permission to create operation");
        }

        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "Unknown resourceTypeCode: " + resourceTypeCode);
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
        OperationPermission op = operationPermissionMapper.selectValidById(operationId, tenantId);
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
    public List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode, String domainCode,
                                                        Boolean includeGlobalFallback) {
        // T-PERM-042：授权页操作列表读门禁（architecture §14.5 终态，类型级 OPERATION:VIEW）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION");
        }
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }
        // T-ACCESS-021 补齐 api-contract §5.3（T-PERM-040 定稿、mock 已按契约实现）：false/缺省维持
        // 现状（resourceType=null → 全量原始定义；指定类型 → 仅专属定义）
        if (!Boolean.TRUE.equals(includeGlobalFallback)) {
            return operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType)
                .stream().map(this::toResp).collect(Collectors.toList());
        }
        // true：「专属优先、全局回退」合并——resourceTypeCode=null/缺省时无专属侧，仅返回全局集合
        if (resourceType == null) {
            return operationPermissionMapper.selectGlobalOperations(tenantId)
                .stream().map(this::toResp).collect(Collectors.toList());
        }
        List<OperationPermission> dedicated = operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType);
        Set<String> dedicatedCodes = dedicated.stream().map(OperationPermission::getCode).collect(Collectors.toSet());
        List<OperationPermission> merged = new java.util.ArrayList<>(dedicated);
        operationPermissionMapper.selectGlobalOperations(tenantId).stream()
            .filter(global -> !dedicatedCodes.contains(global.getCode()))
            .forEach(merged::add);
        return merged.stream().map(this::toResp).collect(Collectors.toList());
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
    @OperationLog(module = "PERMISSION", action = "OPERATION_PERMISSION_UPDATE", targetType = "operation_permission", targetId = "#operationId", summary = "'update operation permission ' + #operationId")
    public OperationPermissionResp updateOperation(Long tenantId, Long operationId, String name, Long binaryBit, Long inheritMask, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update operation");
        }

        OperationPermission op = operationPermissionMapper.selectOneById(operationId);
        if (op == null || op.getDeleteFlag() != 0L || !op.getTenantId().equals(tenantId)) {
            throw new BizException(PermissionErrorCode.OPERATION_NOT_FOUND.getCode(), "Operation not found: " + operationId);
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
    @OperationLog(module = "PERMISSION", action = "OPERATION_PERMISSION_REMOVE", targetType = "operation_permission", targetId = "", summary = "'batch remove operation permissions'")
    public void deleteOperations(Long tenantId, List<Long> operationIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete operations");
        }

        if (operationIds == null || operationIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // 过滤null值ID
        Set<Long> validInputIds = operationIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // 批量查询（避免N+1）
        List<OperationPermission> entities = operationPermissionMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // 收集有效ID
        Set<Long> validIds = entities.stream()
            .map(OperationPermission::getId)
            .collect(Collectors.toSet());

        // 批量软删除（性能修复：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        operationPermissionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " operation_permission row(s)");
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