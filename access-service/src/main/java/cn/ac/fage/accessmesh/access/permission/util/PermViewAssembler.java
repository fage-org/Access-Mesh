package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult.EffectiveOperationEntry;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewFilter;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult.RoleInfo;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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
 * 接收 {@link PermResult} + {@link PermViewFilter}，
 * 应用过滤和分页逻辑，返回 {@link PermViewResult}。
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
     * 将 {@link PermResult} 转换为 {@link PermViewResult}，
     * 应用指定的过滤条件和分页参数。
     *
     * @param tenantId 租户ID
     * @param result   权限查询结果（来自引擎）
     * @param filter   视图过滤条件（可为 null，使用默认值）
     * @return 权限视图结果
     */
    public PermViewResult assemble(Long tenantId, PermResult result, PermViewFilter filter) {
        List<RolePermEntry> entries = result.allEntries();
        List<EffectiveOperationEntry> effectiveOperationEntries = result.effectiveOperationEntries();
        Map<Long, ResourceEntity> resourceMap = result.resourceMap() != null ? result.resourceMap() : Map.of();
        Map<Long, OperationPermission> operationMap = result.operationMap() != null ? result.operationMap() : Map.of();
        Map<Long, AbstractRole> roleMap = result.roleMap() != null ? result.roleMap() : Map.of();

        if (filter == null) {
            filter = new PermViewFilter();
        }

        // Resolve type codes needed for filtering
        Set<Integer> allResourceTypes = entries.stream()
            .map(RolePermEntry::resourceType)
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
        Map<Long, String> resTypeCodeMap = new HashMap<>();
        for (RolePermEntry e : entries) {
            if (e.resourceEntityId() != null) {
                ResourceEntity res = resourceMap.get(e.resourceEntityId());
                if (res != null && res.getResourceType() != null) {
                    resTypeCodeMap.put(e.resourceEntityId(), resourceTypeCodeMap.get(res.getResourceType()));
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

    // ===== 过滤方法 =====

    private List<RolePermEntry> filterByScopePermissions(List<RolePermEntry> entries, PermViewFilter filter) {
        if (!filter.isIncludeScopePermissions()) {
            return entries.stream()
                .filter(e -> e.dependOn() == null)
                .collect(Collectors.toList());
        }
        return entries;
    }

    private List<RolePermEntry> filterByOperationCodes(List<RolePermEntry> entries, PermViewFilter filter,
                                                        Map<Long, OperationPermission> operationMap,
                                                        List<EffectiveOperationEntry> effectiveOperationEntries) {
        if (filter.getOperationCodes() == null || filter.getOperationCodes().isEmpty()
            || (operationMap.isEmpty() && effectiveOperationEntries.isEmpty())) {
            return entries;
        }
        Set<String> codes = filter.getOperationCodes();
        if (!effectiveOperationEntries.isEmpty()) {
            Set<String> matchedEntryKeys = effectiveOperationEntries.stream()
                .filter(e -> e.operationCode() != null && codes.contains(e.operationCode()))
                .map(this::effectiveSourceKey)
                .collect(Collectors.toSet());
            return entries.stream()
                .filter(e -> matchedEntryKeys.contains(effectiveSourceKey(e)))
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
            List<RolePermEntry> entries,
            PermViewFilter filter) {
        if (effectiveOperationEntries == null || effectiveOperationEntries.isEmpty()) {
            return List.of();
        }
        Set<String> allowedEntryKeys = entries.stream()
            .map(this::effectiveSourceKey)
            .collect(Collectors.toSet());
        Set<String> operationCodes = filter.getOperationCodes();
        return effectiveOperationEntries.stream()
            .filter(e -> allowedEntryKeys.contains(effectiveSourceKey(e)))
            .filter(e -> operationCodes == null || operationCodes.isEmpty()
                || (e.operationCode() != null && operationCodes.contains(e.operationCode())))
            .collect(Collectors.toList());
    }

    private String effectiveSourceKey(RolePermEntry entry) {
        return BusinessKeys.permEntrySourceKey(entry.permissionId(), entry.roleId(), entry.resourceEntityId(),
            entry.resourceType(), entry.grantedBits(), entry.scopeAll());
    }

    private String effectiveSourceKey(EffectiveOperationEntry entry) {
        return BusinessKeys.permEntrySourceKey(entry.permissionId(), entry.roleId(), entry.resourceEntityId(),
            entry.resourceType(), entry.grantedBits(), entry.scopeAll());
    }

    private List<RolePermEntry> filterByResourceTypes(List<RolePermEntry> entries, PermViewFilter filter,
                                                       Map<Long, ResourceEntity> resourceMap,
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
                ResourceEntity res = resourceMap.get(e.resourceEntityId());
                if (res == null || res.getResourceType() == null) {
                    return false;
                }
                String typeCode = resourceTypeCodeMap.get(res.getResourceType());
                return typeCode != null && typeCodes.contains(typeCode);
            })
            .collect(Collectors.toList());
    }

    private List<RolePermEntry> filterByKeyword(List<RolePermEntry> entries, PermViewFilter filter,
                                                 Map<Long, ResourceEntity> resourceMap) {
        if (filter.getResourceKeyword() == null || filter.getResourceKeyword().isBlank()) {
            return entries;
        }
        String keyword = filter.getResourceKeyword().toLowerCase();
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) {
                    return false; // scopeAll 条目没有具体资源名，keyword 过滤时不匹配
                }
                ResourceEntity res = resourceMap.get(e.resourceEntityId());
                return res != null && res.getName() != null
                    && res.getName().toLowerCase().contains(keyword);
            })
            .collect(Collectors.toList());
    }

    private List<RolePermEntry> filterExcludeApiResources(List<RolePermEntry> entries, PermViewFilter filter,
                                                           Map<Long, ResourceEntity> resourceMap,
                                                           Map<Integer, String> resourceTypeCodeMap) {
        if (!filter.isExcludeApiResources()) {
            return entries;
        }
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) {
                    return true;
                }
                ResourceEntity res = resourceMap.get(e.resourceEntityId());
                if (res == null || res.getResourceType() == null) {
                    return true;
                }
                String typeCode = resourceTypeCodeMap.get(res.getResourceType());
                return !cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode.API.equals(typeCode);
            })
            .collect(Collectors.toList());
    }

    private List<RolePermEntry> filterByDomainCode(List<RolePermEntry> entries, PermViewFilter filter,
                                                    Map<Long, ResourceEntity> resourceMap,
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
                ResourceEntity res = resourceMap.get(e.resourceEntityId());
                if (res == null || res.getResourceType() == null) {
                    return false;
                }
                String typeCode = resourceTypeCodeMap.get(res.getResourceType());
                if (typeCode == null) {
                    return false;
                }
                return coveredTypeCodes.contains(typeCode);
            })
            .collect(Collectors.toList());
    }

    // ===== 映射构建方法 =====

    private Map<Long, String> buildDomainCodeMap(List<RolePermEntry> entries,
                                                   Map<Long, ResourceEntity> resourceMap,
                                                   Map<Integer, String> resourceTypeCodeMap,
                                                   Long tenantId) {
        if (resourceMap.isEmpty() || resourceTypeCodeMap.isEmpty()) {
            return Map.of();
        }

        Set<Long> resourceIds = entries.stream()
            .map(RolePermEntry::resourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // T-PERM-060：distinct typeCode 一次批量反查（登录权限串热路径，
        // 逐类型点查为 ≤12 类型 × 3 查询的放大源）
        Set<String> distinctTypeCodes = new LinkedHashSet<>();
        for (Long resId : resourceIds) {
            ResourceEntity res = resourceMap.get(resId);
            if (res == null || res.getResourceType() == null) {
                continue;
            }
            String typeCode = resourceTypeCodeMap.get(res.getResourceType());
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

        Map<Long, String> result = new HashMap<>();
        for (Long resId : resourceIds) {
            ResourceEntity res = resourceMap.get(resId);
            if (res == null || res.getResourceType() == null) {
                continue;
            }
            String typeCode = resourceTypeCodeMap.get(res.getResourceType());
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

    private Map<Long, RoleInfo> buildSourceRoleMap(List<RolePermEntry> entries,
                                                     Map<Long, AbstractRole> roleMap,
                                                     PermViewFilter filter,
                                                     Long tenantId) {
        if (roleMap.isEmpty()) {
            return Map.of();
        }

        Set<Long> roleIds = entries.stream()
            .map(RolePermEntry::roleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<Integer> roleTypeValues = roleIds.stream()
            .map(roleMap::get)
            .filter(Objects::nonNull)
            .map(AbstractRole::getRoleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> roleTypeCodeMap = roleTypeValues.isEmpty() ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "role_type", roleTypeValues);

        // sourceRoleMap 不做截断，始终放入所有来源角色。
        // sourceRoleLimit 仅在调用方按资源条目做截断（见 buildResourcePermissionView）。
        Map<Long, RoleInfo> map = new LinkedHashMap<>();
        for (Long roleId : roleIds) {
            AbstractRole role = roleMap.get(roleId);
            if (role != null) {
                String typeCode = roleTypeCodeMap.get(role.getRoleType());
                map.put(roleId, new RoleInfo(typeCode, role.getExternalId(), role.getName()));
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
        List<RolePermEntry> entries, List<EffectiveOperationEntry> effectiveOperationEntries, PermViewFilter filter,
        Map<Long, ResourceEntity> resourceMap, Map<Long, OperationPermission> operationMap,
        Map<Long, AbstractRole> roleMap, Map<Long, RoleInfo> sourceRoleMap,
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
