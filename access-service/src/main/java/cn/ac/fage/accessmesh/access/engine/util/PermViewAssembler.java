package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.dto.PermViewFilter;
import cn.ac.fage.accessmesh.access.engine.dto.PermViewResult;
import cn.ac.fage.accessmesh.access.engine.dto.PermViewResult.RoleInfo;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.OperationDefinition;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.ResourceDescription;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.RoleDescription;
import cn.ac.fage.accessmesh.access.domain.entity.BizDomain;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.domain.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.domain.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限视图装配器
 * <p>
 * 接收新引擎 {@link GrantSetResult}（T-PERM-091 迁新 execute：保留事实＋有效操作投影＋描述块）
 * ＋ {@link PermViewFilter}，应用过滤和分页逻辑，返回 {@link PermViewResult}。
 * 作为 Spring Bean 注入所需服务依赖。
 * </p>
 */
@Component
public class PermViewAssembler {

    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final BizDomainMapper bizDomainMapper;

    public PermViewAssembler(TypeResolutionService typeResolutionService,
                             DomainClassifyService domainClassifyService,
                             BizDomainMapper bizDomainMapper) {
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.bizDomainMapper = bizDomainMapper;
    }

    /**
     * 将引擎事实结果转换为 {@link PermViewResult}，
     * 应用指定的过滤条件和分页参数。
     *
     * @param tenantId 租户ID
     * @param result   授权事实集合结果（来自新引擎 execute）
     * @param filter   视图过滤条件（可为 null，使用默认值）
     * @return 权限视图结果
     */
    public PermViewResult assemble(Long tenantId, GrantSetResult result, PermViewFilter filter) {
        ResultDetails details = result.details();
        List<GrantFact> entries = details.stageFacts().stream()
            .flatMap(stage -> stage.retainedAfterEvaluation().stream()).toList();
        List<EffectiveOperationEntry> effectiveOperationEntries = details.effectiveOperations();
        Map<Long, ResourceDescription> resourceMap = details.descriptions().resources();
        Map<Long, OperationPermission> operationMap = toOperationMap(details.descriptions().operations());
        Map<Long, RoleDescription> roleMap = details.descriptions().roles();

        if (filter == null) {
            filter = new PermViewFilter();
        }

        // Resolve type codes needed for filtering
        Set<Integer> allResourceTypes = entries.stream()
            .map(GrantFact::resourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(
            tenantId, "resource_type", allResourceTypes);

        // Filtering pipeline
        entries = filterByScopePermissions(entries, filter);
        entries = filterByOperationCodes(entries, filter, operationMap, effectiveOperationEntries);
        entries = filterByResourceTypes(entries, filter, resourceMap, resourceTypeCodeMap);
        entries = filterByKeyword(entries, filter, resourceMap);
        entries = filterExcludeApiResources(entries, filter, resourceMap, resourceTypeCodeMap);
        entries = filterByDomainCode(entries, filter, resourceMap, resourceTypeCodeMap, tenantId);
        effectiveOperationEntries = filterEffectiveOperationEntries(effectiveOperationEntries, entries, filter);

        // Build resource type code map (resourceId -> resourceTypeCode)
        Map<Long, String> resTypeCodeMap = new LinkedHashMap<>();
        for (GrantFact e : entries) {
            if (e.resourceEntityId() != null) {
                ResourceDescription res = resourceMap.get(e.resourceEntityId());
                if (res != null && res.resourceType() != null) {
                    resTypeCodeMap.put(e.resourceEntityId(), resourceTypeCodeMap.get(res.resourceType()));
                }
            }
        }

        // Build domain code map and source role map
        Map<Long, String> domainCodeMap = buildDomainCodeMap(entries, resourceMap, resourceTypeCodeMap, tenantId);
        Map<Long, RoleInfo> sourceRoleMap = buildSourceRoleMap(entries, roleMap, filter, tenantId);

        // Paginate
        return paginate(entries, effectiveOperationEntries, filter, resourceMap, operationMap, roleMap,
            sourceRoleMap, resTypeCodeMap, domainCodeMap);
    }

    /** 操作定义快照转位运算实体索引（OperationDefinition.toCacheRow 公开复用，T-PERM-090 起）。 */
    private static Map<Long, OperationPermission> toOperationMap(Map<Long, OperationDefinition> operations) {
        Map<Long, OperationPermission> operationMap = new LinkedHashMap<>();
        operations.values().forEach(op -> operationMap.put(op.id(), op.toCacheRow()));
        return operationMap;
    }

    // ===== 过滤方法 =====

    private List<GrantFact> filterByScopePermissions(List<GrantFact> entries, PermViewFilter filter) {
        if (!filter.isIncludeScopePermissions()) {
            return entries.stream()
                .filter(e -> e.dependOn() == null)
                .collect(Collectors.toList());
        }
        return entries;
    }

    private List<GrantFact> filterByOperationCodes(List<GrantFact> entries, PermViewFilter filter,
                                                   Map<Long, OperationPermission> operationMap,
                                                   List<EffectiveOperationEntry> effectiveOperationEntries) {
        if (filter.getOperationCodes() == null || filter.getOperationCodes().isEmpty()
            || (operationMap.isEmpty() && effectiveOperationEntries.isEmpty())) {
            return entries;
        }
        Set<String> codes = filter.getOperationCodes();
        if (!effectiveOperationEntries.isEmpty()) {
            Set<Long> matchedSourcePermissionIds = effectiveOperationEntries.stream()
                .filter(e -> e.operationCode() != null && codes.contains(e.operationCode()))
                .map(EffectiveOperationEntry::sourcePermissionId)
                .collect(Collectors.toSet());
            return entries.stream()
                .filter(e -> matchedSourcePermissionIds.contains(e.permissionId()))
                .collect(Collectors.toList());
        }
        return entries.stream()
            .filter(e -> {
                if (e.grantedBits() == null || e.resourceType() == null) {
                    return false;
                }
                OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                    operationMap, e.resourceType(), e.grantedBits());
                return op != null && op.getCode() != null && codes.contains(op.getCode());
            })
            .collect(Collectors.toList());
    }

    private List<EffectiveOperationEntry> filterEffectiveOperationEntries(
            List<EffectiveOperationEntry> effectiveOperationEntries,
            List<GrantFact> entries,
            PermViewFilter filter) {
        if (effectiveOperationEntries == null || effectiveOperationEntries.isEmpty()) {
            return List.of();
        }
        Set<Long> allowedSourcePermissionIds = entries.stream()
            .map(GrantFact::permissionId)
            .collect(Collectors.toSet());
        Set<String> operationCodes = filter.getOperationCodes();
        return effectiveOperationEntries.stream()
            .filter(e -> allowedSourcePermissionIds.contains(e.sourcePermissionId()))
            .filter(e -> operationCodes == null || operationCodes.isEmpty()
                || (e.operationCode() != null && operationCodes.contains(e.operationCode())))
            .collect(Collectors.toList());
    }

    /**
     * 事实↔投影按源授权行主键关联（GrantFact.permissionId ↔ EffectiveOperationEntry.sourcePermissionId）。
     * displayedEntityId 是展示资源——展示展开（PARENT/CHILD）下不等于源授权资源，不作关联键
     * （设计 §6.4 方向优先：父/子投影行保留自身派生来源，不得被来源过滤丢弃）。
     */

    private List<GrantFact> filterByResourceTypes(List<GrantFact> entries, PermViewFilter filter,
                                                  Map<Long, ResourceDescription> resourceMap,
                                                  Map<Integer, String> resourceTypeCodeMap) {
        if (filter.getResourceTypes() == null || filter.getResourceTypes().isEmpty()) {
            return entries;
        }
        Set<String> typeCodes = filter.getResourceTypes();
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) {
                    // scopeAll 条目：按 resourceType 参与过滤
                    if (e.resourceType() == null) return false;
                    String typeCode = resourceTypeCodeMap.get(e.resourceType());
                    return typeCode != null && typeCodes.contains(typeCode);
                }
                ResourceDescription res = resourceMap.get(e.resourceEntityId());
                if (res == null || res.resourceType() == null) {
                    return false;
                }
                String typeCode = resourceTypeCodeMap.get(res.resourceType());
                return typeCode != null && typeCodes.contains(typeCode);
            })
            .collect(Collectors.toList());
    }

    private List<GrantFact> filterByKeyword(List<GrantFact> entries, PermViewFilter filter,
                                            Map<Long, ResourceDescription> resourceMap) {
        if (filter.getResourceKeyword() == null || filter.getResourceKeyword().isBlank()) {
            return entries;
        }
        String keyword = filter.getResourceKeyword().toLowerCase();
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) {
                    return false; // scopeAll 条目没有具体资源名，keyword 过滤时不匹配
                }
                ResourceDescription res = resourceMap.get(e.resourceEntityId());
                return res != null && res.name() != null
                    && res.name().toLowerCase().contains(keyword);
            })
            .collect(Collectors.toList());
    }

    private List<GrantFact> filterExcludeApiResources(List<GrantFact> entries, PermViewFilter filter,
                                                      Map<Long, ResourceDescription> resourceMap,
                                                      Map<Integer, String> resourceTypeCodeMap) {
        if (!filter.isExcludeApiResources()) {
            return entries;
        }
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) {
                    return true;
                }
                ResourceDescription res = resourceMap.get(e.resourceEntityId());
                if (res == null || res.resourceType() == null) {
                    return true;
                }
                String typeCode = resourceTypeCodeMap.get(res.resourceType());
                return !cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode.API.equals(typeCode);
            })
            .collect(Collectors.toList());
    }

    private List<GrantFact> filterByDomainCode(List<GrantFact> entries, PermViewFilter filter,
                                               Map<Long, ResourceDescription> resourceMap,
                                               Map<Integer, String> resourceTypeCodeMap,
                                               Long tenantId) {
        if (filter.getDomainCode() == null || filter.getDomainCode().isBlank()) {
            return entries;
        }
        String domainCode = filter.getDomainCode();
        // T-PERM-055：GLOBAL_PLUS 覆盖集一次预载，循环内 contains 复用（消除逐条目 matchesTypeCode 点查放大）
        Set<String> coveredTypeCodes = domainClassifyService.preloadCoveredTypeCodes(
            tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode);
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) {
                    // scopeAll 条目：按 resourceType 反查域分类，参与域过滤
                    if (e.resourceType() == null) return false;
                    String typeCode = resourceTypeCodeMap.get(e.resourceType());
                    if (typeCode == null) return false;
                    return coveredTypeCodes.contains(typeCode);
                }
                ResourceDescription res = resourceMap.get(e.resourceEntityId());
                if (res == null || res.resourceType() == null) {
                    return false;
                }
                String typeCode = resourceTypeCodeMap.get(res.resourceType());
                if (typeCode == null) {
                    return false;
                }
                return coveredTypeCodes.contains(typeCode);
            })
            .collect(Collectors.toList());
    }

    // ===== 映射构建方法 =====

    private Map<Long, String> buildDomainCodeMap(List<GrantFact> entries,
                                                 Map<Long, ResourceDescription> resourceMap,
                                                 Map<Integer, String> resourceTypeCodeMap,
                                                 Long tenantId) {
        if (resourceMap.isEmpty() || resourceTypeCodeMap.isEmpty()) {
            return Map.of();
        }

        Set<Long> resourceIds = entries.stream()
            .map(GrantFact::resourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // T-PERM-060：distinct typeCode 一次批量反查（登录权限串热路径，
        // 逐类型点查按 distinct 类型数 ×3 放大，类型数无硬上限）
        Set<String> distinctTypeCodes = new LinkedHashSet<>();
        for (Long resId : resourceIds) {
            ResourceDescription res = resourceMap.get(resId);
            if (res == null || res.resourceType() == null) {
                continue;
            }
            String typeCode = resourceTypeCodeMap.get(res.resourceType());
            if (typeCode != null) {
                distinctTypeCodes.add(typeCode);
            }
        }
        Map<String, Long> domainIdByTypeCode = distinctTypeCodes.isEmpty()
            ? Map.of()
            : domainClassifyService.findDomainIdsByTypeCodes(tenantId, distinctTypeCodes);

        Set<Long> domainIds = domainIdByTypeCode.values().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> domainCodeById = loadDomainCodes(tenantId, domainIds);

        Map<Long, String> result = new LinkedHashMap<>();
        for (Long resId : resourceIds) {
            ResourceDescription res = resourceMap.get(resId);
            if (res == null || res.resourceType() == null) {
                continue;
            }
            String typeCode = resourceTypeCodeMap.get(res.resourceType());
            if (typeCode == null) {
                continue;
            }
            Long domainId = domainIdByTypeCode.get(typeCode);
            if (domainId != null && domainCodeById.containsKey(domainId)) {
                result.put(resId, domainCodeById.get(domainId));
            }
        }
        return result;
    }

    private Map<Long, RoleInfo> buildSourceRoleMap(List<GrantFact> entries,
                                                   Map<Long, RoleDescription> roleMap,
                                                   PermViewFilter filter,
                                                   Long tenantId) {
        if (roleMap.isEmpty()) {
            return Map.of();
        }

        Set<Long> roleIds = entries.stream()
            .map(GrantFact::roleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<Integer> roleTypeValues = roleIds.stream()
            .map(roleMap::get)
            .filter(Objects::nonNull)
            .map(RoleDescription::roleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> roleTypeCodeMap = roleTypeValues.isEmpty() ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "role_type", roleTypeValues);

        // sourceRoleMap 不做截断，始终放入所有来源角色。
        // sourceRoleLimit 仅在调用方按资源条目做截断（见 buildResourcePermissionView）。
        Map<Long, RoleInfo> map = new LinkedHashMap<>();
        for (Long roleId : roleIds) {
            RoleDescription role = roleMap.get(roleId);
            if (role != null) {
                String typeCode = roleTypeCodeMap.get(role.roleType());
                map.put(roleId, new RoleInfo(typeCode, role.externalId(), role.name()));
            }
        }
        return map;
    }

    private Map<Long, String> loadDomainCodes(Long tenantId, Set<Long> domainIds) {
        if (domainIds.isEmpty()) {
            return Map.of();
        }
        return bizDomainMapper.selectValidByIds(tenantId, domainIds).stream()
            .collect(Collectors.toMap(BizDomain::getId, BizDomain::getCode));
    }

    // ===== 分页 =====

    private PermViewResult paginate(
        List<GrantFact> entries, List<EffectiveOperationEntry> effectiveOperationEntries, PermViewFilter filter,
        Map<Long, ResourceDescription> resourceMap, Map<Long, OperationPermission> operationMap,
        Map<Long, RoleDescription> roleMap, Map<Long, RoleInfo> sourceRoleMap,
        Map<Long, String> resourceTypeCodeMap, Map<Long, String> domainCodeMap) {
        long totalCount = entries.size();
        int pageNum = filter.getPageNum() > 0 ? filter.getPageNum() : 1;
        int pageSize = filter.getPageSize() > 0 ? filter.getPageSize() : 20;

        // 分页延迟到调用方聚合后执行，此处返回全量已过滤条目
        return PermViewResult.builder()
            .entries(entries)
            .effectiveOperationEntries(effectiveOperationEntries)
            .resourceMap(resourceMap)
            .operationMap(operationMap)
            .roleMap(roleMap)
            .sourceRoleMap(sourceRoleMap)
            .resourceTypeCodeMap(resourceTypeCodeMap)
            .domainCodeMap(domainCodeMap)
            .totalCount(totalCount)
            .pageNum(pageNum)
            .pageSize(pageSize)
            .hasNext(false)
            .build();
    }
}
