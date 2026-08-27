package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.ConfigType;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.service.domain.*;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.access.permission.util.ScopeModeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限授予服务实现类
 * <p>
 * 提供角色权限的查询（list）与聚合授予（apply-grant-plan 唯一写入口，记录级 plan 单事务原子）。
 * 实现严格的授权传递安全校验：操作者必须拥有canGrant=true的权限才能授权给他人。
 * 使用批量解析优化性能，避免N+1查询问题。
 * 通过 @PermissionChange afterCommit 统一执行缓存失效和失效广播，确保数据一致性。
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
    private final PermissionGrantPlanDomainService permissionGrantPlanDomainService;
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
                                      PermissionGrantPlanDomainService permissionGrantPlanDomainService,
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
        this.permissionGrantPlanDomainService = permissionGrantPlanDomainService;
        this.auditDomainService = auditDomainService;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "ROLE_RESOURCE_PERMISSION_APPLY_PLAN",
        targetType = "abstract_role", targetId = "#req.roleExternalId",
        summary = "'apply role permission grant plan'")
    @PermissionChange
    public List<RolePermissionItemResp> applyGrantPlan(Long tenantId, ApplyGrantPlanReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            throw biz(PermissionErrorCode.ROLE_NOT_FOUND);
        }
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE,
            String.valueOf(roleId), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }
        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        if (role == null) {
            throw biz(PermissionErrorCode.ROLE_NOT_FOUND);
        }
        if (role.getStatus() != PermissionConstants.ENABLED_STATUS) {
            throw biz(PermissionErrorCode.ROLE_DISABLED);
        }

        PermissionGrantPlanDomainService.PreparedGrantPlan prepared =
            permissionGrantPlanDomainService.prevalidate(
                tenantId, operatorId, roleId, req.domainCode(), req.plan());

        permissionGrantPlanDomainService.apply(prepared);
        PermissionChangeContext.markRoles(tenantId, roleId);
        recordGrantPlanChanges(tenantId, operatorId, roleId, prepared);

        List<RoleResourcePermission> allPermissions = rolePermMapper
            .selectValidByRoleId(tenantId, roleId);
        return toItemRespList(tenantId, allPermissions);
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
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCodeConstants.VIEW)) {
            return List.of();
        }

        List<RoleResourcePermission> allPerms = rolePermMapper.selectValidByRoleId(tenantId, roleId);
        List<RoleResourcePermission> selectedPerms = allPerms;
        if (req.resourceTypeCode() != null && !req.resourceTypeCode().isBlank()) {
            Integer resourceType = typeResolutionService.resolveTypeValue(
                tenantId, "resource_type", req.resourceTypeCode());
            if (resourceType == null) {
                return List.of();
            }
            List<RoleResourcePermission> mainPermissions = allPerms.stream()
                .filter(permission -> permission.getDependOn() == null)
                .filter(permission -> Objects.equals(permission.getResourceType(), resourceType))
                .toList();
            if (req.shouldIncludeChildren()) {
                Set<Long> mainPermissionIds = mainPermissions.stream()
                    .map(RoleResourcePermission::getId)
                    .collect(Collectors.toSet());
                selectedPerms = allPerms.stream()
                    .filter(permission -> permission.getDependOn() == null
                        ? Objects.equals(permission.getResourceType(), resourceType)
                        : mainPermissionIds.contains(permission.getDependOn()))
                    .toList();
            } else {
                selectedPerms = mainPermissions;
            }
        } else if (!req.shouldIncludeChildren()) {
            selectedPerms = allPerms.stream()
                .filter(permission -> permission.getDependOn() == null)
                .toList();
        }
        return toItemRespList(tenantId, selectedPerms, allPerms);
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
        return toItemRespList(tenantId, perms, perms);
    }

    private List<RolePermissionItemResp> toItemRespList(Long tenantId,
                                                        List<RoleResourcePermission> perms,
                                                        List<RoleResourcePermission> allRolePermissions) {
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
        Map<String, OperationPermission> opByTypeAndBit = buildOperationIndex(tenantId, resourceTypeValues);
        Map<Long, Long> childCountByParentId = allRolePermissions.stream()
            .map(RoleResourcePermission::getDependOn)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return perms.stream().map(perm -> {
            ResourceEntity resource = perm.getResourceEntityId() == null ? null : resourceMap.get(perm.getResourceEntityId());
            OperationPermission operation = opByTypeAndBit.get(operationIndexKey(
                perm.getResourceType(), perm.getGrantedBits()));
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
                ScopeModeSupport.fromScopeAll(perm.getScopeAll()),
                perm.getDependOn(),
                perm.getGrantSource() == null ? GrantSource.MANUAL.getValue() : perm.getGrantSource(),
                perm.getGrantedBits() == null ? "0" : String.valueOf(perm.getGrantedBits()),
                perm.getCreatedAt(),
                childCountByParentId.getOrDefault(perm.getId(), 0L)
            );
        }).toList();
    }

    /**
     * 一次加载租户操作定义，并按“类型专属优先、全局定义回退”建立类型+位索引。
     */
    private Map<String, OperationPermission> buildOperationIndex(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Map.of();
        }
        List<OperationPermission> allOperations = operationPermissionMapper
            .selectByTenantAndResourceType(tenantId, null);
        List<OperationPermission> globalOperations = allOperations.stream()
            .filter(operation -> operation.getResourceType() == null)
            .toList();
        Map<String, OperationPermission> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            Map<String, OperationPermission> specificByCode = allOperations.stream()
                .filter(operation -> Objects.equals(operation.getResourceType(), resourceType))
                .collect(Collectors.toMap(operation -> operation.getCode().toUpperCase(),
                    Function.identity(), (left, right) -> left, LinkedHashMap::new));
            for (OperationPermission operation : specificByCode.values()) {
                result.put(operationIndexKey(resourceType, operation.getBinaryBit()), operation);
            }
            for (OperationPermission operation : globalOperations) {
                if (!specificByCode.containsKey(operation.getCode().toUpperCase())) {
                    result.put(operationIndexKey(resourceType, operation.getBinaryBit()), operation);
                }
            }
        }
        return result;
    }

    private String operationIndexKey(Integer resourceType, Long binaryBit) {
        return resourceType + ":" + binaryBit;
    }

    private void recordGrantPlanChanges(
            Long tenantId,
            Long operatorId,
            Long roleId,
            PermissionGrantPlanDomainService.PreparedGrantPlan prepared) {
        List<Long> createdIds = new ArrayList<>();
        for (PermissionGrantPlanDomainService.PreparedCreate create : prepared.creates()) {
            createdIds.add(create.permission().getId());
            for (RoleResourcePermission child : create.children()) {
                createdIds.add(child.getId());
            }
        }
        List<Long> updatedIds = prepared.updates().stream()
            .map(RoleResourcePermission::getId).toList();
        if (!createdIds.isEmpty() || !updatedIds.isEmpty() || !prepared.removes().isEmpty()) {
            String diffSnapshot = String.format(
                "{\"creates\":%s,\"updates\":%s,\"removes\":%s}",
                createdIds, updatedIds, prepared.removes());
            auditDomainService.recordChangeLog(new AuditDomainService.ChangeLogContext(
                tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL,
                "apply-grant-plan"), List.of(new AuditDomainService.ChangeLogEntry(
                    "role_resource_permission", roleId, "APPLY_GRANT_PLAN",
                    null, null, diffSnapshot, null, new Long[]{roleId})));
        }
    }

    private BizException biz(PermissionErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
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
}
