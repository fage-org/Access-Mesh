package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.domain.PcPermissionConflictRule;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.ConflictRuleListReq;
import org.dromara.permission.domain.dto.ConflictRuleSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.ConflictDetectionPageVo;
import org.dromara.permission.domain.vo.ConflictRuleVo;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.event.PermissionGovernanceEventPublisher;
import org.dromara.permission.event.PermissionWriteRefreshEventPublisher;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.ConflictRuleService;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionVersionService;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConflictRuleServiceImpl implements ConflictRuleService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final PcPermissionConflictRuleMapper conflictRuleMapper;
    private final PcUserRoleMapper userRoleMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PcResourceEntityMapper resourceEntityMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PermissionBridgeSupport permissionBridgeSupport;
    private final PermissionGovernanceEventPublisher governanceEventPublisher;
    private final PermissionVersionService permissionVersionService;
    private final PermissionWriteRefreshEventPublisher permissionWriteRefreshEventPublisher;
    private final PermissionChangeLogService permissionChangeLogService;

    @Override
    public List<ConflictRuleVo> list(ConflictRuleListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        return loadRules(req.getTenantId(), req.getBizDomainId(), req.getResourceTypeValue()).stream()
            .map(this::toVo)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ConflictRuleSaveReq req) {
        if (req == null || req.getTenantId() == null || req.getFirstOperationPermissionId() == null
            || req.getSecondOperationPermissionId() == null) {
            return;
        }
        long first = req.getFirstOperationPermissionId();
        long second = req.getSecondOperationPermissionId();
        if (first == second) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "conflict rule operations must differ");
        }
        if (first > second) {
            long tmp = first;
            first = second;
            second = tmp;
        }
        PcOperationPermission firstOperation = permissionBridgeSupport.loadOperation(req.getTenantId(), first);
        PcOperationPermission secondOperation = permissionBridgeSupport.loadOperation(req.getTenantId(), second);
        validateRuleOperationTypes(req.getResourceTypeValue(), firstOperation, secondOperation);
        assertNoDuplicateRule(req.getTenantId(), req.getBizDomainId(), first, second, req.getResourceTypeValue(), req.getId());
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcPermissionConflictRule entity = conflictRuleMapper.selectOne(new LambdaQueryWrapper<PcPermissionConflictRule>()
                .eq(PcPermissionConflictRule::getTenantId, req.getTenantId())
                .eq(PcPermissionConflictRule::getId, req.getId())
                .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity == null) {
                return;
            }
            PcPermissionConflictRule oldSnapshot = copyRule(entity);
            entity.setBizDomainId(req.getBizDomainId());
            entity.setFirstOperationPermissionId(first);
            entity.setSecondOperationPermissionId(second);
            entity.setResourceTypeValue(req.getResourceTypeValue());
            entity.setUpdatedAt(now);
            conflictRuleMapper.updateById(entity);
            recordWriteChain(buildWriteContext(req.getTenantId(), entity.getBizDomainId(),
                    req.getRequestId(), req.getChangeSource(), "saveConflictRule", "save-conflict-rule"),
                entity.getId(),
                new ChangeLogParam()
                    .setEntityType("permission_conflict_rule")
                    .setEntityId(entity.getId())
                    .setOperation("UPDATE")
                    .setOldSnapshot(oldSnapshot)
                    .setNewSnapshot(copyRule(entity))
                    .setChangeReason(req.getChangeReason()));
            return;
        }
        PcPermissionConflictRule entity = new PcPermissionConflictRule();
        entity.setTenantId(req.getTenantId());
        entity.setBizDomainId(req.getBizDomainId());
        entity.setFirstOperationPermissionId(first);
        entity.setSecondOperationPermissionId(second);
        entity.setResourceTypeValue(req.getResourceTypeValue());
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        conflictRuleMapper.insert(entity);
        recordWriteChain(buildWriteContext(req.getTenantId(), entity.getBizDomainId(),
                req.getRequestId(), req.getChangeSource(), "saveConflictRule", "save-conflict-rule"),
            entity.getId(),
            new ChangeLogParam()
                .setEntityType("permission_conflict_rule")
                .setEntityId(entity.getId())
                .setOperation("INSERT")
                .setNewSnapshot(copyRule(entity))
                .setChangeReason(req.getChangeReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcPermissionConflictRule> entities = conflictRuleMapper.selectList(new LambdaQueryWrapper<PcPermissionConflictRule>()
            .eq(PcPermissionConflictRule::getTenantId, req.getTenantId())
            .in(PcPermissionConflictRule::getId, req.getIds())
            .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (entities.isEmpty()) {
            return;
        }
        for (PcPermissionConflictRule entity : entities) {
            PcPermissionConflictRule oldSnapshot = copyRule(entity);
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            conflictRuleMapper.updateById(entity);
            permissionChangeLogService.writeChangeLog(new ChangeLogParam()
                .setTenantId(req.getTenantId())
                .setBizDomainId(entity.getBizDomainId())
                .setEntityType("permission_conflict_rule")
                .setEntityId(entity.getId())
                .setOperation("DELETE")
                .setOldSnapshot(oldSnapshot)
                .setNewSnapshot(copyRule(entity))
                .setChangeReason(req.getChangeReason())
                .setRequestId(req.getRequestId())
                .setChangeSource(resolveChangeSource(req.getChangeSource())));
        }
        Long triggerEntityId = entities.size() == 1 ? entities.get(0).getId() : null;
        recordVersionRefresh(buildWriteContext(req.getTenantId(), resolveCommonBizDomainId(entities),
                req.getRequestId(), req.getChangeSource(), "removeConflictRule", "remove-conflict-rule"),
            triggerEntityId);
    }

    @Override
    public List<ConflictViolationVo> detect(ConflictDetectReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        if (req.getAbstractUserId() == null && req.getAbstractRoleId() == null) {
            long total = conflictRuleMapper.countPagedConflictViolations(
                req.getTenantId(), req.getBizDomainId(), req.getResourceEntityId());
            if (total <= 0) {
                return new ArrayList<>();
            }
            List<ConflictViolationVo> items = conflictRuleMapper.selectPagedConflictViolations(
                req.getTenantId(), req.getBizDomainId(), req.getResourceEntityId(), 0L, total);
            if (!items.isEmpty()) {
                governanceEventPublisher.publishConflictDetected(req, items, total);
            }
            return items;
        }
        return detectScopedViolations(req);
    }

    @Override
    public ConflictDetectionPageVo detectPage(ConflictDetectReq req) {
        ConflictDetectionPageVo page = emptyPage(req);
        if (req == null || req.getTenantId() == null) {
            return page;
        }
        if (req.getAbstractUserId() == null && req.getAbstractRoleId() == null) {
            return detectPagedConflicts(req, page);
        }
        List<ConflictViolationVo> normalized = detectScopedViolations(req);
        page.setTotal(normalized.size());
        page.setItems(paginate(normalized, page.getPageNum(), page.getPageSize()));
        return page;
    }

    private List<ConflictViolationVo> detectScopedViolations(ConflictDetectReq req) {
        List<PcPermissionConflictRule> rules = loadRules(req.getTenantId(), req.getBizDomainId(), null);
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }
        List<PcRoleResourcePermission> permissions = loadPermissions(req);
        if (permissions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PcResourceEntity> resourceMap = loadResources(permissions);
        Map<Long, PcAbstractRole> roleMap = loadRoles(permissions);
        permissions = permissions.stream()
            .filter(permission -> includePermission(permission, roleMap, resourceMap, req))
            .collect(Collectors.toList());
        if (permissions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<PcRoleResourcePermission>> permissionsByRole = permissions.stream()
            .collect(Collectors.groupingBy(PcRoleResourcePermission::getAbstractRoleId, LinkedHashMap::new, Collectors.toList()));
        List<ConflictViolationVo> violations = new ArrayList<>();
        if (shouldScanUsers(req)) {
            violations.addAll(scanUserViolations(req, permissionsByRole, resourceMap, roleMap, rules));
        }
        if (shouldScanRoles(req)) {
            violations.addAll(scanRoleViolations(req, permissionsByRole, resourceMap, roleMap, rules));
        }
        List<ConflictViolationVo> normalized = normalizeViolations(violations);
        if (!normalized.isEmpty()) {
            governanceEventPublisher.publishConflictDetected(req, normalized, normalized.size());
        }
        return normalized;
    }

    private ConflictDetectionPageVo detectPagedConflicts(ConflictDetectReq req, ConflictDetectionPageVo page) {
        long total = conflictRuleMapper.countPagedConflictViolations(
            req.getTenantId(), req.getBizDomainId(), req.getResourceEntityId());
        page.setTotal(total);
        if (total <= 0) {
            return page;
        }
        long offset = (long) (page.getPageNum() - 1) * page.getPageSize();
        List<ConflictViolationVo> items = conflictRuleMapper.selectPagedConflictViolations(
            req.getTenantId(), req.getBizDomainId(), req.getResourceEntityId(), offset, page.getPageSize());
        page.setItems(items);
        if (!items.isEmpty()) {
            governanceEventPublisher.publishConflictDetected(req, items, total);
        }
        return page;
    }

    private List<PcPermissionConflictRule> loadRules(Long tenantId, Long bizDomainId, Integer resourceTypeValue) {
        LambdaQueryWrapper<PcPermissionConflictRule> query = new LambdaQueryWrapper<PcPermissionConflictRule>()
            .eq(PcPermissionConflictRule::getTenantId, tenantId)
            .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (bizDomainId != null) {
            query.and(wrapper -> wrapper.eq(PcPermissionConflictRule::getBizDomainId, bizDomainId)
                .or().isNull(PcPermissionConflictRule::getBizDomainId));
        }
        if (resourceTypeValue != null) {
            query.and(wrapper -> wrapper.eq(PcPermissionConflictRule::getResourceTypeValue, resourceTypeValue)
                .or().isNull(PcPermissionConflictRule::getResourceTypeValue));
        }
        query.orderByAsc(PcPermissionConflictRule::getBizDomainId)
            .orderByAsc(PcPermissionConflictRule::getResourceTypeValue)
            .orderByAsc(PcPermissionConflictRule::getId);
        return conflictRuleMapper.selectList(query);
    }

    private void assertNoDuplicateRule(Long tenantId, Long bizDomainId, Long first, Long second,
                                       Integer resourceTypeValue, Long currentId) {
        List<PcPermissionConflictRule> existingRules = conflictRuleMapper.selectList(new LambdaQueryWrapper<PcPermissionConflictRule>()
            .eq(PcPermissionConflictRule::getTenantId, tenantId)
            .eq(PcPermissionConflictRule::getFirstOperationPermissionId, first)
            .eq(PcPermissionConflictRule::getSecondOperationPermissionId, second)
            .eq(PcPermissionConflictRule::getDeleteFlag, PermissionConstants.NOT_DELETED));
        boolean duplicated = existingRules.stream().anyMatch(rule ->
            !Objects.equals(rule.getId(), currentId)
                && Objects.equals(normalizeNullable(rule.getBizDomainId()), normalizeNullable(bizDomainId))
                && Objects.equals(normalizeNullable(rule.getResourceTypeValue()), normalizeNullable(resourceTypeValue)));
        if (duplicated) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "duplicate conflict rule");
        }
    }

    private List<PcRoleResourcePermission> loadPermissions(ConflictDetectReq req) {
        LambdaQueryWrapper<PcRoleResourcePermission> query = new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, req.getTenantId())
            .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getAbstractRoleId() != null) {
            query.eq(PcRoleResourcePermission::getAbstractRoleId, req.getAbstractRoleId());
        }
        if (req.getResourceEntityId() != null) {
            query.eq(PcRoleResourcePermission::getResourceEntityId, req.getResourceEntityId());
        }
        return roleResourcePermissionMapper.selectList(query);
    }

    private Map<Long, PcResourceEntity> loadResources(Collection<PcRoleResourcePermission> permissions) {
        Set<Long> resourceIds = permissions.stream()
            .map(PcRoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return resourceEntityMapper.selectBatchIds(resourceIds).stream()
            .filter(Objects::nonNull)
            .filter(resource -> PermissionConstants.NOT_DELETED.equals(resource.getDeleteFlag()))
            .collect(Collectors.toMap(PcResourceEntity::getId, resource -> resource));
    }

    private Map<Long, PcAbstractRole> loadRoles(Collection<PcRoleResourcePermission> permissions) {
        Set<Long> roleIds = permissions.stream()
            .map(PcRoleResourcePermission::getAbstractRoleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return abstractRoleMapper.selectBatchIds(roleIds).stream()
            .filter(Objects::nonNull)
            .filter(role -> PermissionConstants.NOT_DELETED.equals(role.getDeleteFlag()))
            .collect(Collectors.toMap(PcAbstractRole::getId, role -> role));
    }

    private boolean includePermission(PcRoleResourcePermission permission, Map<Long, PcAbstractRole> roleMap,
                                      Map<Long, PcResourceEntity> resourceMap, ConflictDetectReq req) {
        PcAbstractRole role = roleMap.get(permission.getAbstractRoleId());
        PcResourceEntity resource = resourceMap.get(permission.getResourceEntityId());
        if (role == null || resource == null) {
            return false;
        }
        if (req.getBizDomainId() != null) {
            if (!matchesDomain(req.getBizDomainId(), role.getBizDomainId())) {
                return false;
            }
            if (!matchesDomain(req.getBizDomainId(), resource.getBizDomainId())) {
                return false;
            }
        }
        return true;
    }

    private List<ConflictViolationVo> scanUserViolations(ConflictDetectReq req,
                                                         Map<Long, List<PcRoleResourcePermission>> permissionsByRole,
                                                         Map<Long, PcResourceEntity> resourceMap,
                                                         Map<Long, PcAbstractRole> roleMap,
                                                         List<PcPermissionConflictRule> rules) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<PcUserRole> query = new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, req.getTenantId())
            .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED)
            .and(wrapper -> wrapper.isNull(PcUserRole::getValidFrom).or().le(PcUserRole::getValidFrom, now))
            .and(wrapper -> wrapper.isNull(PcUserRole::getValidTo).or().ge(PcUserRole::getValidTo, now));
        if (req.getAbstractUserId() != null) {
            query.eq(PcUserRole::getAbstractUserId, req.getAbstractUserId());
        }
        if (req.getAbstractRoleId() != null) {
            query.eq(PcUserRole::getAbstractRoleId, req.getAbstractRoleId());
        }
        List<PcUserRole> assignments = userRoleMapper.selectList(query).stream()
            .filter(assignment -> permissionsByRole.containsKey(assignment.getAbstractRoleId()))
            .filter(assignment -> {
                if (req.getBizDomainId() == null) {
                    return true;
                }
                PcAbstractRole role = roleMap.get(assignment.getAbstractRoleId());
                return role != null && matchesDomain(req.getBizDomainId(), role.getBizDomainId());
            })
            .collect(Collectors.toList());
        if (assignments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<PcUserRole>> assignmentsByUser = assignments.stream()
            .collect(Collectors.groupingBy(PcUserRole::getAbstractUserId, LinkedHashMap::new, Collectors.toList()));
        List<ConflictViolationVo> violations = new ArrayList<>();
        for (Map.Entry<Long, List<PcUserRole>> entry : assignmentsByUser.entrySet()) {
            List<PcRoleResourcePermission> userPermissions = entry.getValue().stream()
                .map(PcUserRole::getAbstractRoleId)
                .distinct()
                .flatMap(roleId -> permissionsByRole.getOrDefault(roleId, Collections.emptyList()).stream())
                .collect(Collectors.toList());
            violations.addAll(detectViolations(req, rules, resourceMap, userPermissions, entry.getKey(), null, "USER"));
        }
        return violations;
    }

    private List<ConflictViolationVo> scanRoleViolations(ConflictDetectReq req,
                                                         Map<Long, List<PcRoleResourcePermission>> permissionsByRole,
                                                         Map<Long, PcResourceEntity> resourceMap,
                                                         Map<Long, PcAbstractRole> roleMap,
                                                         List<PcPermissionConflictRule> rules) {
        List<Long> roleIds;
        if (req.getAbstractRoleId() != null) {
            roleIds = permissionsByRole.containsKey(req.getAbstractRoleId())
                ? List.of(req.getAbstractRoleId()) : Collections.emptyList();
        } else {
            roleIds = new ArrayList<>(permissionsByRole.keySet());
        }
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConflictViolationVo> violations = new ArrayList<>();
        for (Long roleId : roleIds) {
            PcAbstractRole role = roleMap.get(roleId);
            if (role == null) {
                continue;
            }
            if (req.getBizDomainId() != null && !matchesDomain(req.getBizDomainId(), role.getBizDomainId())) {
                continue;
            }
            violations.addAll(detectViolations(req, rules, resourceMap,
                permissionsByRole.getOrDefault(roleId, Collections.emptyList()), null, roleId, "ROLE"));
        }
        return violations;
    }

    private List<ConflictViolationVo> detectViolations(ConflictDetectReq req, List<PcPermissionConflictRule> rules,
                                                       Map<Long, PcResourceEntity> resourceMap,
                                                       List<PcRoleResourcePermission> permissions,
                                                       Long abstractUserId, Long abstractRoleId,
                                                       String subjectLabel) {
        Map<Long, Set<Long>> resourceToOperations = new LinkedHashMap<>();
        for (PcRoleResourcePermission permission : permissions) {
            PcResourceEntity resource = resourceMap.get(permission.getResourceEntityId());
            if (resource == null) {
                continue;
            }
            if (req.getBizDomainId() != null && !matchesDomain(req.getBizDomainId(), resource.getBizDomainId())) {
                continue;
            }
            resourceToOperations.computeIfAbsent(permission.getResourceEntityId(), ignored -> new LinkedHashSet<>())
                .add(permission.getOperationPermissionId());
        }
        if (resourceToOperations.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConflictViolationVo> violations = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : resourceToOperations.entrySet()) {
            PcResourceEntity resource = resourceMap.get(entry.getKey());
            if (resource == null) {
                continue;
            }
            List<PcPermissionConflictRule> applicableRules = rules.stream()
                .filter(rule -> ruleAppliesToResource(rule, resource))
                .collect(Collectors.toList());
            if (applicableRules.isEmpty()) {
                continue;
            }
            Set<Long> operationIds = entry.getValue();
            for (PcPermissionConflictRule rule : applicableRules) {
                if (!operationIds.contains(rule.getFirstOperationPermissionId())
                    || !operationIds.contains(rule.getSecondOperationPermissionId())) {
                    continue;
                }
                ConflictViolationVo violation = new ConflictViolationVo();
                violation.setConflictRuleId(rule.getId());
                violation.setAbstractUserId(abstractUserId);
                violation.setAbstractRoleId(abstractRoleId);
                violation.setBizDomainId(resource.getBizDomainId());
                violation.setResourceEntityId(resource.getId());
                violation.setResourceTypeValue(resource.getResourceType());
                violation.setFirstOperationPermissionId(rule.getFirstOperationPermissionId());
                violation.setSecondOperationPermissionId(rule.getSecondOperationPermissionId());
                violation.setDescription(subjectLabel + " owns conflicting operations "
                    + rule.getFirstOperationPermissionId() + " and " + rule.getSecondOperationPermissionId());
                violations.add(violation);
            }
        }
        return violations;
    }

    private boolean ruleAppliesToResource(PcPermissionConflictRule rule, PcResourceEntity resource) {
        return (rule.getResourceTypeValue() == null || Objects.equals(rule.getResourceTypeValue(), resource.getResourceType()))
            && (rule.getBizDomainId() == null || Objects.equals(rule.getBizDomainId(), resource.getBizDomainId()));
    }

    private List<ConflictViolationVo> normalizeViolations(List<ConflictViolationVo> violations) {
        if (violations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ConflictViolationVo> deduplicated = new LinkedHashMap<>();
        for (ConflictViolationVo violation : violations) {
            String key = String.join(":",
                Objects.toString(violation.getConflictRuleId(), ""),
                Objects.toString(violation.getAbstractUserId(), ""),
                Objects.toString(violation.getAbstractRoleId(), ""),
                Objects.toString(violation.getResourceEntityId(), ""));
            deduplicated.putIfAbsent(key, violation);
        }
        return deduplicated.values().stream()
            .sorted(Comparator
                .comparing((ConflictViolationVo violation) -> violation.getAbstractUserId() == null ? Long.MAX_VALUE : violation.getAbstractUserId())
                .thenComparing(violation -> violation.getAbstractRoleId() == null ? Long.MAX_VALUE : violation.getAbstractRoleId())
                .thenComparing(violation -> violation.getResourceEntityId() == null ? Long.MAX_VALUE : violation.getResourceEntityId())
                .thenComparing(violation -> violation.getConflictRuleId() == null ? Long.MAX_VALUE : violation.getConflictRuleId()))
            .collect(Collectors.toList());
    }

    private List<ConflictViolationVo> paginate(List<ConflictViolationVo> violations, int pageNum, int pageSize) {
        int fromIndex = Math.max(0, (pageNum - 1) * pageSize);
        if (fromIndex >= violations.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(violations.size(), fromIndex + pageSize);
        return new ArrayList<>(violations.subList(fromIndex, toIndex));
    }

    private ConflictDetectionPageVo emptyPage(ConflictDetectReq req) {
        ConflictDetectionPageVo page = new ConflictDetectionPageVo();
        page.setPageNum(req != null && req.getPageNum() != null ? req.getPageNum() : DEFAULT_PAGE_NUM);
        page.setPageSize(req != null && req.getPageSize() != null ? req.getPageSize() : DEFAULT_PAGE_SIZE);
        page.setItems(new ArrayList<>());
        return page;
    }

    private boolean shouldScanUsers(ConflictDetectReq req) {
        return req.getAbstractUserId() != null || (req.getAbstractUserId() == null && req.getAbstractRoleId() == null);
    }

    private boolean shouldScanRoles(ConflictDetectReq req) {
        return req.getAbstractRoleId() != null || (req.getAbstractUserId() == null && req.getAbstractRoleId() == null);
    }

    private boolean matchesDomain(Long expectedBizDomainId, Long actualBizDomainId) {
        return actualBizDomainId == null || Objects.equals(expectedBizDomainId, actualBizDomainId);
    }

    private PermissionContext buildWriteContext(Long tenantId, Long bizDomainId, String requestId,
                                                String changeSource, String action, String versionRemark) {
        PermissionContext ctx = new PermissionContext(tenantId, null, bizDomainId, InheritMode.NONE, null);
        ctx.setAction(action);
        ctx.setRequestId(requestId);
        ctx.setChangeSource(resolveChangeSource(changeSource));
        ctx.setVersionRemark(versionRemark);
        return ctx;
    }

    private void recordWriteChain(PermissionContext ctx, Long triggerEntityId, ChangeLogParam changeLogParam) {
        permissionChangeLogService.writeChangeLog(changeLogParam
            .setTenantId(ctx.getTenantId())
            .setBizDomainId(changeLogParam.getBizDomainId() == null ? ctx.getBizDomainId() : changeLogParam.getBizDomainId())
            .setRequestId(changeLogParam.getRequestId() == null ? ctx.getRequestId() : changeLogParam.getRequestId())
            .setChangeSource(changeLogParam.getChangeSource() == null ? ctx.getChangeSource() : changeLogParam.getChangeSource()));
        recordVersionRefresh(ctx, triggerEntityId);
    }

    private void recordVersionRefresh(PermissionContext ctx, Long triggerEntityId) {
        PcPermissionVersion version = permissionVersionService.bumpVersion(
            ctx.getTenantId(), "permission_conflict_rule", triggerEntityId, ctx.getVersionRemark());
        permissionWriteRefreshEventPublisher.publish(ctx, version);
    }

    private Long resolveCommonBizDomainId(List<PcPermissionConflictRule> entities) {
        Long common = null;
        for (PcPermissionConflictRule entity : entities) {
            if (common == null) {
                common = entity.getBizDomainId();
                continue;
            }
            if (!Objects.equals(common, entity.getBizDomainId())) {
                return null;
            }
        }
        return common;
    }

    private String resolveChangeSource(String changeSource) {
        return changeSource == null || changeSource.isBlank() ? "API" : changeSource;
    }

    private void validateRuleOperationTypes(Integer resourceTypeValue, PcOperationPermission firstOperation,
                                            PcOperationPermission secondOperation) {
        Integer firstResourceType = firstOperation.getResourceType();
        Integer secondResourceType = secondOperation.getResourceType();
        if (resourceTypeValue != null) {
            if ((firstResourceType != null && !Objects.equals(firstResourceType, resourceTypeValue))
                || (secondResourceType != null && !Objects.equals(secondResourceType, resourceTypeValue))) {
                throw new PermissionServiceException(PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH);
            }
            return;
        }
        if (firstResourceType != null && secondResourceType != null && !Objects.equals(firstResourceType, secondResourceType)) {
            throw new PermissionServiceException(PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH,
                "conflict rule operations must target the same resource type");
        }
    }

    private <T> T normalizeNullable(T value) {
        return value;
    }

    private PcPermissionConflictRule copyRule(PcPermissionConflictRule source) {
        PcPermissionConflictRule target = new PcPermissionConflictRule();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private ConflictRuleVo toVo(PcPermissionConflictRule entity) {
        ConflictRuleVo vo = new ConflictRuleVo();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
