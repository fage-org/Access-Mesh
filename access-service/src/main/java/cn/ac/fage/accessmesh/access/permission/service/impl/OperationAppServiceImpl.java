package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq;
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
import java.util.Map;
import java.util.Objects;
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
    private final cn.ac.fage.accessmesh.access.permission.service.domain.OperationResolutionDomainService operationResolution;

    /**
     * 构造函数注入依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     * @param typeResolutionService     类型解析服务
     * @param engine                    权限查询引擎
     */
    public OperationAppServiceImpl(OperationPermissionMapper operationPermissionMapper,
                                      TypeResolutionService typeResolutionService,
                                      PermQueryEngine engine,
                                      cn.ac.fage.accessmesh.access.permission.service.domain.OperationResolutionDomainService operationResolution) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.operationResolution = operationResolution;
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
     * 以业务键 (resourceTypeCode, code) 查询操作权限完整信息（T-PERM-028；
     * resourceTypeCode null/空白 = 全局操作）。类型级 OPERATION:VIEW 门禁。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      操作权限业务键
     * @return 操作权限响应
     * @throws SecurityException 无 VIEW 权限时抛出
     * @throws BizException      业务键查不到（20005）或资源类型不存在（20021）时抛出
     */
    @Override
    public OperationPermissionResp getOperation(Long tenantId, OperationKeyReq key) {
        // T-PERM-028：详情读门禁（类型级 OPERATION:VIEW，对齐 list 门禁先例）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION");
        }
        return toResp(selectOperationByBusinessKey(tenantId, key));
    }

    /**
     * 以业务键定位有效操作权限（专属/全局两轨）
     *
     * @param tenantId 租户ID
     * @param key      操作权限业务键
     * @return 操作权限实体
     * @throws BizException 资源类型不存在（20021）或操作不存在（20005）时抛出
     */
    private OperationPermission selectOperationByBusinessKey(Long tenantId, OperationKeyReq key) {
        OperationPermission op;
        if (key.isGlobal()) {
            op = operationPermissionMapper.selectGlobalByCode(tenantId, key.code());
        } else {
            Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", key.resourceTypeCode());
            if (resourceType == null) {
                throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "Unknown resourceTypeCode: " + key.resourceTypeCode());
            }
            op = operationPermissionMapper.selectByResourceTypeAndCode(tenantId, resourceType, key.code());
        }
        if (op == null) {
            throw new BizException(PermissionErrorCode.OPERATION_NOT_FOUND.getCode(),
                "Operation not found: " + (key.isGlobal() ? "<global>" : key.resourceTypeCode()) + ":" + key.code());
        }
        return op;
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
        // true：「专属优先、全局回退」合并——经共享解析器（与授权计划 operationCode 适用性校验
        // 同一实现，契约禁止两套逻辑；resourceTypeCode=null/缺省时无专属侧仅全局集合）
        return operationResolution.mergeGlobalFallback(
                operationPermissionMapper.selectByTenantAndResourceType(tenantId, null), resourceType)
            .stream().map(this::toResp).collect(Collectors.toList());
    }

    /**
     * 更新操作权限
     * <p>
     * 以业务键 (resourceTypeCode, code) 定位后更新名称、二进制位、继承掩码。
     * 需要OPERATION_MANAGE权限（T-PERM-028：业务键定位，编码/类型不可更新）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        操作更新请求（业务键 + 可编辑字段）
     * @param operatorId 操作者ID，可选
     * @return 更新后的操作权限响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      业务键查不到或资源类型不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "OPERATION_PERMISSION_UPDATE", targetType = "operation_permission", targetId = "#req.code()", summary = "'update operation permission ' + (#req.resourceTypeCode != null ? #req.resourceTypeCode : '<global>') + ':' + #req.code()")
    public OperationPermissionResp updateOperation(Long tenantId, OperationUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update operation");
        }

        OperationPermission op = selectOperationByBusinessKey(tenantId, new OperationKeyReq(req.resourceTypeCode(), req.code()));
        if (req.name() != null) op.setName(req.name());
        if (req.binaryBit() != null) op.setBinaryBit(req.binaryBit());
        if (req.inheritMask() != null) op.setInheritMask(req.inheritMask());
        op.setUpdatedAt(LocalDateTime.now());
        op.setUpdatedBy(operatorId);
        operationPermissionMapper.update(op);
        return toResp(op);
    }

    /**
     * 批量删除操作权限
     * <p>
     * 以业务键批量定位后批量软删除（T-PERM-028）。按 resourceTypeCode 分组批量解析，
     * 避免N+1问题。需要OPERATION_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param keys       操作权限业务键列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "OPERATION_PERMISSION_REMOVE", targetType = "operation_permission", targetId = "", summary = "'batch remove operation permissions'")
    public void deleteOperations(Long tenantId, List<OperationKeyReq> keys, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限校验
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete operations");
        }

        if (keys == null || keys.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // 业务键解析为实体：专属按 resourceTypeCode 分组批量查询，全局单独批量（避免N+1）
        List<OperationPermission> entities = resolveOperationsByKeys(tenantId, keys);

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
     * 批量解析业务键为有效操作权限实体（固定两次批量查询，T-PERM-028 复评 P2 收口）
     * <p>
     * 一次 batchResolveTypeValues 解析全部类型 + 一次跨类型
     * selectByTenantResourceTypesAndOpCodes 查询，再按 (resourceType, code) 二元组内存
     * 精确过滤；全局轨单独一次——查询次数不随请求内资源类型数增长
     * （循环内禁止单条数据库查询，project-rules §8.4.8）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keys     操作权限业务键列表
     * @return 命中的有效实体列表（未命中的键静默跳过，对齐原 ids 批删语义）
     */
    private List<OperationPermission> resolveOperationsByKeys(Long tenantId, List<OperationKeyReq> keys) {
        Set<String> typeCodes = new java.util.LinkedHashSet<>();
        Set<String> typedCodes = new java.util.LinkedHashSet<>();
        Set<String> globalCodes = new java.util.LinkedHashSet<>();
        for (OperationKeyReq key : keys) {
            if (key == null || key.code() == null || key.code().isBlank()) {
                continue;
            }
            if (key.isGlobal()) {
                globalCodes.add(key.code());
            } else {
                typeCodes.add(key.resourceTypeCode());
                typedCodes.add(key.code());
            }
        }
        if (typedCodes.isEmpty() && globalCodes.isEmpty()) {
            return List.of();
        }

        List<OperationPermission> entities = new java.util.ArrayList<>();
        if (!typedCodes.isEmpty()) {
            Map<String, Integer> typeValues = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", typeCodes);
            Set<Integer> resolvedTypes = typeValues.values().stream()
                .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!resolvedTypes.isEmpty()) {
                // 请求二元组集合（typeValue:code），未知类型码的键静默跳过
                Set<String> pairs = new java.util.HashSet<>();
                for (Map.Entry<String, Integer> entry : typeValues.entrySet()) {
                    for (String code : typedCodes) {
                        pairs.add(entry.getValue() + ":" + code);
                    }
                }
                for (OperationPermission op : operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(tenantId, resolvedTypes, typedCodes)) {
                    if (pairs.contains(op.getResourceType() + ":" + op.getCode())) {
                        entities.add(op);
                    }
                }
            }
        }
        if (!globalCodes.isEmpty()) {
            entities.addAll(operationPermissionMapper.selectGlobalByCodes(tenantId, globalCodes));
        }
        return entities;
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