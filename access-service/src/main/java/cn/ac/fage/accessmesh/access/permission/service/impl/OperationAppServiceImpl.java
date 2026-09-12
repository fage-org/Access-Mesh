package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceType;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.OperationAppService;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.service.domain.GrantOriginDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import cn.ac.fage.accessmesh.common.cache.CacheService;
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
    private final CacheService cacheService;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final GrantOriginDomainService grantOriginDomainService;
    private final TreeWriteLockSupport treeWriteLockSupport;

    /**
     * 构造函数注入依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     * @param typeResolutionService     类型解析服务
     * @param engine                    权限查询引擎
     * @param cacheService              统一缓存服务（OPERATION_PERMISSIONS_BY_TYPE 写路径失效，T-PERM-047）
     * @param typeDefinitionMapper      类型定义数据访问层（追加操作的授权根钩子目标类型装载，T-PERM-062）
     * @param grantOriginDomainService  类型授权根域服务（自定义类型追加操作同事务补种，T-PERM-062）
     * @param treeWriteLockSupport      树写锁（resource_type 类型生命周期写路径共持 RESOURCE_ENTITY 锁，T-PERM-062 评审批次）
     */
    public OperationAppServiceImpl(OperationPermissionMapper operationPermissionMapper,
                                      TypeResolutionService typeResolutionService,
                                      PermQueryEngine engine,
                                      CacheService cacheService,
                                      TypeDefinitionMapper typeDefinitionMapper,
                                      GrantOriginDomainService grantOriginDomainService,
                                      TreeWriteLockSupport treeWriteLockSupport) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.cacheService = cacheService;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.grantOriginDomainService = grantOriginDomainService;
        this.treeWriteLockSupport = treeWriteLockSupport;
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
    @PermissionChange
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
        // T-PERM-062 评审批次（代码轨 P2-1）：操作创建目标恒属 resource_type 族，与类型生命周期
        // 写路径（createType/updateType/deleteTypesByIds 均持 RESOURCE_ENTITY 树写锁）共持同锁——
        // 闭合并发交错：无锁时「追加操作读旧 owner → 所有者变更先提交 → 补种落旧 owner」造成旧
        // owner 持不可经 apply-grant-plan 移除的种子行、新 owner 缺该操作位；与类型删除交错亦会
        // 漏级联在途操作行。锁内重读类型行（extra/isSystem 以锁内快照为准）
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        TypeDefinition typeDef = typeDefinitionMapper.selectByTypeKeyAndCode(tenantId, "resource_type", resourceTypeCode);
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
        // T-PERM-062：自定义 resource_type 追加操作同事务向类型所有者补种该操作位首授行——
        // 不钩则死锁转移到第五个操作（EXPORT 位 16 先例：类型创建只种 CRUD 四位）；is_system
        // 类型不钩（内置类型转授链收窄是既有产品选择，T-PERM-027 口径维持）。所有者以
        // type_definition.extra.grantOriginRole 为准（缺失缺省引导角色；解析失败整单回滚）
        if (typeDef != null && !Boolean.TRUE.equals(typeDef.getIsSystem())) {
            Long ownerRoleId = grantOriginDomainService.resolveOwnerRoleId(tenantId, typeDef.getExtra());
            grantOriginDomainService.seedAuthorityRootGrants(
                tenantId, ownerRoleId, resourceType, List.of(op.getBinaryBit()), operatorId);
            PermissionChangeContext.markRoles(tenantId, ownerRoleId);
        }
        // T-PERM-047：新增操作改变该类型的操作集合，提交后失效 per-type 缓存
        // （引擎位掩码按 op_perm:{type} 缓存全量操作 Map，L1 60m/L2 120m TTL 不兜底变更）
        cacheService.evictAfterCommit(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId,
            PermCacheCatalog.operationPermissionsByTypeKey(resourceType));
        return toResp(op);
    }

    /**
     * 获取操作权限详情
     * <p>
     * 以业务键 (resourceTypeCode, code) 查询操作权限完整信息（T-PERM-028；
     * 全局操作概念已退役，resourceTypeCode 必填）。类型级 OPERATION:VIEW 门禁。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      操作权限业务键
     * @return 操作权限响应
     * @throws SecurityException 无 VIEW 权限时抛出
     * @throws BizException      业务键查不到（20005）或资源类型不存在（20021）时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public OperationPermissionResp getOperation(Long tenantId, OperationKeyReq key) {
        // T-PERM-028：详情读门禁（类型级 OPERATION:VIEW，对齐 list 门禁先例）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION");
        }
        return toResp(selectOperationByBusinessKey(tenantId, key));
    }

    /**
     * 以业务键定位有效操作权限（全局操作已退役：resourceTypeCode 必填）
     *
     * @param tenantId 租户ID
     * @param key      操作权限业务键
     * @return 操作权限实体
     * @throws BizException 资源类型不存在（20021）或操作不存在（20005）时抛出
     */
    private OperationPermission selectOperationByBusinessKey(Long tenantId, OperationKeyReq key) {
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", key.resourceTypeCode());
        if (resourceType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "Unknown resourceTypeCode: " + key.resourceTypeCode());
        }
        OperationPermission op = operationPermissionMapper.selectByResourceTypeAndCode(tenantId, resourceType, key.code());
        if (op == null) {
            throw new BizException(PermissionErrorCode.OPERATION_NOT_FOUND.getCode(),
                "Operation not found: " + key.resourceTypeCode() + ":" + key.code());
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
     * @return 操作权限响应列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<OperationPermissionResp> listOperations(Long tenantId, String resourceTypeCode) {
        // T-PERM-042：授权页操作列表读门禁（architecture §14.5 终态，类型级 OPERATION:VIEW）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.OPERATION, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on OPERATION");
        }
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
            // 未知类型 fail-closed：返回空列表而非回退全量（与 role-resource-permission/list 同口径）
            if (resourceType == null) {
                return List.of();
            }
        }
        // 全局操作概念已退役（2026-08-30 设计定案）：操作定义仅按类型返回，
        // 原 includeGlobalFallback 合并参数随概念一并退役
        List<OperationPermission> operations = operationPermissionMapper
            .selectByTenantAndResourceType(tenantId, resourceType);
        if (operations.isEmpty()) {
            return List.of();
        }
        // 类型反解走批量（逐项 resolveTypeCode 为循环内类型解析禁止模式——冷缓存 N+1）；
        // 批量查询只返回未软删类型——类型定义已删除的操作行（孤儿行）fail-closed 过滤，
        // 不回退逐项解析（回退会在冷缓存逐行单查且返回 null 违反契约「恒非空」）
        Set<Integer> typeValues = operations.stream()
            .map(OperationPermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> typeCodes = typeValues.isEmpty() ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", typeValues);
        return operations.stream()
            .filter(op -> typeCodes.containsKey(op.getResourceType()))
            .map(op -> toResp(op, typeCodes))
            .collect(Collectors.toList());
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
    @OperationLog(module = "PERMISSION", action = "OPERATION_PERMISSION_UPDATE", targetType = "operation_permission", targetId = "#req.code()", summary = "'update operation permission ' + #req.resourceTypeCode + ':' + #req.code()")
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
        // T-PERM-047：位值/继承掩码变更改变覆盖判定输入，提交后失效 per-type 缓存
        // （否则 TTL 窗口内引擎按旧位值判定，已授权角色语义静默翻转）
        cacheService.evictAfterCommit(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId,
            PermCacheCatalog.operationPermissionsByTypeKey(op.getResourceType()));
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
        // T-PERM-047：已删操作在 TTL 窗口内仍参与覆盖判定（陈旧 Map 含已删行），
        // 按受影响类型集合批量失效 per-type 缓存（同类型去重一次提交）
        Set<String> affectedTypeKeys = entities.stream()
            .map(OperationPermission::getResourceType)
            .filter(Objects::nonNull)
            .map(PermCacheCatalog::operationPermissionsByTypeKey)
            .collect(Collectors.toSet());
        if (!affectedTypeKeys.isEmpty()) {
            cacheService.evictBatchAfterCommit(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, affectedTypeKeys);
        }
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " operation_permission row(s)");
    }

    /**
     * 批量解析业务键为有效操作权限实体（固定一次跨类型批量查询，T-PERM-028 复评 P2 收口；
     * 全局轨已随概念退役移除）
     * <p>
     * 一次 batchResolveTypeValues 解析全部类型 + 一次跨类型
     * selectByTenantResourceTypesAndOpCodes 查询，再按 (resourceType, code) 二元组内存
     * 精确过滤——查询次数不随请求内资源类型数增长
     * （循环内禁止单条数据库查询，project-rules §8.4.8）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keys     操作权限业务键列表
     * @return 命中的有效实体列表（未命中的键静默跳过，对齐原 ids 批删语义）
     */
    private List<OperationPermission> resolveOperationsByKeys(Long tenantId, List<OperationKeyReq> keys) {
        // 保留 typeCode → codes 分组（二元组必须按原始请求构造，拍平成两个集合再做笛卡尔
        // 会误命中 ROLE:DELETE / USER:VIEW 等未请求组合——复评 P1 回归修复）
        Map<String, Set<String>> typedByTypeCode = new java.util.LinkedHashMap<>();
        for (OperationKeyReq key : keys) {
            if (key == null || key.code() == null || key.code().isBlank()
                || key.resourceTypeCode() == null || key.resourceTypeCode().isBlank()) {
                continue;
            }
            typedByTypeCode.computeIfAbsent(key.resourceTypeCode(), k -> new java.util.LinkedHashSet<>()).add(key.code());
        }
        if (typedByTypeCode.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> typeValues = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", typedByTypeCode.keySet());
        Set<Integer> resolvedTypes = typeValues.values().stream()
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (resolvedTypes.isEmpty()) {
            return List.of();
        }
        // 请求二元组集合（typeValue:code）：仅由「已解析类型 × 该类型实际请求的码」构造，
        // 未知类型码的键静默跳过；跨类型 SQL 的笛卡尔超集行由此精确过滤
        Set<String> pairs = new java.util.HashSet<>();
        for (Map.Entry<String, Set<String>> entry : typedByTypeCode.entrySet()) {
            Integer typeValue = typeValues.get(entry.getKey());
            if (typeValue == null) {
                continue;
            }
            for (String code : entry.getValue()) {
                pairs.add(BusinessKeys.operationCodeKey(typeValue, code));
            }
        }
        Set<String> allTypedCodes = typedByTypeCode.values().stream()
            .flatMap(Set::stream).collect(Collectors.toSet());
        List<OperationPermission> entities = new java.util.ArrayList<>();
        for (OperationPermission op : operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(tenantId, resolvedTypes, allTypedCodes)) {
            if (pairs.contains(BusinessKeys.operationCodeKey(op.getResourceType(), op.getCode()))) {
                entities.add(op);
            }
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
            op.getId(), op.getTenantId(),
            typeResolutionService.resolveTypeCode(op.getTenantId(), "resource_type", op.getResourceType()),
            resourceTypeName,
            op.getCode(), op.getName(), op.getBinaryBit(), op.getInheritMask(),
            op.getCreatedAt(), op.getUpdatedAt()
        );
    }

    /** 列表路径专用：类型码只消费批量反解结果，不做逐项回退（孤儿行由调用方 fail-closed 过滤） */
    private OperationPermissionResp toResp(OperationPermission op, Map<Integer, String> typeCodeByValue) {
        String resourceTypeName = ResourceType.safeGetLabel(op.getResourceType());

        return new OperationPermissionResp(
            op.getId(), op.getTenantId(),
            typeCodeByValue.get(op.getResourceType()),
            resourceTypeName,
            op.getCode(), op.getName(), op.getBinaryBit(), op.getInheritMask(),
            op.getCreatedAt(), op.getUpdatedAt()
        );
    }
}