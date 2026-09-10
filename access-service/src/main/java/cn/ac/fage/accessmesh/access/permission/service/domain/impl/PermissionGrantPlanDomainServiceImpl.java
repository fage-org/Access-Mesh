package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.ConfigType;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.DatabaseExceptionSupport;
import cn.ac.fage.accessmesh.access.permission.util.ScopeModeSupport;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 聚合授权计划领域实现：先一次性解析并校验完整计划，再执行记录级变更。
 */
@Service
public class PermissionGrantPlanDomainServiceImpl implements PermissionGrantPlanDomainService {

    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final PermissionGrantDomainService permissionGrantDomainService;
    private final RoleResourcePermissionMapper rolePermissionMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final DomainConfigMapper domainConfigMapper;
    private final ObjectMapper objectMapper;

    public PermissionGrantPlanDomainServiceImpl(
            TypeResolutionService typeResolutionService,
            DomainClassifyService domainClassifyService,
            PermissionGrantDomainService permissionGrantDomainService,
            RoleResourcePermissionMapper rolePermissionMapper,
            ResourceEntityMapper resourceEntityMapper,
            OperationPermissionMapper operationPermissionMapper,
            PermissionConditionMapper permissionConditionMapper,
            DomainConfigMapper domainConfigMapper,
            ObjectMapper objectMapper) {
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.permissionGrantDomainService = permissionGrantDomainService;
        this.rolePermissionMapper = rolePermissionMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.permissionConditionMapper = permissionConditionMapper;
        this.domainConfigMapper = domainConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PreparedGrantPlan prevalidate(Long tenantId, Long subjectId, Long roleId, String domainCode,
                                         ApplyGrantPlanReq.GrantPlan plan) {
        List<ApplyGrantPlanReq.CreateItem> createItems = plan.createItems();
        List<ApplyGrantPlanReq.UpdateItem> updateItems = plan.updateItems();
        List<Long> removeIds = plan.removeIds();
        if (createItems.isEmpty() && updateItems.isEmpty() && removeIds.isEmpty()) {
            throw biz(PermissionErrorCode.GRANT_REQUEST_EMPTY);
        }

        assertDistinct(updateItems.stream().map(ApplyGrantPlanReq.UpdateItem::id).toList(),
            "plan.updates contains duplicate id");
        assertDistinct(removeIds, "plan.removes contains duplicate id");
        Set<Long> updateIds = updateItems.stream().map(ApplyGrantPlanReq.UpdateItem::id)
            .collect(Collectors.toSet());
        Set<Long> removeIdSet = new HashSet<>(removeIds);
        if (!java.util.Collections.disjoint(updateIds, removeIdSet)) {
            throw validation("The same permission id cannot be updated and removed");
        }

        List<RoleResourcePermission> existingPermissions = rolePermissionMapper
            .selectValidByRoleId(tenantId, roleId);
        Map<Long, RoleResourcePermission> existingById = existingPermissions.stream()
            .collect(Collectors.toMap(RoleResourcePermission::getId, Function.identity()));

        Set<Long> referencedIds = new LinkedHashSet<>();
        referencedIds.addAll(updateIds);
        referencedIds.addAll(removeIdSet);
        createItems.stream().map(ApplyGrantPlanReq.CreateItem::parentPermissionId)
            .filter(Objects::nonNull).forEach(referencedIds::add);
        for (Long referencedId : referencedIds) {
            if (!existingById.containsKey(referencedId)) {
                PermissionErrorCode code = createItems.stream()
                    .anyMatch(item -> Objects.equals(item.parentPermissionId(), referencedId))
                    ? PermissionErrorCode.PARENT_PERMISSION_NOT_FOUND
                    : PermissionErrorCode.PERMISSION_NOT_FOUND;
                throw biz(code, "Permission id not found: " + referencedId);
            }
        }

        for (Long id : updateIds) {
            assertMutable(existingById.get(id));
            RoleResourcePermission permission = existingById.get(id);
            // 子权限 update 一律 20043（§6.5.1 错误优先级②：先于空变更/父被删等其余拒绝路径）
            if (permission.getDependOn() != null) {
                throw biz(PermissionErrorCode.SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED,
                    "Cannot update a child permission: " + id);
            }
        }
        for (Long id : removeIdSet) {
            assertMutable(existingById.get(id));
        }

        List<ApplyGrantPlanReq.GrantRecordKey> allKeys = new ArrayList<>();
        for (ApplyGrantPlanReq.CreateItem create : createItems) {
            if (create.parentPermissionId() != null && !create.childItems().isEmpty()) {
                throw validation("A child create cannot contain children");
            }
            if (create.parentPermissionId() != null) {
                RoleResourcePermission parent = existingById.get(create.parentPermissionId());
                if (parent.getDependOn() != null) {
                    throw biz(PermissionErrorCode.PARENT_PERMISSION_NOT_TOP_LEVEL);
                }
                if (removeIdSet.contains(parent.getId())) {
                    throw biz(PermissionErrorCode.PARENT_PERMISSION_NOT_FOUND,
                        "Cannot add a child to a removed parent permission");
                }
                // 向 AUTO_DEP 父挂子权限同属 AUTO_DEP 只读边界（自动补全记录不承载手动子权限）
                assertMutable(parent);
            }
            // 子权限属性系统不变量（先于 toPermission 内主权限 20041 判定）：
            // 两种子权限 create 形态的 conditionCode 必须 null、canGrant 必须 false
            if (create.parentPermissionId() != null) {
                assertChildKeyAttributes(create.key());
            }
            for (ApplyGrantPlanReq.GrantRecordKey childKey : create.childItems()) {
                assertChildKeyAttributes(childKey);
            }
            allKeys.add(create.key());
            allKeys.addAll(create.childItems());
        }
        validateKeyShapes(allKeys);

        Set<String> resourceTypeCodes = allKeys.stream()
            .map(ApplyGrantPlanReq.GrantRecordKey::resourceTypeCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> rawTypeValues = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", resourceTypeCodes);
        Map<String, Integer> typeValues = normalizeTypeValues(rawTypeValues);
        for (String typeCode : resourceTypeCodes) {
            if (!typeValues.containsKey(normalize(typeCode))) {
                throw biz(PermissionErrorCode.RESOURCE_TYPE_NOT_FOUND,
                    "resourceTypeCode not found: " + typeCode);
            }
        }

        List<ResourceResolveRequest> resourceRequests = allKeys.stream()
            .filter(key -> !isScopeAll(key))
            .map(key -> new ResourceResolveRequest(
                key.resourceTypeCode(), key.resourceCode(), key.codeType(), domainCode))
            .distinct()
            .toList();
        Map<ResourceResolveKey, Long> resourceIds = typeResolutionService
            .batchResolveResourceIds(tenantId, resourceRequests);

        List<OperationPermission> allOperations = operationPermissionMapper
            .selectByTenantAndResourceType(tenantId, null);
        Set<String> knownOperationCodes = allOperations.stream()
            .map(OperationPermission::getCode).filter(Objects::nonNull)
            .map(PermissionGrantPlanDomainServiceImpl::normalize)
            .collect(Collectors.toSet());

        Set<String> conditionCodes = new LinkedHashSet<>();
        // 空白串按非空收集：仅精确空串表示清除（契约三态），空白串走 20006 存在性预检
        // fail-closed（create 轨同口径——validateKeyShapes 拒空白，不静默清除）
        allKeys.stream().map(ApplyGrantPlanReq.GrantRecordKey::conditionCode)
            .filter(code -> code != null && !code.isEmpty()).forEach(conditionCodes::add);
        updateItems.stream().map(ApplyGrantPlanReq.UpdateItem::conditionCode)
            .filter(code -> code != null && !code.isEmpty()).forEach(conditionCodes::add);
        Map<String, PermissionCondition> conditionsByCode = conditionCodes.isEmpty() ? Map.of()
            : permissionConditionMapper.selectValidByCodes(tenantId, conditionCodes).stream()
                .collect(Collectors.toMap(PermissionCondition::getCode, Function.identity()));
        for (String conditionCode : conditionCodes) {
            if (!conditionsByCode.containsKey(conditionCode)) {
                throw biz(PermissionErrorCode.CONDITION_NOT_FOUND,
                    "conditionCode not found: " + conditionCode);
            }
        }

        List<PreparedCreate> preparedCreates = new ArrayList<>();
        List<RoleResourcePermission> directCreates = new ArrayList<>();
        Set<PermissionGrantDomainService.GrantCheckKey> delegationKeys = new LinkedHashSet<>();
        List<PermissionGrantPlanDomainService.AuditPermissionKey> auditKeys = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ApplyGrantPlanReq.CreateItem create : createItems) {
            RoleResourcePermission permission = toPermission(tenantId, roleId, domainCode,
                create.key(), create.parentPermissionId(), typeValues, resourceIds,
                allOperations, knownOperationCodes, conditionsByCode, now);
            List<RoleResourcePermission> children = create.childItems().stream()
                .map(key -> toPermission(tenantId, roleId, domainCode, key, null,
                    typeValues, resourceIds, allOperations, knownOperationCodes,
                    conditionsByCode, now))
                .toList();
            preparedCreates.add(new PreparedCreate(permission, children));
            directCreates.add(permission);
            delegationKeys.add(toGrantCheckKey(create.key()));
            create.childItems().stream().map(this::toGrantCheckKey).forEach(delegationKeys::add);
            auditKeys.add(toAuditKey("ADD", create.key()));
            create.childItems().forEach(key -> auditKeys.add(toAuditKey("ADD", key)));
        }

        permissionGrantDomainService.validateSingleManualGrants(
            existingPermissions, directCreates, removeIdSet);
        for (PreparedCreate create : preparedCreates) {
            if (!create.children().isEmpty()) {
                permissionGrantDomainService.validateSingleManualGrants(
                    List.of(), create.children(), Set.of());
            }
        }

        validateSubPermissions(tenantId, preparedCreates, existingById, typeValues);

        // 级联删除的子权限：apply 随主权限一并软删 depend_on 命中的行（租户口径，
        // 与 cascadeSoftDeleteChildren 一致），预检期快照其业务键；显式删除项去重
        List<RoleResourcePermission> cascadedChildren = removeIdSet.isEmpty() ? List.of()
            : rolePermissionMapper.selectValidByDependOns(tenantId, removeIdSet).stream()
                .filter(child -> !removeIdSet.contains(child.getId()))
                .toList();

        Set<Integer> updateTypeValues = updateItems.stream()
            .map(item -> existingById.get(item.id()).getResourceType())
            .filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long removeId : removeIdSet) {
            Integer removeType = existingById.get(removeId).getResourceType();
            if (removeType != null) {
                updateTypeValues.add(removeType);
            }
        }
        for (RoleResourcePermission child : cascadedChildren) {
            if (child.getResourceType() != null) {
                updateTypeValues.add(child.getResourceType());
            }
        }
        Map<Integer, String> updateTypeCodes = typeResolutionService.batchResolveTypeCodes(
            tenantId, "resource_type", updateTypeValues);
        Set<Long> updateResourceIds = updateItems.stream()
            .map(item -> existingById.get(item.id()).getResourceEntityId())
            .filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long removeId : removeIdSet) {
            Long removeResourceId = existingById.get(removeId).getResourceEntityId();
            if (removeResourceId != null) {
                updateResourceIds.add(removeResourceId);
            }
        }
        for (RoleResourcePermission child : cascadedChildren) {
            if (child.getResourceEntityId() != null) {
                updateResourceIds.add(child.getResourceEntityId());
            }
        }
        Map<Long, ResourceEntity> updateResources = updateResourceIds.isEmpty() ? Map.of()
            : resourceEntityMapper.selectValidByIds(tenantId, updateResourceIds).stream()
                .collect(Collectors.toMap(ResourceEntity::getId, Function.identity()));

        List<RoleResourcePermission> preparedUpdates = new ArrayList<>();
        for (ApplyGrantPlanReq.UpdateItem update : updateItems) {
            if (update.canGrant() == null && update.conditionCode() == null) {
                throw validation("An update must change canGrant or conditionCode");
            }
            RoleResourcePermission permission = existingById.get(update.id());
            Long originalConditionId = permission.getConditionId();
            if (update.canGrant() != null) {
                permission.setCanGrant(update.canGrant());
            }
            if (update.conditionCode() != null) {
                permission.setConditionId(update.conditionCode().isEmpty()
                    ? null : conditionsByCode.get(update.conditionCode()).getId());
            }
            permissionGrantDomainService.validateGrantAttributes(permission);
            // 20042 条件启用状态（T-PERM-041）：仅 conditionCode 变更时校验——新条件 id
            // 与改前绑定一致视为存量保留（2026-08-30 设计定案：同 id 重写豁免，
            // 与前端 v3.1「未修改 conditionCode 允许保留」同口径）；清除与缺省不触发
            if (update.conditionCode() != null && !update.conditionCode().isEmpty()) {
                PermissionCondition changedCondition = conditionsByCode.get(update.conditionCode());
                if (!Objects.equals(changedCondition.getId(), originalConditionId)) {
                    assertConditionEnabled(changedCondition, update.conditionCode());
                }
            }
            permission.setUpdatedAt(now);
            preparedUpdates.add(permission);

            ResourceEntity resource = permission.getResourceEntityId() == null ? null
                : updateResources.get(permission.getResourceEntityId());
            OperationPermission operation = resolveOperationByBit(
                allOperations, permission.getResourceType(), permission.getGrantedBits());
            String resourceTypeCode = updateTypeCodes.get(permission.getResourceType());
            if (resourceTypeCode == null || operation == null
                || (!Boolean.TRUE.equals(permission.getScopeAll()) && resource == null)) {
                throw biz(PermissionErrorCode.PERMISSION_NOT_FOUND,
                    "Permission definition has changed: " + permission.getId());
            }
            delegationKeys.add(new PermissionGrantDomainService.GrantCheckKey(
                resourceTypeCode,
                resource == null ? null : resource.getCode(),
                resource == null ? null : resource.getCodeType(),
                operation.getCode(),
                Boolean.TRUE.equals(permission.getScopeAll())
            ));
            auditKeys.add(new PermissionGrantPlanDomainService.AuditPermissionKey("UPDATE",
                resourceTypeCode, resource == null ? null : resource.getCode(),
                resource == null ? null : resource.getCodeType(), operation.getCode(),
                Boolean.TRUE.equals(permission.getScopeAll()) ? ScopeMode.ALL : ScopeMode.INSTANCE));
        }

        // removes 业务键快照（行随后被软删，事后不可回查；§5.8 diff_snapshot 规范聚合（原 §6.8）形状装配）。
        // 悬挂引用（资源实体/操作定义已不存在）降级为 null 键字段：删除不得被
        // 死引用阻塞（清理死引用正是删除的合法场景），update 路径维持既有严格判定
        for (Long removeId : removeIds) {
            auditKeys.add(buildRemoveAuditKey(
                existingById.get(removeId), updateResources, updateTypeCodes, allOperations));
        }
        // 级联删除的子权限同记 REMOVE（实际被删除的行都要能按业务键检索到本次变更）
        for (RoleResourcePermission child : cascadedChildren) {
            auditKeys.add(buildRemoveAuditKey(child, updateResources, updateTypeCodes, allOperations));
        }

        verifyDelegation(tenantId, subjectId, domainCode, delegationKeys);
        return new PreparedGrantPlan(tenantId, roleId, preparedCreates,
            preparedUpdates, List.copyOf(removeIds), Set.copyOf(delegationKeys), List.copyOf(auditKeys));
    }

    @Override
    public void apply(PreparedGrantPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        if (!plan.removes().isEmpty()) {
            int affected = rolePermissionMapper.softDeleteBatch(plan.tenantId(), plan.removes(), now);
            if (affected != plan.removes().size()) {
                throw biz(PermissionErrorCode.PERMISSION_NOT_FOUND);
            }
            rolePermissionMapper.cascadeSoftDeleteChildren(plan.tenantId(), plan.removes(), now);
        }
        for (RoleResourcePermission update : plan.updates()) {
            if (rolePermissionMapper.updateGrantAttributes(
                plan.tenantId(), plan.roleId(), update.getId(), update.getCanGrant(),
                update.getConditionId(), update.getUpdatedAt()) != 1) {
                throw biz(PermissionErrorCode.PERMISSION_NOT_FOUND);
            }
        }
        for (PreparedCreate create : plan.creates()) {
            insertOne(create.permission());
            if (!create.children().isEmpty()) {
                for (RoleResourcePermission child : create.children()) {
                    child.setDependOn(create.permission().getId());
                }
                insertBatch(create.children());
            }
        }
    }

    private RoleResourcePermission toPermission(
            Long tenantId,
            Long roleId,
            String domainCode,
            ApplyGrantPlanReq.GrantRecordKey key,
            Long parentPermissionId,
            Map<String, Integer> typeValues,
            Map<ResourceResolveKey, Long> resourceIds,
            List<OperationPermission> operations,
            Set<String> knownOperationCodes,
            Map<String, PermissionCondition> conditionsByCode,
            LocalDateTime now) {
        Integer resourceType = typeValues.get(normalize(key.resourceTypeCode()));
        OperationPermission operation = resolveOperation(
            operations, resourceType, key.operationCode(), knownOperationCodes);
        boolean scopeAll = isScopeAll(key);
        Long resourceId = null;
        if (!scopeAll) {
            resourceId = resourceIds.get(new ResourceResolveKey(
                key.resourceTypeCode(), key.resourceCode(), key.codeType(), domainCode));
            if (resourceId == null) {
                throw biz(PermissionErrorCode.RESOURCE_NOT_FOUND,
                    "resource not found: " + key.resourceCode());
            }
        }
        RoleResourcePermission permission = new RoleResourcePermission();
        permission.setTenantId(tenantId);
        permission.setAbstractRoleId(roleId);
        permission.setResourceEntityId(resourceId);
        permission.setGrantedBits(operation.getBinaryBit());
        permission.setResourceType(resourceType);
        permission.setDependOn(parentPermissionId);
        permission.setScopeAll(scopeAll);
        permission.setCanGrant(Boolean.TRUE.equals(key.canGrant()));
        permission.setConditionId(key.conditionCode() == null ? null
            : conditionsByCode.get(key.conditionCode()).getId());
        permission.setGrantSource(GrantSource.MANUAL.getValue());
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        permission.setDeleteFlag(0L);
        permissionGrantDomainService.validateGrantAttributes(permission);
        // 20042 条件启用状态（T-PERM-041）：create 新写入的 conditionCode 必须启用中
        // （子权限带条件已被 20043 先行拦截，能携带条件到此处的均为主权限）
        if (key.conditionCode() != null) {
            assertConditionEnabled(conditionsByCode.get(key.conditionCode()), key.conditionCode());
        }
        return permission;
    }

    private void validateKeyShapes(List<ApplyGrantPlanReq.GrantRecordKey> keys) {
        for (ApplyGrantPlanReq.GrantRecordKey key : keys) {
            if (key == null || key.resourceTypeCode() == null || key.resourceTypeCode().isBlank()
                || key.operationCode() == null || key.operationCode().isBlank()
                || key.scopeMode() == null) {
                throw validation("Invalid grant record key");
            }
            if (isScopeAll(key)) {
                if (key.resourceCode() != null || key.codeType() != null) {
                    throw validation("ALL scope requires null resourceCode and codeType");
                }
            } else {
                if (key.resourceCode() == null || key.resourceCode().isBlank()) {
                    throw biz(PermissionErrorCode.RESOURCE_CODE_REQUIRED);
                }
                if (key.codeType() == null || key.codeType().isBlank()) {
                    throw validation("INSTANCE scope requires codeType");
                }
            }
            if (key.conditionCode() != null && key.conditionCode().isBlank()) {
                throw biz(PermissionErrorCode.CONDITION_NOT_FOUND,
                    "Create conditionCode cannot be blank");
            }
        }
    }

    private void validateSubPermissions(
            Long tenantId,
            List<PreparedCreate> creates,
            Map<Long, RoleResourcePermission> existingById,
            Map<String, Integer> typeValues) {
        List<ParentChildTypes> relations = new ArrayList<>();
        Map<Integer, String> requestedTypeCodesByValue = typeValues.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (left, right) -> left));
        Set<Integer> unresolvedParentTypes = creates.stream()
            .filter(create -> create.permission().getDependOn() != null)
            .map(create -> existingById.get(create.permission().getDependOn()).getResourceType())
            .filter(type -> !requestedTypeCodesByValue.containsKey(type))
            .collect(Collectors.toSet());
        if (!unresolvedParentTypes.isEmpty()) {
            requestedTypeCodesByValue.putAll(typeResolutionService.batchResolveTypeCodes(
                tenantId, "resource_type", unresolvedParentTypes));
        }
        for (PreparedCreate create : creates) {
            if (create.permission().getDependOn() != null) {
                RoleResourcePermission parent = existingById.get(create.permission().getDependOn());
                relations.add(new ParentChildTypes(
                    requestedTypeCodesByValue.get(parent.getResourceType()),
                    List.of(requestedTypeCodesByValue.get(create.permission().getResourceType()))));
            }
            if (!create.children().isEmpty()) {
                relations.add(new ParentChildTypes(
                    requestedTypeCodesByValue.get(create.permission().getResourceType()),
                    create.children().stream()
                        .map(child -> requestedTypeCodesByValue.get(child.getResourceType())).toList()));
            }
        }
        if (relations.isEmpty()) {
            return;
        }
        Set<String> parentTypeCodes = relations.stream().map(ParentChildTypes::parentTypeCode)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Long> domainIds = domainClassifyService.findDomainIdsByTypeCodes(
            tenantId, parentTypeCodes);
        Map<Long, DomainConfig> subPermByDomain = domainConfigMapper.selectByTenantId(tenantId).stream()
            .filter(config -> ConfigType.SUB_PERM.getValue().equals(config.getConfigType()))
            .collect(Collectors.toMap(DomainConfig::getBizDomainId, Function.identity(),
                (left, right) -> left));
        for (ParentChildTypes relation : relations) {
            Long domainId = domainIds.get(relation.parentTypeCode());
            DomainConfig config = domainId == null ? null : subPermByDomain.get(domainId);
            for (String childTypeCode : relation.childTypeCodes()) {
                assertSubPermissionAllowed(config, relation.parentTypeCode(), childTypeCode);
            }
        }
    }

    private void assertSubPermissionAllowed(DomainConfig config, String parentTypeCode,
                                            String childTypeCode) {
        // 写链路复用读接口同一策略解析器（读写同源，§6.5.2 实现约束）
        SubPermissionPolicy policy = parseSubPermissionPolicy(config, parentTypeCode);
        if (!policy.allows(childTypeCode)) {
            throw biz(PermissionErrorCode.SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED,
                "Child type " + childTypeCode + " is not allowed for parent type " + parentTypeCode
                    + " (" + policy.mode() + (policy.reason() == null ? "" : "/" + policy.reason()) + ")");
        }
    }

    /**
     * SUB_PERM 策略唯一解析入口（读接口直接序列化，写链路 prevalidate 复用 allows()）。
     * <p>
     * 判定优先级固定（§6.5.2）：0 配置存在性（CONFIG_MISSING/CONFIG_EMPTY）→
     * 1 顶层通配 "*" → ALLOW_ALL；2 全量结构校验（JSON 失败或任一 allowed 项
     * parent_type 非非空字符串 / child_types 非数组 → CONFIG_INVALID）→
     * 3 无匹配 parent_type → PARENT_NOT_CONFIGURED；4 匹配项 child_types 含 "*" →
     * ALLOW_ALL；5 并集去重非空 → ALLOW_LIST（配置原文，不做码转换）；
     * 6 并集为空 → CHILD_TYPES_EMPTY。比较均大小写不敏感。
     * </p>
     *
     * @throws BizException parentResourceTypeCode 资源类型不存在（20007）
     */
    @Override
    public SubPermissionPolicy resolveSubPermissionPolicy(Long tenantId, String parentResourceTypeCode) {
        if (typeResolutionService.resolveTypeValue(tenantId, "resource_type", parentResourceTypeCode) == null) {
            throw biz(PermissionErrorCode.RESOURCE_TYPE_NOT_FOUND,
                "parentResourceTypeCode not found: " + parentResourceTypeCode);
        }
        DomainConfig config = findSubPermConfigForParentType(tenantId, parentResourceTypeCode);
        return parseSubPermissionPolicy(config, parentResourceTypeCode);
    }

    /** 按父资源类型定位其所属域的 SUB_PERM 配置（域分类 + 全局域兜底口径同写链路批量路径） */
    private DomainConfig findSubPermConfigForParentType(Long tenantId, String parentTypeCode) {
        Map<String, Long> domainIds = domainClassifyService.findDomainIdsByTypeCodes(
            tenantId, Set.of(parentTypeCode));
        Long domainId = domainIds.get(parentTypeCode);
        if (domainId == null) {
            return null;
        }
        return domainConfigMapper.selectByTenantId(tenantId).stream()
            .filter(existing -> ConfigType.SUB_PERM.getValue().equals(existing.getConfigType()))
            .filter(existing -> domainId.equals(existing.getBizDomainId()))
            .findFirst().orElse(null);
    }

    /** 从单条 SUB_PERM 配置解析策略（§6.5.2 优先级 0-6；读写同源；包内可见供同包单测直测判定表） */
    SubPermissionPolicy parseSubPermissionPolicy(DomainConfig config, String parentTypeCode) {
        if (config == null) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_NONE,
                "CONFIG_MISSING", List.of());
        }
        String extra = config.getExtra();
        if (extra == null || extra.isBlank()) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_NONE,
                "CONFIG_EMPTY", List.of());
        }
        if ("*".equals(extra.trim())) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_ALL, null, List.of());
        }
        List<String> matchedChildTypes = new ArrayList<>();
        boolean parentMatched = false;
        try {
            JsonNode allowed = objectMapper.readTree(extra).get("allowed");
            if (allowed == null || !allowed.isArray()) {
                throw new IllegalArgumentException("allowed must be an array");
            }
            for (JsonNode item : allowed) {
                // 全量结构校验：非匹配项不容错（无法证明属于其他父类型，属全局结构错误）；
                // parent_type 必须为非空字符串、child_types 必须为字符串数组（数字/布尔等
                // 非字符串标量按 §6.5.2 优先级 2 落 CONFIG_INVALID，不 asText 容错收编）
                JsonNode parentNode = item.path("parent_type");
                JsonNode childTypes = item.get("child_types");
                if (!parentNode.isTextual() || parentNode.asText().isBlank()
                    || childTypes == null || !childTypes.isArray()) {
                    throw new IllegalArgumentException("invalid allowed item");
                }
                for (JsonNode childType : childTypes) {
                    if (!childType.isTextual()) {
                        throw new IllegalArgumentException("invalid allowed child_type");
                    }
                }
                String configuredParent = parentNode.asText();
                if (!configuredParent.equalsIgnoreCase(parentTypeCode)) {
                    continue;
                }
                parentMatched = true;
                for (JsonNode childType : childTypes) {
                    matchedChildTypes.add(childType.asText());
                }
            }
        } catch (IllegalArgumentException exception) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_NONE,
                "CONFIG_INVALID", List.of());
        } catch (Exception exception) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_NONE,
                "CONFIG_INVALID", List.of());
        }
        if (!parentMatched) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_NONE,
                "PARENT_NOT_CONFIGURED", List.of());
        }
        if (matchedChildTypes.stream().anyMatch("*"::equals)) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_ALL, null, List.of());
        }
        // 并集去重（大小写不敏感，保留配置原文首次出现顺序）
        List<String> union = new ArrayList<>();
        for (String childType : matchedChildTypes) {
            if (childType == null || childType.isBlank()) {
                continue;
            }
            boolean duplicated = union.stream().anyMatch(existing -> existing.equalsIgnoreCase(childType));
            if (!duplicated) {
                union.add(childType);
            }
        }
        if (union.isEmpty()) {
            return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_NONE,
                "CHILD_TYPES_EMPTY", List.of());
        }
        return new SubPermissionPolicy(SubPermissionPolicy.Mode.ALLOW_LIST, null, List.copyOf(union));
    }

    /** 子权限属性系统不变量（2026-08-08 产品确认）：create 形态的 conditionCode 必须 null、canGrant 必须 false */
    private static void assertChildKeyAttributes(ApplyGrantPlanReq.GrantRecordKey key) {
        if (key.conditionCode() != null || Boolean.TRUE.equals(key.canGrant())) {
            throw new BizException(PermissionErrorCode.SUB_PERMISSION_ATTRIBUTE_NOT_ALLOWED.getCode(),
                "Child permission does not carry conditionCode/canGrant: "
                    + key.resourceTypeCode() + "/" + key.operationCode());
        }
    }

    /** 20042 条件启用状态：写入/变更的目标条件必须 enabled=true（存量保留豁免由调用方判定） */
    private static void assertConditionEnabled(PermissionCondition condition, String conditionCode) {
        if (!Boolean.TRUE.equals(condition.getEnabled())) {
            throw new BizException(PermissionErrorCode.CONDITION_DISABLED.getCode(),
                "conditionCode is disabled: " + conditionCode);
        }
    }

    private OperationPermission resolveOperation(List<OperationPermission> operations,
                                                 Integer resourceType,
                                                 String operationCode,
                                                 Set<String> knownOperationCodes) {
        // 操作位空间按类型完全隔离（全局操作概念已退役）：仅匹配目标类型的专属定义
        String normalizedCode = normalize(operationCode);
        OperationPermission resolved = operations.stream()
            .filter(operation -> Objects.equals(operation.getResourceType(), resourceType))
            .filter(operation -> normalizedCode.equals(normalize(operation.getCode())))
            .findFirst().orElse(null);
        if (resolved != null) {
            return resolved;
        }
        if (knownOperationCodes.contains(normalizedCode)) {
            throw biz(PermissionErrorCode.RESOURCE_TYPE_OPERATION_MISMATCH,
                "operationCode does not apply to resourceTypeCode: " + operationCode);
        }
        throw biz(PermissionErrorCode.OPERATION_NOT_FOUND,
            "operationCode not found: " + operationCode);
    }

    private OperationPermission resolveOperationByBit(List<OperationPermission> operations,
                                                      Integer resourceType,
                                                      Long grantedBits) {
        return operations.stream()
            .filter(operation -> Objects.equals(operation.getResourceType(), resourceType))
            .filter(operation -> Objects.equals(operation.getBinaryBit(), grantedBits))
            .findFirst().orElse(null);
    }

    /** create 请求键 → 变更日志业务键快照（ADD） */
    private static PermissionGrantPlanDomainService.AuditPermissionKey toAuditKey(
            String changeType, ApplyGrantPlanReq.GrantRecordKey key) {
        return new PermissionGrantPlanDomainService.AuditPermissionKey(changeType,
            key.resourceTypeCode(), key.resourceCode(), key.codeType(),
            key.operationCode(), key.scopeMode());
    }

    /** 被删除行（显式 removes 与级联子权限共用）→ 变更日志业务键快照（REMOVE，悬挂引用降级 null 键字段） */
    private PermissionGrantPlanDomainService.AuditPermissionKey buildRemoveAuditKey(
            RoleResourcePermission permission,
            Map<Long, ResourceEntity> updateResources,
            Map<Integer, String> updateTypeCodes,
            List<OperationPermission> allOperations) {
        ResourceEntity resource = permission.getResourceEntityId() == null ? null
            : updateResources.get(permission.getResourceEntityId());
        OperationPermission operation = resolveOperationByBit(
            allOperations, permission.getResourceType(), permission.getGrantedBits());
        String resourceTypeCode = updateTypeCodes.get(permission.getResourceType());
        return new PermissionGrantPlanDomainService.AuditPermissionKey("REMOVE",
            resourceTypeCode, resource == null ? null : resource.getCode(),
            resource == null ? null : resource.getCodeType(),
            operation == null ? null : operation.getCode(),
            Boolean.TRUE.equals(permission.getScopeAll()) ? ScopeMode.ALL : ScopeMode.INSTANCE);
    }

    private PermissionGrantDomainService.GrantCheckKey toGrantCheckKey(
            ApplyGrantPlanReq.GrantRecordKey key) {
        return new PermissionGrantDomainService.GrantCheckKey(
            key.resourceTypeCode(), key.resourceCode(), key.codeType(), key.operationCode(),
            isScopeAll(key));
    }

    private void verifyDelegation(
            Long tenantId,
            Long subjectId,
            String domainCode,
            Set<PermissionGrantDomainService.GrantCheckKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        Map<String, PermissionGrantDomainService.GrantCheckResult> results =
            permissionGrantDomainService.checkCanGrant(tenantId, subjectId, keys, domainCode);
        for (PermissionGrantDomainService.GrantCheckKey key : keys) {
            PermissionGrantDomainService.GrantCheckResult result = results.get(grantCheckKey(key));
            if (result == null || !result.canGrant()) {
                throw biz(PermissionErrorCode.GRANT_CANNOT_DELEGATE,
                    "Cannot delegate " + grantCheckKey(key)
                        + "; reason=" + (result == null ? "UNKNOWN" : result.reason()));
            }
        }
    }

    /** K8 转授检查五段键（经 BusinessKeys 构造，与 PermissionGrantDomainServiceImpl 共享同一格式锁）。 */
    private String grantCheckKey(PermissionGrantDomainService.GrantCheckKey key) {
        return BusinessKeys.grantCheckKey(key.resourceTypeCode(), key.resourceCode(),
            key.codeType(), key.operationCode(), key.scopeAll());
    }

    private boolean isScopeAll(ApplyGrantPlanReq.GrantRecordKey key) {
        return ScopeModeSupport.toScopeAllForGrant(
            key.scopeMode(), key.resourceCode(), key.codeType());
    }

    private Map<String, Integer> normalizeTypeValues(Map<String, Integer> values) {
        Map<String, Integer> normalized = new HashMap<>();
        values.forEach((code, value) -> normalized.put(normalize(code), value));
        return normalized;
    }

    private void assertMutable(RoleResourcePermission permission) {
        if (permission != null && GrantSource.AUTO_DEP.getValue().equals(permission.getGrantSource())) {
            throw biz(PermissionErrorCode.AUTO_DEP_READONLY);
        }
    }

    private void assertDistinct(Collection<Long> ids, String message) {
        if (ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) {
            throw validation(message);
        }
    }

    private void insertOne(RoleResourcePermission permission) {
        try {
            if (rolePermissionMapper.insert(permission) != 1) {
                throw biz(PermissionErrorCode.PERMISSION_NOT_FOUND,
                    "Permission create did not affect exactly one row");
            }
        } catch (DataIntegrityViolationException exception) {
            throw translateUniqueViolation(exception);
        }
    }

    private void insertBatch(List<RoleResourcePermission> permissions) {
        try {
            if (rolePermissionMapper.insertBatch(permissions) != permissions.size()) {
                throw biz(PermissionErrorCode.PERMISSION_NOT_FOUND,
                    "Permission create count does not match the plan");
            }
        } catch (DataIntegrityViolationException exception) {
            throw translateUniqueViolation(exception);
        }
    }

    private RuntimeException translateUniqueViolation(DataIntegrityViolationException exception) {
        if (DatabaseExceptionSupport.isUniqueViolation(exception,
            "uk_role_resource_permission_manual_direct", "uk_role_resource_permission")) {
            return biz(PermissionErrorCode.DIRECT_PERMISSION_CONFLICT);
        }
        return exception;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private BizException validation(String message) {
        return new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(), message);
    }

    private BizException biz(PermissionErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }

    private BizException biz(PermissionErrorCode errorCode, String message) {
        return new BizException(errorCode.getCode(), message);
    }

    private record ParentChildTypes(String parentTypeCode, List<String> childTypeCodes) {}
}
