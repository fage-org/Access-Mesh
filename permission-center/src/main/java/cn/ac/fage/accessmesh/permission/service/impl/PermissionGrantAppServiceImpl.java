package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionChildrenReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionRemoveChildReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.enums.ConfigType;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.service.domain.*;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限授予服务实现类
 * <p>
 * 提供角色权限的批量授予、批量撤销、权限列表查询、子权限管理等核心功能。
 * 实现严格的授权传递安全校验：操作者必须拥有canGrant=true的权限才能授权给他人。
 * 使用批量解析优化性能，避免N+1查询问题。
 * 在事务提交后执行缓存失效和版本递增，确保数据一致性。
 * </p>
 * <p>
 * TODO: 构造函数依赖过多(14个)，违反单一职责原则
 * 建议：拆分为GrantValidationService/GrantExecutionService/GrantCascadeService
 * 优先级：P2（非阻塞，建议在下次大版本重构时处理）
 * </p>
 */
@Service
public class PermissionGrantAppServiceImpl implements PermissionGrantAppService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGrantAppServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final DomainConfigMapper domainConfigMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final PermissionGrantDomainService permissionGrantDomainService;
    private final AuditDomainService auditDomainService;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入所有依赖
     * <p>
     * TODO: 构造函数依赖过多(14个)，违反单一职责原则
     * 建议：拆分为GrantValidationService/GrantExecutionService/GrantCascadeService
     * 优先级：P2（非阻塞，建议在下次大版本重构时处理）
     * </p>
     */
    public PermissionGrantAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                      ResourceEntityMapper resourceEntityMapper,
                                      OperationPermissionMapper operationPermissionMapper,
                                      DomainConfigMapper domainConfigMapper,
                                      PermissionConditionMapper permissionConditionMapper,
                                      RoleResourcePermissionMapper rolePermMapper,
                                      PermissionGrantDomainService permissionGrantDomainService,
                                      AuditDomainService auditDomainService,
                                      SubjectDomainService subjectDomainService,
                                      TypeResolutionService typeResolutionService,
                                      DomainClassifyService domainClassifyService,
                                      PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.permissionConditionMapper = permissionConditionMapper;
        this.rolePermMapper = rolePermMapper;
        this.permissionGrantDomainService = permissionGrantDomainService;
        this.auditDomainService = auditDomainService;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.engine = engine;
    }

    /**
     * 批量授予角色权限
     * <p>
     * 执行严格的授权传递安全校验：
     * 1. 操作者必须对角色拥有MANAGE权限
     * 2. 授权新增权限时，操作者必须拥有该权限且canGrant=true
     * 3. 更新canGrant=true时，操作者必须已拥有canGrant=true的该权限
     * 使用批量解析（资源ID、操作ID、类型值）避免N+1查询。
     * 在事务提交后执行缓存失效和版本递增。
     *
     * TODO: 自动授予依赖权限（autoGrantForInsert）——查询resource_dependency表自动补充依赖权限
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量授予请求，包含角色标识、新增项、更新项、删除项
     * @return 授予后的角色权限列表
    * @throws BizException              角色/资源/操作/条件不存在，或角色已禁用，或请求不合法
     * @throws SecurityException         操作者无授权传递权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "role-resource-permission-grant", targetType = "abstract_role",
        targetId = "#req.roleExternalId", summary = "save granted role perms")
    public List<RolePermissionItemResp> batchGrant(Long tenantId, RoleGrantReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode()
        );
        if (roleId == null) {
            throw biz(PermissionErrorCode.ROLE_NOT_FOUND, "Role not found by business key");
        }

        // 操作者授权校验 - 对角色拥有MANAGE权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }

        boolean hasChanges = (req.add() != null && !req.add().isEmpty())
            || (req.update() != null && !req.update().isEmpty())
            || (req.remove() != null && !req.remove().isEmpty());
        if (!hasChanges) {
            throw biz(PermissionErrorCode.GRANT_REQUEST_EMPTY, "At least one of add/update/remove is required");
        }

        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        if (role == null) {
            throw biz(PermissionErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleId);
        }
        if (role.getStatus() != PermissionConstants.ENABLED_STATUS) {
            throw biz(PermissionErrorCode.ROLE_DISABLED, "Role is disabled: " + roleId);
        }

        List<RoleGrantReq.GrantAddItem> addItems = req.add() == null ? List.of() : req.add();
        List<RoleGrantReq.GrantUpdateItem> updateItems = req.update() == null ? List.of() : req.update();
        List<Long> removeItems = req.remove() == null ? List.of() : req.remove();

        // ===== 安全校验：验证新增项的授权传递权限 =====
        // 操作者必须拥有该权限且canGrant=true才能授权给他人
        if (!addItems.isEmpty()) {
            Set<PermissionGrantDomainService.GrantCheckKey> grantKeys = addItems.stream()
                .map(item -> new PermissionGrantDomainService.GrantCheckKey(
                    item.resourceTypeCode(),
                    item.resourceCode(),
                    item.operationCode(),
                    Boolean.TRUE.equals(item.scopeAll())
                ))
                .collect(Collectors.toSet());

            Map<String, PermissionGrantDomainService.GrantCheckResult> grantResults =
                permissionGrantDomainService.checkCanGrant(tenantId, operatorId, grantKeys, req.domainCode());

            // 校验每个新增项
            for (RoleGrantReq.GrantAddItem item : addItems) {
                String permKey = buildGrantKey(item);
                PermissionGrantDomainService.GrantCheckResult result = grantResults.get(permKey);

                if (result == null || !result.canGrant()) {
                    String reason = result != null ? result.reason() : "UNKNOWN";
                    throw new SecurityException(String.format(
                        "Operator %s cannot grant permission %s:%s:%s (scopeAll=%s). Reason: %s. " +
                        "Operator must have the permission with canGrant=true.",
                        operatorId, item.resourceTypeCode(),
                        item.resourceCode() == null ? "*" : item.resourceCode(),
                        item.operationCode(), item.scopeAll(), reason
                    ));
                }
            }
        }

        // ===== 安全校验：验证更新项的授权传递权限 =====
        // 如果操作者想要设置canGrant=true，必须已拥有canGrant=true的该权限
        Set<Long> updatePermIds = updateItems.stream()
            .filter(item -> item.id() != null && Boolean.TRUE.equals(item.canGrant()))
            .map(RoleGrantReq.GrantUpdateItem::id)
            .collect(Collectors.toSet());

        if (!updatePermIds.isEmpty()) {
            // 批量查询现有权限
            List<RoleResourcePermission> existingPerms = rolePermMapper.selectValidByIds(tenantId, roleId,
                new ArrayList<>(updatePermIds));
            Map<Long, RoleResourcePermission> existingPermMap = existingPerms.stream()
                .collect(Collectors.toMap(RoleResourcePermission::getId, p -> p));

            // 批量查询资源实体
            Set<Long> resourceIds = existingPerms.stream()
                .map(RoleResourcePermission::getResourceEntityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, ResourceEntity> resourceMap = resourceIds.isEmpty() ? Map.of()
                : batchLoadResources(tenantId, resourceIds);

            // 批量解析资源类型编码
            Set<Integer> resourceTypeValues = existingPerms.stream()
                .map(RoleResourcePermission::getResourceType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);
            Map<String, OperationPermission> opByTypeAndBit = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(
                batchLoadOperationsByResourceTypes(tenantId, resourceTypeValues)
                    .values()
                    .stream()
                    .flatMap(List::stream)
                    .toList()
            );

            // 校验每个更新项
            for (RoleGrantReq.GrantUpdateItem updateItem : updateItems) {
                if (updateItem.id() == null || !Boolean.TRUE.equals(updateItem.canGrant())) {
                    continue;
                }

                RoleResourcePermission existing = existingPermMap.get(updateItem.id());
                if (existing == null) {
                    continue;
                }

                // 从预加载的映射中获取资源信息
                ResourceEntity resource = existing.getResourceEntityId() == null ? null
                    : resourceMap.get(existing.getResourceEntityId());
                OperationPermission operation = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                    opByTypeAndBit,
                    existing.getResourceType(),
                    existing.getGrantedBits()
                );
                String resourceTypeCode = resourceTypeCodeMap.get(existing.getResourceType());

                // 校验操作者是否可以授权该权限
                boolean canGrant = permissionGrantDomainService.canGrantPermission(
                    tenantId, operatorId, resourceTypeCode,
                    resource == null ? null : resource.getCode(),
                    operation == null ? null : operation.getCode(),
                    Boolean.TRUE.equals(existing.getScopeAll()),
                    req.domainCode()
                );

                if (!canGrant) {
                    throw new SecurityException(String.format(
                        "Operator %s cannot set canGrant=true on permission id=%s. " +
                        "Operator must have the permission with canGrant=true.",
                        operatorId, updateItem.id()
                    ));
                }
            }
        }
        // ===== 安全校验结束 =====

        // ===== 批量解析避免N+1查询 =====
        // 1. 批量解析资源ID
        List<ResourceResolveRequest> resourceRequests = addItems.stream()
            .filter(item -> !Boolean.TRUE.equals(item.scopeAll()))
            .map(item -> new ResourceResolveRequest(
                item.resourceTypeCode(),
                item.resourceCode(),
                item.codeType(),
                req.domainCode()))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        // 2. 批量解析操作ID（按资源类型分组）
        Map<String, Set<String>> operationCodesByType = addItems.stream()
            .collect(Collectors.groupingBy(
                RoleGrantReq.GrantAddItem::resourceTypeCode,
                Collectors.mapping(RoleGrantReq.GrantAddItem::operationCode, Collectors.toSet())
            ));
        Map<String, Map<String, Long>> operationIdMapByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : operationCodesByType.entrySet()) {
            Map<String, Long> opMap = typeResolutionService.batchResolveOperationIds(tenantId, entry.getKey(), entry.getValue());
            operationIdMapByType.put(entry.getKey(), opMap);
        }

        // 3. 批量解析资源类型值
        Set<String> resourceTypeCodes = addItems.stream()
            .map(RoleGrantReq.GrantAddItem::resourceTypeCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> resourceTypeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", resourceTypeCodes);

        Set<Long> resourceIds = resourceIdMap.values().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // 批量加载资源避免N+1查询
        Map<Long, ResourceEntity> resourceById = resourceIds.isEmpty() ? Map.of()
            : batchLoadResources(tenantId, resourceIds);

        // 校验所有资源是否存在
        for (Long resId : resourceIds) {
            if (!resourceById.containsKey(resId)) {
                throw biz(PermissionErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + resId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> toInsert = new ArrayList<>();
        for (RoleGrantReq.GrantAddItem item : addItems) {
            boolean scopeAll = Boolean.TRUE.equals(item.scopeAll());
            Long resourceEntityId = null;
            if (!scopeAll) {
                ResourceResolveKey resKey = new ResourceResolveKey(
                    item.resourceTypeCode(), item.resourceCode(), item.codeType(), req.domainCode());
                resourceEntityId = resourceIdMap.get(resKey);
                if (resourceEntityId == null) {
                    throw biz(PermissionErrorCode.RESOURCE_NOT_FOUND, "resource not found: " + item.resourceCode());
                }
            }

            Map<String, Long> opMap = operationIdMapByType.getOrDefault(item.resourceTypeCode(), Map.of());
            Long operationId = opMap.get(item.operationCode());
            OperationPermission operation = operationId != null
                ? operationPermissionMapper.selectValidById(tenantId, operationId) : null;
            if (operation == null) {
                throw biz(PermissionErrorCode.OPERATION_NOT_FOUND, "operationCode not found: " + item.operationCode());
            }
            Long conditionId = null;
            if (item.conditionCode() != null && !item.conditionCode().isBlank()) {
                PermissionCondition condition = permissionConditionMapper.selectValidByCode(tenantId, item.conditionCode());
                if (condition == null || condition.getDeleteFlag() != 0L || !tenantId.equals(condition.getTenantId())) {
                    throw biz(PermissionErrorCode.CONDITION_NOT_FOUND, "conditionCode not found: " + item.conditionCode());
                }
                conditionId = condition.getId();
            }
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(roleId);
            rp.setResourceEntityId(scopeAll ? null : resourceEntityId);
            rp.setGrantedBits(operation.getBinaryBit());
            Integer finalResourceType = resourceTypeValueMap.get(item.resourceTypeCode());
            if (finalResourceType == null) {
                throw biz(PermissionErrorCode.RESOURCE_TYPE_NOT_FOUND, "resourceTypeCode not found: " + item.resourceTypeCode());
            }
            if (operation.getResourceType() != null && !operation.getResourceType().equals(finalResourceType)) {
                throw biz(PermissionErrorCode.RESOURCE_TYPE_OPERATION_MISMATCH,
                    "resourceType does not match operationPermission resource type");
            }
            rp.setResourceType(finalResourceType);
            rp.setScopeAll(scopeAll);
            rp.setCanGrant(item.canGrant() != null ? item.canGrant() : false);
            rp.setConditionId(conditionId);
            rp.setGrantSource(GrantSource.MANUAL.getValue());
            rp.setCreatedAt(now);
            rp.setUpdatedAt(now);
            rp.setDeleteFlag(0L);
            toInsert.add(rp);
        }

        // 批量软删除权限避免N+1查询
        if (!removeItems.isEmpty()) {
            permissionGrantDomainService.revokePermissions(tenantId, roleId, removeItems);
        }

        // ===== 批量处理更新项避免N+1查询 =====
        Set<Long> updateIds = updateItems.stream()
            .map(RoleGrantReq.GrantUpdateItem::id)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (!updateIds.isEmpty()) {
            // 批量查询现有权限
            List<RoleResourcePermission> existingPerms = rolePermMapper.selectValidByIds(tenantId, roleId,
                    new ArrayList<>(updateIds));
            Map<Long, RoleResourcePermission> existingPermMap = existingPerms.stream()
                .collect(Collectors.toMap(RoleResourcePermission::getId, p -> p));

            // 批量查询权限条件
            Set<String> conditionCodes = updateItems.stream()
                .map(RoleGrantReq.GrantUpdateItem::conditionCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
            Map<String, PermissionCondition> conditionMap = conditionCodes.isEmpty() ? Map.of()
                : permissionConditionMapper.selectValidByCodes(tenantId, conditionCodes).stream()
                    .collect(Collectors.toMap(PermissionCondition::getCode, Function.identity()));

            // 使用预加载的数据处理每个更新项
            for (RoleGrantReq.GrantUpdateItem updateItem : updateItems) {
                if (updateItem.id() == null) {
                    continue;
                }
                RoleResourcePermission existing = existingPermMap.get(updateItem.id());
                if (existing == null) {
                    continue;
                }
                if (updateItem.canGrant() != null) {
                    existing.setCanGrant(updateItem.canGrant());
                }
                if (updateItem.conditionCode() != null) {
                    if (updateItem.conditionCode().isBlank()) {
                        existing.setConditionId(null);
                    } else {
                        PermissionCondition condition = conditionMap.get(updateItem.conditionCode());
                        if (condition == null) {
                            throw biz(PermissionErrorCode.CONDITION_NOT_FOUND,
                                "conditionCode not found: " + updateItem.conditionCode());
                        }
                        existing.setConditionId(condition.getId());
                    }
                }
                existing.setUpdatedAt(now);
                rolePermMapper.update(existing);
            }
        }

        if (!toInsert.isEmpty()) {
            rolePermMapper.insertBatch(toInsert);
        }

        // 事务提交后执行缓存失效和版本递增（入口日志由 @OperationLog AOP 统一记录）
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    subjectDomainService.invalidateRoleCacheByRole(tenantId, roleId);
                }
            });
        }

        List<RoleResourcePermission> allPerms = rolePermMapper.selectValidByRoleId(tenantId, roleId);
        return toItemRespList(tenantId, allPerms);
    }

    /**
     * 批量撤销角色权限
     * <p>
     * 执行操作者授权校验（对角色拥有MANAGE权限）。
     * 使用批量软删除方法优化性能。
     * 版本递增和缓存失效由revokePermissions内部处理。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量撤销请求，包含角色标识和权限ID列表
    * @throws BizException      角色不存在
     * @throws SecurityException        操作者无MANAGE权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "role-resource-permission-revoke", targetType = "abstract_role",
        targetId = "#req.roleExternalId", summary = "revoke perms from role")
    public void batchRevoke(Long tenantId, BatchRevokeReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode()
        );
        if (roleId == null) {
            throw biz(PermissionErrorCode.ROLE_NOT_FOUND, "Role not found by business key");
        }

        // 操作者授权校验
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }

        List<Long> permissionIds = req.permissionIds() == null ? List.of() : req.permissionIds();

        // 性能优化：使用批量方法替代循环
        if (!permissionIds.isEmpty()) {
            permissionGrantDomainService.revokePermissions(tenantId, roleId, permissionIds);
        }

        // 事务提交后执行版本递增和缓存失效（入口日志由 @OperationLog AOP 统一记录）
        final Long revokeRoleId = roleId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    subjectDomainService.invalidateRoleCacheByRole(tenantId, revokeRoleId);
                }
            });
        }
    }

    /**
     * 查询角色权限列表
     * <p>
     * 执行操作者授权校验（对角色拥有VIEW权限）。
     * 使用批量加载避免N+1查询（资源实体、操作权限、条件）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限列表查询请求，包含角色标识
     * @return 角色权限项列表，无权限时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionItemResp> listPermissions(Long tenantId, RolePermissionListReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode()
        );
        if (roleId == null) {
            return List.of();
        }

        // 操作者授权校验 - 需要VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.VIEW)) {
            return List.of();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleId(tenantId, roleId);
        return toItemRespList(tenantId, perms);
    }

    /**
     * 查询权限的子权限列表
     * <p>
     * 查询依赖于指定权限的子权限（dependOn字段）。
     * 执行操作者授权校验（对父权限所属角色拥有VIEW权限）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      子权限查询请求，包含父权限ID
     * @return 子权限项列表，无权限时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionItemResp> listChildren(Long tenantId, RolePermissionChildrenReq req) {
        RoleResourcePermission parent = rolePermMapper.selectValidById(tenantId, null, req.permissionId());
        if (parent == null) {
            return List.of();
        }

        // 操作者授权校验 - 需要VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, parent.getAbstractRoleId(), OperationCodeConstants.VIEW)) {
            return List.of();
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleIdAndDependIds(
            tenantId, parent.getAbstractRoleId(), Set.of(req.permissionId()));
        return toItemRespList(tenantId, perms);
    }

    /**
     * 添加子权限
     * <p>
     * 为父权限添加依赖的子权限。
     * 父权限必须是顶层权限（dependOn=null）。
     * 执行SUB_PERM配置校验，限制允许的资源类型。
     * 执行操作者授权校验（对父权限所属角色拥有MANAGE权限）。
     * 使用批量解析避免N+1查询。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      添加子权限请求，包含父权限ID和子权限列表
     * @return 新增的子权限项列表
    * @throws BizException      父权限不存在、父权限不是顶层、资源类型不允许，或资源/操作/条件不存在
     * @throws SecurityException        操作者无MANAGE权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "role-resource-permission-child-add", targetType = "role_resource_permission",
        targetId = "#req.parentPermissionId", summary = "add child perms")
    public List<RolePermissionItemResp> addChildren(Long tenantId, RolePermissionAddChildReq req) {
        RoleResourcePermission parent = rolePermMapper.selectValidById(tenantId, null, req.parentPermissionId());
        if (parent == null) {
            throw biz(PermissionErrorCode.PARENT_PERMISSION_NOT_FOUND, "parentPermissionId not found");
        }

        // 操作者授权校验
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, parent.getAbstractRoleId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + parent.getAbstractRoleId());
        }

        if (parent.getDependOn() != null) {
            throw biz(PermissionErrorCode.PARENT_PERMISSION_NOT_TOP_LEVEL,
                "parentPermissionId must be a top-level permission");
        }
        ResourceEntity parentResource = parent.getResourceEntityId() == null ? null : resourceEntityMapper.selectOneById(parent.getResourceEntityId());
        DomainConfig subPermConfig = null;
        // 通过DomainClassifyService按资源类型码反查域ID，用于SUB_PERM配置查找
        if (parentResource != null) {
            String parentResourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", parentResource.getResourceType());
            Long parentBizDomainId = domainClassifyService.findDomainIdByTypeCode(tenantId, parentResourceTypeCode);
            if (parentBizDomainId != null) {
                subPermConfig = domainConfigMapper.selectValidByTypeString(tenantId, parentBizDomainId,
                        ConfigType.SUB_PERM.getValue());
            }
        }

        // ===== 批量解析避免N+1查询 =====
        List<RolePermissionAddChildReq.ChildItem> children = req.children();
        if (children.isEmpty()) {
            return List.of();
        }

        // 1. 批量解析资源类型值
        Set<String> resourceTypeCodes = children.stream()
            .map(RolePermissionAddChildReq.ChildItem::resourceTypeCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> resourceTypeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", resourceTypeCodes);

        // 2. 批量解析操作ID（按资源类型分组）
        Map<String, Set<String>> operationCodesByType = children.stream()
            .filter(c -> c.operationCode() != null && !c.operationCode().isBlank())
            .collect(Collectors.groupingBy(
                RolePermissionAddChildReq.ChildItem::resourceTypeCode,
                Collectors.mapping(RolePermissionAddChildReq.ChildItem::operationCode, Collectors.toSet())
            ));
        Map<String, Map<String, Long>> operationIdMapByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : operationCodesByType.entrySet()) {
            Map<String, Long> opMap = typeResolutionService.batchResolveOperationIds(tenantId, entry.getKey(), entry.getValue());
            operationIdMapByType.put(entry.getKey(), opMap);
        }

        // 3. 批量解析资源ID（非scopeAll项）
        List<ResourceResolveRequest> resourceRequests = children.stream()
            .filter(c -> !Boolean.TRUE.equals(c.scopeAll()) && c.resourceCode() != null && !c.resourceCode().isBlank())
            .map(c -> new ResourceResolveRequest(c.resourceTypeCode(), c.resourceCode(), c.codeType(), null))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        // 4. 批量查询权限条件
        Set<String> conditionCodes = children.stream()
            .map(RolePermissionAddChildReq.ChildItem::conditionCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, PermissionCondition> conditionMap = conditionCodes.isEmpty() ? Map.of()
            : permissionConditionMapper.selectValidByCodes(tenantId, conditionCodes).stream()
                .collect(Collectors.toMap(PermissionCondition::getCode, Function.identity()));

        List<RoleResourcePermission> inserted = new ArrayList<>();
        List<AuditDomainService.ChangeLogEntry> changeLogs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (RolePermissionAddChildReq.ChildItem child : children) {
            Integer resourceType = resourceTypeValueMap.get(child.resourceTypeCode());
            if (resourceType == null) {
                throw biz(PermissionErrorCode.RESOURCE_TYPE_NOT_FOUND,
                    "resourceTypeCode not found: " + child.resourceTypeCode());
            }
            // SUB_PERM配置校验
            if (subPermConfig != null && subPermConfig.getExtra() != null && !subPermConfig.getExtra().isBlank()
                && !parseAllowedTypeCodes(subPermConfig.getExtra()).contains(child.resourceTypeCode().trim().toUpperCase())) {
                throw biz(PermissionErrorCode.SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED,
                    "resourceTypeCode not allowed by SUB_PERM config: " + child.resourceTypeCode());
            }
            Map<String, Long> opMap = operationIdMapByType.getOrDefault(child.resourceTypeCode(), Map.of());
            Long operationId = opMap.get(child.operationCode());
            OperationPermission operation = operationId != null
                ? operationPermissionMapper.selectValidById(tenantId, operationId) : null;
            if (operation == null) {
                throw biz(PermissionErrorCode.OPERATION_NOT_FOUND, "operationCode not found: " + child.operationCode());
            }
            boolean scopeAll = Boolean.TRUE.equals(child.scopeAll());
            if (!scopeAll && (child.resourceCode() == null || child.resourceCode().isBlank())) {
                throw biz(PermissionErrorCode.RESOURCE_CODE_REQUIRED, "resourceCode is required when scopeAll=false");
            }
            Long resourceId = null;
            if (!scopeAll) {
                ResourceResolveKey resKey = new ResourceResolveKey(child.resourceTypeCode(), child.resourceCode(), child.codeType(), null);
                resourceId = resourceIdMap.get(resKey);
                if (resourceId == null) {
                    throw biz(PermissionErrorCode.RESOURCE_NOT_FOUND, "resource not found: " + child.resourceCode());
                }
            }
            Long conditionId = null;
            if (child.conditionCode() != null && !child.conditionCode().isBlank()) {
                PermissionCondition condition = conditionMap.get(child.conditionCode());
                if (condition == null) {
                    throw biz(PermissionErrorCode.CONDITION_NOT_FOUND, "conditionCode not found: " + child.conditionCode());
                }
                conditionId = condition.getId();
            }
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(parent.getAbstractRoleId());
            rp.setResourceEntityId(resourceId);
            rp.setGrantedBits(operation.getBinaryBit());
            rp.setResourceType(resourceType);
            rp.setDependOn(parent.getId());
            rp.setScopeAll(scopeAll);
            rp.setCanGrant(Boolean.TRUE.equals(child.canGrant()));
            rp.setConditionId(conditionId);
            rp.setGrantSource(GrantSource.MANUAL.getValue());
            rp.setCreatedAt(now);
            rp.setUpdatedAt(now);
            rp.setDeleteFlag(0L);
            inserted.add(rp);
            changeLogs.add(new AuditDomainService.ChangeLogEntry(
                "role_resource_permission", rp.getId(), "ADD", null, "child-added", "{}", null,
                new Long[]{parent.getAbstractRoleId()}
            ));
        }
        // 性能优化：批量插入替代循环插入
        if (!inserted.isEmpty()) {
            rolePermMapper.insertBatch(inserted);
        }
        // 版本递增和缓存失效（事务提交后执行）
        final Long roleIdForCache = parent.getAbstractRoleId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    subjectDomainService.invalidateRoleCacheByRole(tenantId, roleIdForCache);
                }
            });
        }
        auditDomainService.recordChangeLog(new AuditDomainService.ChangeLogContext(
            tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "add-child"
        ), changeLogs);
        return toItemRespList(tenantId, inserted);
    }

    /**
     * 移除子权限
     * <p>
     * 软删除指定的子权限（必须是依赖权限，即dependOn不为null）。
     * 执行操作者授权校验（对子权限所属角色拥有MANAGE权限）。
     * 在事务提交后执行缓存失效和版本递增。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      移除子权限请求，包含子权限ID
    * @throws BizException      子权限不存在、权限不是子权限
     * @throws SecurityException        操作者无MANAGE权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "role-resource-permission-child-remove", targetType = "role_resource_permission",
        targetId = "#req.permissionId", summary = "remove child perm")
    public void removeChild(Long tenantId, RolePermissionRemoveChildReq req) {
        RoleResourcePermission child = rolePermMapper.selectValidById(tenantId, null, req.permissionId());
        if (child == null) {
            throw biz(PermissionErrorCode.CHILD_PERMISSION_NOT_FOUND, "child permission not found");
        }

        // 操作者授权校验
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, child.getAbstractRoleId(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + child.getAbstractRoleId());
        }

        if (child.getDependOn() == null) {
            throw biz(PermissionErrorCode.PERMISSION_NOT_CHILD, "permission is not a child");
        }
        child.setDeleteFlag(child.getId());
        child.setDeletedAt(LocalDateTime.now());
        rolePermMapper.update(child);
        // 版本递增和缓存失效（事务提交后执行）
        final Long roleIdForCache = child.getAbstractRoleId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    subjectDomainService.invalidateRoleCacheByRole(tenantId, roleIdForCache);
                }
            });
        }
        auditDomainService.recordChangeLog(new AuditDomainService.ChangeLogContext(
            tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "remove-child"
        ), List.of(new AuditDomainService.ChangeLogEntry(
            "role_resource_permission", child.getId(), "REMOVE", "child-exists", null, "{}", null,
            new Long[]{child.getAbstractRoleId()}
        )));
    }

    /**
     * 将权限实体列表转换为响应对象列表
     * <p>
     * 使用批量加载避免N+1查询：
     * 1. 批量加载资源实体
     * 2. 批量加载操作权限
     * 3. 批量加载权限条件
     * 4. 批量解析资源类型编码
     * </p>
     *
     * @param tenantId 租户ID
     * @param perms    权限实体列表
     * @return 权限项响应列表
     */
    private List<RolePermissionItemResp> toItemRespList(Long tenantId, List<RoleResourcePermission> perms) {
        if (perms.isEmpty()) {
            return List.of();
        }
        // 批量加载资源实体避免N+1查询
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = resourceIds.isEmpty() ? Map.of() :
            batchLoadResources(tenantId, resourceIds);

        // 批量加载权限条件避免N+1查询
        Set<Long> conditionIds = perms.stream()
            .map(RoleResourcePermission::getConditionId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, PermissionCondition> conditionMap = conditionIds.isEmpty() ? Map.of() :
            permissionConditionMapper.selectValidByIdsNoTenant(conditionIds).stream()
                .collect(java.util.stream.Collectors.toMap(PermissionCondition::getId, Function.identity()));

        // 批量解析资源类型编码（避免N+1）
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);
        Map<String, OperationPermission> opByTypeAndBit = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(
            batchLoadOperationsByResourceTypes(tenantId, resourceTypeValues)
                .values()
                .stream()
                .flatMap(List::stream)
                .toList()
        );

        return perms.stream().map(perm -> {
            ResourceEntity resource = perm.getResourceEntityId() == null ? null : resourceMap.get(perm.getResourceEntityId());
            OperationPermission operation = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                opByTypeAndBit,
                perm.getResourceType(),
                perm.getGrantedBits()
            );
            String resourceTypeCode = resourceTypeCodeMap.get(perm.getResourceType());
            PermissionCondition condition = perm.getConditionId() == null ? null : conditionMap.get(perm.getConditionId());
            return new RolePermissionItemResp(
                perm.getId(),
                resourceTypeCode,
                resource == null ? null : resource.getCode(),
                resource == null ? null : resource.getCodeType(),
                resource == null ? null : resource.getName(),
                operation == null ? null : operation.getCode(),
                perm.getCanGrant(),
                condition == null ? null : condition.getCode(),
                Boolean.TRUE.equals(perm.getScopeAll()),
                perm.getDependOn()
            );
        }).toList();
    }

    /**
     * 解析SUB_PERM配置中允许的资源类型编码
     * <p>
     * 配置格式为JSON数组字符串，如["ORG","USER"]。
     * 解析为Set并统一大写处理。
     * </p>
     *
     * @param extra 配置字符串
     * @return 允许的资源类型编码集合
     */
    private Set<String> parseAllowedTypeCodes(String extra) {
        String normalized = extra.replace("[", "").replace("]", "").replace("\"", "");
        Set<String> codes = new HashSet<>();
        for (String token : normalized.split(",")) {
            String value = token.trim();
            if (!value.isEmpty()) {
                codes.add(value.toUpperCase());
            }
        }
        return codes;
    }

    /**
     * 构建授权校验的权限键
     * <p>
     * 格式：resourceTypeCode:resourceCode:operationCode:scopeAll
     * 与AuthorizationServiceImpl的格式一致。
     * </p>
     *
     * @param item 授权新增项
     * @return 权限键字符串
     */
    private String buildGrantKey(RoleGrantReq.GrantAddItem item) {
        return String.format("%s:%s:%s:%s",
            item.resourceTypeCode(),
            item.resourceCode() == null ? "*" : item.resourceCode(),
            item.operationCode(),
            Boolean.TRUE.equals(item.scopeAll()) ? "ALL" : "SPECIFIC");
    }

    private BizException biz(PermissionErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }

    private BizException biz(PermissionErrorCode errorCode, String message) {
        return new BizException(errorCode.getCode(), message);
    }

    // ===== 私有批量加载方法 =====

    /**
     * 批量加载资源实体
     */
    private Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return resourceEntityMapper.selectValidByIds(tenantId, ids)
            .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));
    }

    /**
     * 按资源类型批量加载操作权限
     */
    private Map<Integer, List<OperationPermission>> batchLoadOperationsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<OperationPermission>> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            if (resourceType == null) {
                continue;
            }
            result.put(resourceType, operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType));
        }
        return result;
    }
}