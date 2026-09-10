package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.SubPermAllowedTypesReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SubPermAllowedTypesResp;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
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
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.access.permission.util.ScopeModeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class PermissionGrantAppServiceImpl implements PermissionGrantAppService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGrantAppServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final PermissionGrantPlanDomainService permissionGrantPlanDomainService;
    private final AuditDomainService auditDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;
    private final ObjectMapper objectMapper;

    public PermissionGrantAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                      ResourceEntityMapper resourceEntityMapper,
                                      OperationPermissionMapper operationPermissionMapper,
                                      PermissionConditionMapper permissionConditionMapper,
                                      RoleResourcePermissionMapper rolePermMapper,
                                      PermissionGrantPlanDomainService permissionGrantPlanDomainService,
                                      AuditDomainService auditDomainService,
                                      TypeResolutionService typeResolutionService,
                                      PermQueryEngine engine,
                                      ObjectMapper objectMapper) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.permissionConditionMapper = permissionConditionMapper;
        this.rolePermMapper = rolePermMapper;
        this.permissionGrantPlanDomainService = permissionGrantPlanDomainService;
        this.auditDomainService = auditDomainService;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.objectMapper = objectMapper;
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
        recordGrantPlanChanges(tenantId, operatorId, req, role, prepared);

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

        List<RoleResourcePermission> selectedPerms;
        if (req.resourceTypeCode() != null && !req.resourceTypeCode().isBlank()) {
            // 类型过滤下沉 Mapper（T-PERM-040）：主权限专用查询（depend_on IS NULL + 类型匹配），
            // 子权限按 depend_on 批量挂父——SQL 层同时过滤 abstract_role_id，满足契约 §6.4
            // 双重约束（depend_on ∈ 主权限集合 且同角色）；子权限可能跨类型不按自身类型过滤
            Integer resourceType = typeResolutionService.resolveTypeValue(
                tenantId, "resource_type", req.resourceTypeCode());
            if (resourceType == null) {
                return List.of();
            }
            List<RoleResourcePermission> mains = rolePermMapper
                .selectValidMainByRoleIdAndResourceType(tenantId, roleId, resourceType);
            if (mains.isEmpty()) {
                return List.of();
            }
            Set<Long> mainIds = mains.stream()
                .map(RoleResourcePermission::getId)
                .collect(Collectors.toSet());
            List<RoleResourcePermission> children = rolePermMapper
                .selectValidByRoleIdAndDependIds(tenantId, roleId, mainIds);
            // childCount 计数源 = 主权限 + 其全部直接子权限（父必须顶层，无孙代）
            List<RoleResourcePermission> mainsWithChildren = new ArrayList<>(mains);
            mainsWithChildren.addAll(children);
            selectedPerms = req.shouldIncludeChildren() ? mainsWithChildren : mains;
            return toItemRespList(tenantId, selectedPerms, mainsWithChildren);
        }
        List<RoleResourcePermission> allPerms = rolePermMapper.selectValidByRoleId(tenantId, roleId);
        selectedPerms = req.shouldIncludeChildren() ? allPerms
            : allPerms.stream()
                .filter(permission -> permission.getDependOn() == null)
                .toList();
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
            OperationPermission operation = opByTypeAndBit.get(BusinessKeys.operationBitKey(
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
     * 一次加载租户操作定义，并按类型+位建立索引（操作位空间按类型完全隔离，
     * 全局操作概念已退役——2026-08-30 设计定案）。
     */
    private Map<String, OperationPermission> buildOperationIndex(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Map.of();
        }
        List<OperationPermission> allOperations = operationPermissionMapper
            .selectByTenantAndResourceType(tenantId, null);
        Map<String, OperationPermission> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            for (OperationPermission operation : allOperations.stream()
                    .filter(op -> Objects.equals(op.getResourceType(), resourceType))
                    .toList()) {
                result.put(BusinessKeys.operationBitKey(resourceType, operation.getBinaryBit()), operation);
            }
        }
        return result;
    }

    private void recordGrantPlanChanges(
            Long tenantId,
            Long operatorId,
            ApplyGrantPlanReq req,
            AbstractRole role,
            PermissionGrantPlanDomainService.PreparedGrantPlan prepared) {
        // §5.8 diff_snapshot 规范聚合（原 §6.8）形状（T-PERM-034 收口）：eventType=ROLE_PERMISSION_CHANGE +
        // items[]{changeType, permission(6 字段业务键), role 摘要}，一条聚合日志；
        // 业务键快照由 prevalidate 期装配（removes 行随后被软删，事后不可回查），
        // 读侧 change-log 列表按 items[].permission 解析（原 recent-changes 读面已随 T-PERM-059 删除）
        if (prepared.auditKeys().isEmpty()) {
            return;
        }
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("eventType", "ROLE_PERMISSION_CHANGE");
        ArrayNode items = snapshot.putArray("items");
        for (PermissionGrantPlanDomainService.AuditPermissionKey key : prepared.auditKeys()) {
            ObjectNode item = items.addObject();
            item.put("changeType", key.changeType());
            ObjectNode permission = item.putObject("permission");
            permission.put("domainCode", req.domainCode());
            permission.put("resourceTypeCode", key.resourceTypeCode());
            permission.put("resourceCode", key.resourceCode());
            permission.put("codeType", key.codeType());
            permission.put("operationCode", key.operationCode());
            permission.put("scopeMode", key.scopeMode() == null ? null : key.scopeMode().name());
            ObjectNode roleSummary = item.putObject("role");
            roleSummary.put("roleTypeCode", req.roleTypeCode());
            roleSummary.put("roleExternalId", req.roleExternalId());
            roleSummary.put("roleName", role.getName());
        }
        auditDomainService.recordChangeLog(new AuditDomainService.ChangeLogContext(
            tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL,
            "apply-grant-plan"), List.of(new AuditDomainService.ChangeLogEntry(
            "role_resource_permission", role.getId(), "APPLY_GRANT_PLAN",
            null, null, snapshot.toString(), null, new Long[]{role.getId()})));
    }

    /**
     * 子权限允许类型只读查询（api-contract §6.5.2）
     * <p>
     * 门禁：目标角色 ROLE:VIEW 实例级——resolveRoleId 失败明确抛 20001（本接口无
     * 「空列表即自然结果」语义，避免前端把角色不存在误判为 ALLOW_NONE）；无 VIEW
     * 抛 SecurityException 走统一访问拒绝（区别于 §6.4 list 的失败返回空列表）。
     * 策略结果由 {@code resolveSubPermissionPolicy} 直接映射（读写同源，禁止本层
     * 另行编写 SUB_PERM 判断）。
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public SubPermAllowedTypesResp subPermAllowedTypes(Long tenantId, SubPermAllowedTypesReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            throw biz(PermissionErrorCode.ROLE_NOT_FOUND);
        }
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE,
            String.valueOf(roleId), OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE:" + roleId);
        }
        PermissionGrantPlanDomainService.SubPermissionPolicy policy =
            permissionGrantPlanDomainService.resolveSubPermissionPolicy(tenantId, req.parentResourceTypeCode());
        return new SubPermAllowedTypesResp(
            req.parentResourceTypeCode(),
            policy.mode().name(),
            policy.reason(),
            policy.mode() == PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_LIST
                ? policy.allowedTypeCodes() : List.of());
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
