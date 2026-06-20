package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionTreeReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp.TreeNode;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp.ScopeGroup;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionQueryAppService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.permission.util.SnapshotAssembler;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限查询应用服务实现
 * <p>
 * 提供高级查询功能：资源查询、范围查询、权限树查询、接口快照。
 * 从 PermissionServiceImpl 提取。
 * </p>
 */
@Service
public class PermissionQueryAppServiceImpl implements PermissionQueryAppService {

    private static final Logger log = LoggerFactory.getLogger(PermissionQueryAppServiceImpl.class);

    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final SubjectDomainService subjectDomainService;
    private final PermissionConflictDomainService permissionConflictDomainService;
    private final PermissionConditionDomainService permissionConditionDomainService;
    private final TypeResolutionService typeResolutionService;
    private final CacheService cacheService;
    private final PermissionVersionDomainService permissionVersionDomainService;
    private final DomainClassifyService domainClassifyService;
    private final PermQueryEngine engine;
    private final SnapshotAssembler snapshotAssembler;

    /**
     * 构造函数注入依赖
     *
     * @param resourceEntityMapper             资源实体数据访问层
     * @param operationPermissionMapper        操作权限数据访问层
     * @param subjectDomainService             主体领域服务
     * @param permissionConflictDomainService  权限冲突领域服务
     * @param permissionConditionDomainService 权限条件领域服务
     * @param typeResolutionService            类型解析服务
     * @param cacheService                     缓存服务
     * @param permissionVersionDomainService   权限版本领域服务
     * @param domainClassifyService            域分类服务
     * @param engine                           权限查询引擎
     * @param snapshotAssembler                快照装配器
     */
    public PermissionQueryAppServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                          OperationPermissionMapper operationPermissionMapper,
                                          SubjectDomainService subjectDomainService,
                                          PermissionConflictDomainService permissionConflictDomainService,
                                          PermissionConditionDomainService permissionConditionDomainService,
                                          TypeResolutionService typeResolutionService,
                                          CacheService cacheService,
                                          PermissionVersionDomainService permissionVersionDomainService,
                                          DomainClassifyService domainClassifyService,
                                          PermQueryEngine engine,
                                          SnapshotAssembler snapshotAssembler) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.subjectDomainService = subjectDomainService;
        this.permissionConflictDomainService = permissionConflictDomainService;
        this.permissionConditionDomainService = permissionConditionDomainService;
        this.typeResolutionService = typeResolutionService;
        this.cacheService = cacheService;
        this.permissionVersionDomainService = permissionVersionDomainService;
        this.domainClassifyService = domainClassifyService;
        this.engine = engine;
        this.snapshotAssembler = snapshotAssembler;
    }

    /**
     * 查询用户有权限的资源列表
     * <p>
     * 使用 forUserView 查询管线获取用户的所有权限（scopeAll 和实例级）。
     * 支持按资源类型、操作码过滤，支持继承权限和子资源展开。
     * 返回结果包含权限版本号用于客户端缓存校验。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      资源查询请求
     * @return 资源查询响应，包含资源列表和权限版本号
     */
    // ===== queryResources =====

    @Override
    @Transactional(readOnly = true)
    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), "", 60);

        // Use forUserView to get all permissions (both scopeAll and instance-level)
        PermQuery q = PermQuery.forUserView(tenantId, userId);
        if (req.context() != null) {
            q.setContext(req.context());
        }
        PermResult r = engine.query(q);
        Set<Long> effectiveRoles = r.matchedRoleIds();
        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(userId, tenantId, effectiveRoles);
        return buildQueryResourcesResponse(r, req, permissionVersion, tenantId);
    }

    private QueryResourcesResp buildQueryResourcesResponse(PermResult r, QueryResourcesReq req,
                                                            String permissionVersion, Long tenantId) {
        Set<String> resourceTypeCodes = new HashSet<>(req.resourceTypeCodes());
        Set<String> operationCodes = new HashSet<>(req.operationCodes());
        String codeType = req.codeType();
        String domainCode = req.domainCode();
        Map<Long, ResourceEntity> resMap = r.resourceMap() != null
            ? new LinkedHashMap<>(r.resourceMap()) : new LinkedHashMap<>();
        Map<Long, OperationPermission> opMap = r.operationMap() != null ? r.operationMap() : Map.of();

        // TODO: treeMode — 需要树结构响应 DTO 支持

        // Expand entries based on includeInherited / includeChildren
        List<RolePermEntry> allEntries = r.allEntries();
        if (Boolean.TRUE.equals(req.includeChildren()) || Boolean.TRUE.equals(req.includeInherited())) {
            List<RolePermEntry> expandedEntries = expandResourceScope(tenantId, r, req, resMap);
            allEntries = new ArrayList<>(r.allEntries());
            allEntries.addAll(expandedEntries);
        }

        // Collect all resource types for batch resolution
        Set<Integer> resourceTypesNeeded = new HashSet<>();
        for (RolePermEntry e : allEntries) {
            if (e.resourceType() != null) {
                resourceTypesNeeded.add(e.resourceType());
            }
        }
        Map<Integer, String> resourceTypeCodeMap = !resourceTypesNeeded.isEmpty()
            ? typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypesNeeded)
            : Map.of();

        // 按操作码过滤
        // 先构建 code→OperationPermission 索引用于 opMatch
        Map<String, OperationPermission> opByCode = opMap.values().stream()
            .collect(Collectors.toMap(
                o -> o.getResourceType() + ":" + o.getCode(),
                o -> o, (a, b) -> a));

        // 使用引擎的 covers() 覆盖判定：MANAGE 覆盖 VIEW 等
        java.util.function.Predicate<RolePermEntry> opMatch = entry -> {
            if (entry.grantedBits() == null || entry.resourceType() == null) return false;
            if (operationCodes.isEmpty()) return true;
            OperationPermission granted = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                opMap, entry.resourceType(), entry.grantedBits());
            if (granted == null) return false;
            // 检查授予的操作是否覆盖请求中的任一操作
            return operationCodes.stream().anyMatch(reqOp -> {
                OperationPermission target = opByCode.get(entry.resourceType() + ":" + reqOp);
                return target != null && OperationPermissionUtils.covers(granted, target);
            });
        };

        // 按 domainCode 过滤
        java.util.function.Predicate<RolePermEntry> domainMatch = entry -> {
            if (domainCode == null || domainCode.isBlank()) return true;
            if (entry.resourceType() == null) return false;
            String rtCode = resourceTypeCodeMap.get(entry.resourceType());
            return rtCode != null && domainClassifyService.matchesTypeCode(
                tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, rtCode);
        };

        // 按 codeType 过滤
        java.util.function.Predicate<RolePermEntry> codeTypeMatch = entry -> {
            if (codeType == null || codeType.isBlank()) return true;
            if (entry.resourceEntityId() == null) return true; // scopeAll 不限 codeType
            ResourceEntity res = resMap.get(entry.resourceEntityId());
            return res == null || res.getCodeType() == null
                || codeType.equalsIgnoreCase(res.getCodeType());
        };

        List<QueryResourcesResp.ResourceEntry> entries = new ArrayList<>();

        // 1. scopeAll entries — filter by operationCodes, domainCode, codeType
        Map<Integer, List<RolePermEntry>> scopeAllByType = allEntries.stream()
            .filter(e -> Boolean.TRUE.equals(e.scopeAll()) && e.resourceEntityId() == null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Integer, List<RolePermEntry>> e : scopeAllByType.entrySet()) {
            String rtCode = resourceTypeCodeMap.get(e.getKey());
            if (rtCode == null || (!resourceTypeCodes.isEmpty() && !resourceTypeCodes.contains(rtCode))) {
                continue;
            }
            // 先按 operationCodes / domainCode / codeType 过滤条目
            List<RolePermEntry> matchedPerms = e.getValue().stream()
                .filter(opMatch).filter(domainMatch).filter(codeTypeMatch).toList();
            if (matchedPerms.isEmpty()) continue;

            Set<String> ops = matchedPerms.stream()
                .map(entry -> OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull).map(OperationPermission::getCode).filter(Objects::nonNull)
                .filter(op -> operationCodes.isEmpty() || operationCodes.contains(op))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ops.isEmpty() && !operationCodes.isEmpty()) continue;

            List<Long> roleIds = matchedPerms.stream().map(RolePermEntry::roleId).filter(Objects::nonNull).distinct().toList();
            List<Long> permIds = matchedPerms.stream().map(RolePermEntry::permissionId).filter(Objects::nonNull).distinct().toList();
            List<String> sources = matchedPerms.stream().map(RolePermEntry::grantSource).filter(Objects::nonNull).distinct().toList();
            entries.add(new QueryResourcesResp.ResourceEntry(
                rtCode, null, null, null, false, true,
                new ArrayList<>(ops), roleIds, permIds, sources));
        }

        // 2. Instance-level entries — filter by resourceType, operationCodes, domainCode, codeType
        Map<Long, List<RolePermEntry>> byResource = allEntries.stream()
            .filter(e -> e.resourceEntityId() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceEntityId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<RolePermEntry>> e : byResource.entrySet()) {
            ResourceEntity res = resMap.get(e.getKey());
            if (res == null) continue;
            String rtCode = resourceTypeCodeMap.get(res.getResourceType());
            if (rtCode == null || (!resourceTypeCodes.isEmpty() && !resourceTypeCodes.contains(rtCode))) {
                continue;
            }
            // 先按 operationCodes / domainCode / codeType 过滤条目
            List<RolePermEntry> matchedPerms = e.getValue().stream()
                .filter(opMatch).filter(domainMatch).filter(codeTypeMatch).toList();
            if (matchedPerms.isEmpty()) continue;

            Set<String> ops = matchedPerms.stream()
                .map(entry -> OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull).map(OperationPermission::getCode).filter(Objects::nonNull)
                .filter(op -> operationCodes.isEmpty() || operationCodes.contains(op))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ops.isEmpty() && !operationCodes.isEmpty()) continue;

            List<Long> roleIds = matchedPerms.stream().map(RolePermEntry::roleId).filter(Objects::nonNull).distinct().toList();
            List<Long> permIds = matchedPerms.stream().map(RolePermEntry::permissionId).filter(Objects::nonNull).distinct().toList();
            List<String> sources = matchedPerms.stream().map(RolePermEntry::grantSource).filter(Objects::nonNull).distinct().toList();
            boolean canGrant = matchedPerms.stream().anyMatch(p -> Boolean.TRUE.equals(p.canGrant()));
            entries.add(new QueryResourcesResp.ResourceEntry(
                rtCode, res.getCode(), res.getCodeType(), res.getName(),
                canGrant, false,
                new ArrayList<>(ops), roleIds, permIds, sources));
        }

        return new QueryResourcesResp(entries, permissionVersion, 60);
    }

    /**
     * Expand resource scope by collecting descendants (includeChildren) and ancestors (includeInherited),
     * cloning the original permission entries for newly-included resources.
     */
    private List<RolePermEntry> expandResourceScope(Long tenantId, PermResult r, QueryResourcesReq req,
                                                     Map<Long, ResourceEntity> resMap) {
        // Load all valid resources for parent-child relationship traversal
        List<ResourceEntity> allResources = resourceEntityMapper.selectAllValid(tenantId);
        Map<Long, ResourceEntity> allResMap = allResources.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, re -> re, (a, b) -> a));

        // Build parentId -> childrenIds and id -> parentId maps
        Map<Long, List<Long>> childrenMap = new LinkedHashMap<>();
        Map<Long, Long> parentMap = new LinkedHashMap<>();
        for (ResourceEntity re : allResources) {
            if (re.getParentId() != null) {
                childrenMap.computeIfAbsent(re.getParentId(), k -> new ArrayList<>()).add(re.getId());
                parentMap.put(re.getId(), re.getParentId());
            }
        }

        List<RolePermEntry> expandedEntries = new ArrayList<>();
        for (RolePermEntry entry : r.allEntries()) {
            Long resourceEntityId = entry.resourceEntityId();
            if (resourceEntityId == null) continue; // skip scopeAll entries

            if (Boolean.TRUE.equals(req.includeChildren())) {
                Set<Long> descendants = new LinkedHashSet<>();
                collectDescendants(resourceEntityId, childrenMap, descendants);
                for (Long descendantId : descendants) {
                    ResourceEntity descendantRes = allResMap.get(descendantId);
                    if (descendantRes == null) continue;
                    resMap.putIfAbsent(descendantId, descendantRes);
                    expandedEntries.add(buildExpandedEntry(entry, descendantRes));
                }
            }

            if (Boolean.TRUE.equals(req.includeInherited())) {
                Set<Long> ancestors = new LinkedHashSet<>();
                collectAncestors(resourceEntityId, parentMap, ancestors);
                for (Long ancestorId : ancestors) {
                    ResourceEntity ancestorRes = allResMap.get(ancestorId);
                    if (ancestorRes == null) continue;
                    resMap.putIfAbsent(ancestorId, ancestorRes);
                    expandedEntries.add(buildExpandedEntry(entry, ancestorRes));
                }
            }
        }
        return expandedEntries;
    }

    private void collectDescendants(Long id, Map<Long, List<Long>> childrenMap, Set<Long> result) {
        List<Long> children = childrenMap.getOrDefault(id, List.of());
        for (Long child : children) {
            if (result.add(child)) {
                collectDescendants(child, childrenMap, result);
            }
        }
    }

    private void collectAncestors(Long id, Map<Long, Long> parentMap, Set<Long> result) {
        Long parentId = parentMap.get(id);
        while (parentId != null && result.add(parentId)) {
            parentId = parentMap.get(parentId);
        }
    }

    private RolePermEntry buildExpandedEntry(RolePermEntry source, ResourceEntity res) {
        return new RolePermEntry(
            source.permissionId(), source.roleId(), res.getId(),
            res.getCode(), res.getResourceType(),
            source.grantedBits(), source.operationCode(), source.effectiveBits(),
            "INHERITED", source.canGrant(), source.conditionId(),
            source.hasCondition(), source.dependOn(), false
        );
    }

    // ===== queryScopes =====

    @Override
    @Transactional(readOnly = true)
    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new QueryScopesResp("USER_NOT_FOUND", List.of(), List.of(), List.of(), "", 60);
        }

        Long parentResourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.parentResourceTypeCode(), req.parentResourceCode(), req.parentCodeType(), req.domainCode());
        if (parentResourceEntityId == null) {
            return new QueryScopesResp("OBJECT_KEY_NOT_FOUND", List.of(), List.of(), List.of(), "", 60);
        }

        Map<String, Object> ctx = req.context() != null ? req.context() : Map.of();

        ParentPermissionsResult parentResult = validateParentPermissions(tenantId, userId,
            req, parentResourceEntityId, ctx);
        if (parentResult == null) {
            // 父资源无任何匹配权限 → 整体拒绝，所有 scope 格置 DENIED
            List<ScopeGroup> deniedGroups = buildDeniedGroups(req);
            return new QueryScopesResp("NO_PERMISSION", List.of(), List.of(), deniedGroups, "", 60);
        }

        List<ScopeGroup> scopeGroups = processScopePermissions(tenantId, userId,
            req, parentResult.parentPermissionIds, ctx);

        Set<Long> effectiveRoles = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(userId, tenantId, effectiveRoles);

        return new QueryScopesResp(
            null,
            new ArrayList<>(parentResult.matchedParentOps),
            new ArrayList<>(parentResult.parentPermissionIds),
            scopeGroups,
            permissionVersion,
            60
        );
    }

    /**
     * 构造全 DENIED 分组（父资源鉴权整体拒绝时使用）。
     */
    private List<ScopeGroup> buildDeniedGroups(QueryScopesReq req) {
        List<ScopeGroup> groups = new ArrayList<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            for (String scopeOpCode : req.scopeOperationCodes()) {
                groups.add(new ScopeGroup(
                    scopeTypeCode, scopeOpCode, ScopeMode.DENIED,
                    List.of(), List.of(), List.of(), List.of()
                ));
            }
        }
        return groups;
    }

    private ParentPermissionsResult validateParentPermissions(Long tenantId, Long userId,
                                                               QueryScopesReq req, Long parentResourceEntityId,
                                                               Map<String, Object> ctx) {
        Set<String> matchedParentOps = new HashSet<>();
        Set<Long> parentPermissionIds = new HashSet<>();
        for (String parentOpCode : req.parentOperationCodes()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                req.parentResourceTypeCode(), req.parentResourceCode(), parentOpCode);
            q.setCodeType(req.parentCodeType());
            q.setContext(ctx);
            PermResult r = engine.query(q);
            if (r.allowed()) {
                matchedParentOps.add(parentOpCode);
                parentPermissionIds.addAll(r.matchedPermissionIds());
            }
        }
        if (parentPermissionIds.isEmpty()) return null;
        return new ParentPermissionsResult(matchedParentOps, parentPermissionIds);
    }

    /**
     * 按 (resourceTypeCode, operationCode) 分桶处理数据范围权限。
     * <p>
     * 每格独立判定 {@link ScopeMode}：
     * <ul>
     *   <li>rawEntries 为空（无覆盖该操作的权限）→ {@link ScopeMode#DENIED}</li>
     *   <li>rawEntries 非空但条件/互斥过滤后为空 → {@link ScopeMode#EMPTY}</li>
     *   <li>过滤后含 scopeAll 条目 → {@link ScopeMode#ALL}（items 为空，ALL 覆盖 INSTANCE）</li>
     *   <li>过滤后仅具体实例 → {@link ScopeMode#INSTANCE}（items = 实例集合）</li>
     * </ul>
     * </p>
     */
    private List<ScopeGroup> processScopePermissions(Long tenantId, Long userId,
                                                      QueryScopesReq req,
                                                      Set<Long> parentPermissionIds,
                                                      Map<String, Object> ctx) {
        PermQuery q = PermQuery.forScopeQuery(tenantId, userId,
            new HashSet<>(req.scopeResourceTypeCodes()),
            new HashSet<>(req.scopeOperationCodes()));
        q.setContext(ctx);
        PermResult result = engine.query(q);

        List<RolePermEntry> allEntries = result.allEntries();
        Map<Integer, List<RolePermEntry>> byType = allEntries.stream()
            .filter(e -> e.resourceType() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType));
        Map<Long, ResourceEntity> resourceMap = result.resourceMap();
        Map<Long, OperationPermission> operationMap = result.operationMap() != null ? result.operationMap() : Map.of();
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(operationMap.values());

        Map<String, Integer> scopeTypeValueMap = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", new HashSet<>(req.scopeResourceTypeCodes()));

        List<ScopeGroup> groups = new ArrayList<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            Integer scopeType = scopeTypeValueMap.get(scopeTypeCode);
            if (scopeType == null) {
                // 类型未解析 → 该类型下所有操作 DENIED
                for (String scopeOpCode : req.scopeOperationCodes()) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.DENIED,
                        List.of(), List.of(), List.of(), List.of()));
                }
                continue;
            }
            List<RolePermEntry> typeEntries = byType.getOrDefault(scopeType, List.of());
            Map<String, Long> scopeOpIdMap = typeResolutionService.batchResolveOperationIds(
                tenantId, scopeTypeCode, new HashSet<>(req.scopeOperationCodes()));

            for (String scopeOpCode : req.scopeOperationCodes()) {
                Long scopeOpId = scopeOpIdMap.get(scopeOpCode);
                if (scopeOpId == null) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.DENIED,
                        List.of(), List.of(), List.of(), List.of()));
                    continue;
                }
                groups.add(buildScopeGroup(tenantId, ctx, scopeTypeCode, scopeOpCode, scopeOpId,
                    typeEntries, grantedOpIndex, operationMap, resourceMap, parentPermissionIds));
            }
        }
        return groups;
    }

    private ScopeGroup buildScopeGroup(Long tenantId, Map<String, Object> ctx,
                                        String scopeTypeCode, String scopeOpCode, Long scopeOpId,
                                        List<RolePermEntry> typeEntries,
                                        Map<String, OperationPermission> grantedOpIndex,
                                        Map<Long, OperationPermission> operationMap,
                                        Map<Long, ResourceEntity> resourceMap,
                                        Set<Long> parentPermissionIds) {
        OperationPermission targetOp = operationMap.get(scopeOpId);
        // rawEntries：覆盖目标操作且依赖父权限满足的条目（条件/互斥过滤前）
        List<RolePermEntry> rawEntries = typeEntries.stream()
            .filter(entry -> OperationPermissionUtils.covers(
                OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                    grantedOpIndex, entry.resourceType(), entry.grantedBits()),
                targetOp))
            .filter(entry -> entry.dependOn() == null || parentPermissionIds.contains(entry.dependOn()))
            .toList();

        if (rawEntries.isEmpty()) {
            return new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.DENIED,
                List.of(), List.of(), List.of(), List.of());
        }

        // 条件评估 + 互斥过滤
        List<RolePermEntry> filtered = permissionConditionDomainService.evaluate(tenantId, rawEntries, ctx);
        filtered = permissionConflictDomainService.filterPermMutex(tenantId, filtered);

        Set<Long> matchedRoleIds = new LinkedHashSet<>();
        Set<Long> matchedPermissionIds = new LinkedHashSet<>();
        Set<Long> dependOnPermissionIds = new LinkedHashSet<>();
        for (RolePermEntry e : filtered) {
            if (e.roleId() != null) matchedRoleIds.add(e.roleId());
            if (e.permissionId() != null) matchedPermissionIds.add(e.permissionId());
            if (e.dependOn() != null) dependOnPermissionIds.add(e.dependOn());
        }

        if (filtered.isEmpty()) {
            // 有操作权限但条件/互斥过滤后无数据
            return new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.EMPTY,
                List.of(), List.of(), List.of(), List.of());
        }

        // ALL 优先：任一 scopeAll 条目存在 → 全量授权
        boolean hasScopeAll = filtered.stream().anyMatch(e -> e.resourceEntityId() == null);
        if (hasScopeAll) {
            return new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.ALL,
                List.of(),
                new ArrayList<>(matchedRoleIds),
                new ArrayList<>(matchedPermissionIds),
                new ArrayList<>(dependOnPermissionIds));
        }

        // INSTANCE：收集有效具体实例（去重，跳过已删除资源）
        Map<String, QueryScopesResp.ScopeItem> items = new LinkedHashMap<>();
        for (RolePermEntry entry : filtered) {
            if (entry.resourceEntityId() == null) continue;
            ResourceEntity resource = resourceMap.get(entry.resourceEntityId());
            if (resource == null || resource.getDeleteFlag() != 0L) continue;
            String itemKey = resource.getCodeType() + "|" + resource.getCode();
            items.putIfAbsent(itemKey, new QueryScopesResp.ScopeItem(
                resource.getCode(), resource.getCodeType(), resource.getName()));
        }
        return new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.INSTANCE,
            new ArrayList<>(items.values()),
            new ArrayList<>(matchedRoleIds),
            new ArrayList<>(matchedPermissionIds),
            new ArrayList<>(dependOnPermissionIds));
    }

    private static class ParentPermissionsResult {
        final Set<String> matchedParentOps;
        final Set<Long> parentPermissionIds;
        ParentPermissionsResult(Set<String> matchedParentOps, Set<Long> parentPermissionIds) {
            this.matchedParentOps = matchedParentOps;
            this.parentPermissionIds = parentPermissionIds;
        }
    }

    // ===== interfaceSnapshot =====

    @Override
    @Transactional(readOnly = true)
    public InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new InterfaceSnapshotResp(false, "", List.of());

        Set<Long> effectiveRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        Set<Long> validRoleIds = effectiveRoleIds.isEmpty()
            ? Set.of()
            : permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        String permissionVersion = buildInterfacePermissionVersion(tenantId, validRoleIds);

        if (permissionVersion.equals(req.permissionVersion())) {
            return new InterfaceSnapshotResp(true, permissionVersion, List.of());
        }

        String cacheIdentifier = buildInterfaceSnapshotCacheIdentifier(req.serviceCode(), permissionVersion);
        InterfaceSnapshot cached = cacheService.get(PermCacheCatalog.INTERFACE_SNAPSHOT, tenantId, cacheIdentifier);
        if (cached != null) {
            return new InterfaceSnapshotResp(false, permissionVersion, toApiPermissionEntries(cached.entries()));
        }

        if (validRoleIds.isEmpty()) {
            cacheInterfaceSnapshot(tenantId, req.serviceCode(), permissionVersion, List.of(), cacheIdentifier);
            return new InterfaceSnapshotResp(false, permissionVersion, List.of());
        }

        // 调引擎获取全量权限，通过 SnapshotAssembler 过滤 API 类型并构建快照条目
        PermQuery query = PermQuery.forUserView(tenantId, userId);
        query.setRoleIds(validRoleIds); // 使用已过滤互斥的角色
        PermResult result = engine.query(query);
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "API");
        List<ApiPermissionEntry> entries = snapshotAssembler.buildSnapshot(tenantId, result, req.serviceCode(), apiType);

        List<ApiPermissionEntry> dedupedEntries = entries.stream()
            .collect(Collectors.toMap(
                item -> item.serviceCode() + "|" + item.httpMethod() + "|" + item.pathPattern(),
                item -> item,
                (left, right) -> left.hasCondition() ? left : right,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();

        cacheInterfaceSnapshot(tenantId, req.serviceCode(), permissionVersion, dedupedEntries, cacheIdentifier);
        return new InterfaceSnapshotResp(false, permissionVersion, dedupedEntries);
    }

    private String buildInterfacePermissionVersion(Long tenantId, Set<Long> validRoleIds) {
        // T-PERM-003（2026-06-20）：删除 permission_version 持久化层后，令牌不再包含 versionNo。
        // 占位实现：基于有序 roleIds 的 sha256 指纹。令牌仅在角色集合变化时变化；
        // 权限内容失效改由 Redis pub/sub 广播（T-PERM-006）+ TTL 兜底承担。
        // T-PERM-001 落地后改为 sha256(permissions)（v3.5 §5.1 ETag）。
        StringBuilder raw = new StringBuilder("perm:v2|");
        List<Long> sortedRoleIds = validRoleIds.stream()
            .sorted()
            .toList();

        if (sortedRoleIds.isEmpty()) {
            raw.append("empty");
        } else {
            for (Long roleId : sortedRoleIds) {
                raw.append(roleId).append(";");
            }
        }

        return "perm:v2:" + sha256Hex(raw.toString());
    }

    private String buildInterfaceSnapshotCacheIdentifier(String serviceCode, String permissionVersion) {
        return serviceCode + "|" + permissionVersion;
    }

    private void cacheInterfaceSnapshot(Long tenantId, String serviceCode, String permissionVersion,
                                         List<ApiPermissionEntry> entries, String cacheIdentifier) {
        InterfaceSnapshot snapshot = new InterfaceSnapshot(
            tenantId,
            serviceCode,
            permissionVersion,
            entries.stream()
                .map(item -> new InterfaceSnapshot.InterfacePermEntry(
                    item.serviceCode(), item.httpMethod(), item.pathPattern(), item.hasCondition(), item.conditionId(), item.scopeAll()
                ))
                .toList()
        );
        cacheService.put(PermCacheCatalog.INTERFACE_SNAPSHOT, tenantId, cacheIdentifier, snapshot);
    }

    private List<ApiPermissionEntry> toApiPermissionEntries(List<InterfaceSnapshot.InterfacePermEntry> entries) {
        return entries.stream()
            .map(e -> new ApiPermissionEntry(e.serviceCode(), e.httpMethod(), e.pathPattern(),
                e.hasCondition(), e.conditionId(), e.scopeAll()))
            .collect(Collectors.toList());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new SystemException(PermissionErrorCode.SYSTEM_INIT_FAILED.getCode(), "SHA-256 algorithm is not available", e);
        }
    }

    // ===== queryPermissionTree =====

    @Override
    @Transactional(readOnly = true)
    public PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req) {
        TreeContext context = prepareTreeContext(tenantId, req);
        if (context.userId == null) {
            return new PermissionTreeResp(null, List.of(), List.of(), null, 60);
        }
        if (context.rootResourceId == null) {
            return new PermissionTreeResp(null, List.of(), List.of(), null, 60);
        }
        if (context.validRoleIds.isEmpty()) {
            ResourceEntity rootResource = resourceEntityMapper.selectOneById(context.rootResourceId);
            return new PermissionTreeResp(buildNode(context.rootResourceId, 0, Set.of(), false, null, rootResource, Map.of()),
                List.of(), List.of(), null, 60);
        }

        Map<Long, List<RolePermEntry>> permissionMap = buildPermissionMap(tenantId, context);
        return buildPermissionTreeResponse(tenantId, context, permissionMap);
    }

    private static class TreeContext {
        final Long userId;
        final Long rootResourceId;
        final Set<Long> validRoleIds;
        final Set<Long> operationIds;
        final Map<Long, OperationPermission> operationMap;
        final int maxDepth;
        final String direction;
        final Map<String, Object> context;

        TreeContext(Long userId, Long rootResourceId, Set<Long> validRoleIds,
                    Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                    int maxDepth, String direction, Map<String, Object> context) {
            this.userId = userId;
            this.rootResourceId = rootResourceId;
            this.validRoleIds = validRoleIds;
            this.operationIds = operationIds;
            this.operationMap = operationMap;
            this.maxDepth = maxDepth;
            this.direction = direction;
            this.context = context;
        }
    }

    private TreeContext prepareTreeContext(Long tenantId, PermissionTreeReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new TreeContext(null, null, Set.of(), Set.of(), Map.of(), 10, "BOTH", Map.of());
        }

        Long rootResourceId = typeResolutionService.resolveResourceId(
            tenantId, req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.domainCode()
        );
        if (rootResourceId == null) {
            return new TreeContext(userId, null, Set.of(), Set.of(), Map.of(), 10, "BOTH", Map.of());
        }

        Set<Long> effectiveRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        if (effectiveRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, Set.of(), Set.of(), Map.of(), 10, "BOTH", Map.of());
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, Set.of(), Set.of(), Map.of(), 10, "BOTH", Map.of());
        }

        Set<Long> operationIds = resolveOperationIds(tenantId, req);

        Map<Long, OperationPermission> operationMap = operationIds.isEmpty()
            ? Collections.emptyMap()
            : operationPermissionMapper.selectValidByIds(tenantId, operationIds)
                .stream().collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));

        int maxDepth = req.maxDepth() != null ? req.maxDepth() : 10;
        String direction = req.direction() != null ? req.direction().toUpperCase() : "BOTH";

        return new TreeContext(userId, rootResourceId, validRoleIds, operationIds, operationMap, maxDepth, direction, req.context());
    }

    private Set<Long> resolveOperationIds(Long tenantId, PermissionTreeReq req) {
        Set<String> opCodes = new HashSet<>(req.operationCodes());
        Map<String, Long> opIdMap = typeResolutionService.batchResolveOperationIds(tenantId, req.resourceTypeCode(), opCodes);
        return new HashSet<>(opIdMap.values());
    }

    private Map<Long, List<RolePermEntry>> buildPermissionMap(Long tenantId, TreeContext context) {
        PermQuery query = PermQuery.forUserView(tenantId, context.userId);
        query.setRoleIds(context.validRoleIds); // 使用已过滤互斥的角色
        if (context.context != null) {
            query.setContext(context.context);
        }
        PermResult result = engine.query(query);
        return result.allEntries().stream()
            .filter(e -> e.resourceEntityId() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceEntityId));
    }

    private PermissionTreeResp buildPermissionTreeResponse(Long tenantId, TreeContext context,
                                                            Map<Long, List<RolePermEntry>> permsByResource) {
        List<ResourceEntity> allResources = resourceEntityMapper.selectAllValid(tenantId);
        Map<Long, ResourceEntity> allResourceMap = allResources.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        Set<Integer> allResourceTypes = allResources.stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", allResourceTypes);

        ResourceEntity rootResource = allResourceMap.get(context.rootResourceId);
        TreeNode root = buildNode(context.rootResourceId, 0,
            getOperationsForResource(permsByResource.get(context.rootResourceId), context.operationIds, context.operationMap),
            hasCanGrant(permsByResource.get(context.rootResourceId), context.operationIds, context.operationMap),
            rootResource != null ? rootResource.getName() : null,
            rootResource, resourceTypeCodeMap);

        List<TreeNode> ancestors = List.of();
        List<TreeNode> descendants = List.of();

        if ("ANCESTORS".equals(context.direction) || "BOTH".equals(context.direction)) {
            ancestors = traverseAncestors(tenantId, context.rootResourceId, permsByResource,
                context.operationIds, context.operationMap, context.maxDepth, allResourceMap, resourceTypeCodeMap);
        }
        if ("DESCENDANTS".equals(context.direction) || "BOTH".equals(context.direction)) {
            descendants = traverseDescendants(tenantId, context.rootResourceId, permsByResource,
                context.operationIds, context.operationMap, context.maxDepth, allResourceMap, resourceTypeCodeMap);
        }

        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(context.userId, tenantId, context.validRoleIds);

        return new PermissionTreeResp(root, ancestors, descendants, permissionVersion, 60);
    }

    private TreeNode buildNode(Long resourceId, int depth,
                               Set<String> operations, boolean canGrant, String name,
                               ResourceEntity resource, Map<Integer, String> resourceTypeCodeMap) {
        if (resource == null) {
            return new TreeNode(resourceId, null, null, name, depth, operations, canGrant, null);
        }
        String typeCode = resourceTypeCodeMap.get(resource.getResourceType());
        return new TreeNode(resourceId, typeCode, resource.getCode(), resource.getName(), depth, operations, canGrant, null);
    }

    private Set<String> getOperationsForResource(List<RolePermEntry> perms, Set<Long> operationIds,
                                                  Map<Long, OperationPermission> operationMap) {
        if (perms == null || perms.isEmpty()) return Set.of();
        Set<OperationPermission> targetOps = operationIds.stream()
            .map(operationMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(operationMap.values());
        Set<String> operations = new LinkedHashSet<>();
        for (RolePermEntry perm : perms) {
            OperationPermission grantedOp = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                grantedOpIndex,
                perm.resourceType(),
                perm.grantedBits()
            );
            if (grantedOp == null) {
                continue;
            }
            for (OperationPermission targetOp : targetOps) {
                if (OperationPermissionUtils.covers(grantedOp, targetOp)) {
                    operations.add(targetOp.getCode());
                }
            }
        }
        return operations;
    }

    private boolean hasCanGrant(List<RolePermEntry> perms, Set<Long> operationIds,
                                 Map<Long, OperationPermission> operationMap) {
        if (perms == null || perms.isEmpty()) return false;
        Set<OperationPermission> targetOps = operationIds.stream()
            .map(operationMap::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(operationMap.values());
        for (RolePermEntry perm : perms) {
            if (!Boolean.TRUE.equals(perm.canGrant())) {
                continue;
            }
            OperationPermission grantedOp = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                grantedOpIndex,
                perm.resourceType(),
                perm.grantedBits()
            );
            if (grantedOp == null) {
                continue;
            }
            for (OperationPermission targetOp : targetOps) {
                if (OperationPermissionUtils.covers(grantedOp, targetOp)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<TreeNode> traverseAncestors(Long tenantId, Long startResourceId,
                                              Map<Long, List<RolePermEntry>> permsByResource,
                                              Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                              int maxDepth, Map<Long, ResourceEntity> allResourceMap,
                                              Map<Integer, String> resourceTypeCodeMap) {
        List<TreeNode> ancestors = new ArrayList<>();
        Long currentId = startResourceId;
        int depth = -1;

        while (currentId != null && Math.abs(depth) <= maxDepth) {
            ResourceEntity resource = allResourceMap.get(currentId);
            if (resource == null || resource.getDeleteFlag() != 0L || !resource.getTenantId().equals(tenantId)) {
                break;
            }
            if (resource.getParentId() != null) {
                List<RolePermEntry> perms = permsByResource.get(resource.getParentId());
                Set<String> ops = getOperationsForResource(perms, operationIds, operationMap);
                if (!ops.isEmpty()) {
                    ResourceEntity parentResource = allResourceMap.get(resource.getParentId());
                    TreeNode node = buildNode(resource.getParentId(), depth, ops,
                        hasCanGrant(perms, operationIds, operationMap), null, parentResource, resourceTypeCodeMap);
                    ancestors.add(node);
                }
                currentId = resource.getParentId();
                depth--;
            } else {
                break;
            }
        }
        return ancestors;
    }

    private List<TreeNode> traverseDescendants(Long tenantId, Long startResourceId,
                                                Map<Long, List<RolePermEntry>> permsByResource,
                                                Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                int maxDepth, Map<Long, ResourceEntity> allResourceMap,
                                                Map<Integer, String> resourceTypeCodeMap) {
        List<TreeNode> descendants = new ArrayList<>();
        collectDescendantsWithPermission(tenantId, startResourceId, permsByResource, operationIds, operationMap,
            1, maxDepth, descendants, allResourceMap, resourceTypeCodeMap);
        return descendants;
    }

    private void collectDescendantsWithPermission(Long tenantId, Long parentId,
                                                   Map<Long, List<RolePermEntry>> permsByResource,
                                                   Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                   int currentDepth, int maxDepth,
                                                   List<TreeNode> result,
                                                   Map<Long, ResourceEntity> allResourceMap,
                                                   Map<Integer, String> resourceTypeCodeMap) {
        if (currentDepth > maxDepth) return;

        List<ResourceEntity> children = allResourceMap.values().stream()
            .filter(r -> Objects.equals(r.getParentId(), parentId) && r.getDeleteFlag() == 0L && r.getTenantId().equals(tenantId))
            .collect(Collectors.toList());

        for (ResourceEntity child : children) {
            List<RolePermEntry> perms = permsByResource.get(child.getId());
            Set<String> ops = getOperationsForResource(perms, operationIds, operationMap);
            if (!ops.isEmpty()) {
                TreeNode node = buildNode(child.getId(), currentDepth, ops,
                    hasCanGrant(perms, operationIds, operationMap), child.getName(), child, resourceTypeCodeMap);
                result.add(node);
            }
            collectDescendantsWithPermission(tenantId, child.getId(), permsByResource, operationIds, operationMap,
                currentDepth + 1, maxDepth, result, allResourceMap, resourceTypeCodeMap);
        }
    }

}
