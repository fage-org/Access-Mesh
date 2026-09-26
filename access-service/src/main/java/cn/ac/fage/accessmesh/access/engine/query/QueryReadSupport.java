package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper.BitMaskEntry;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 新执行器的批量读取边界（T-PERM-084，设计 §4.2/§5.2）。
 * 无单例运行态、独立事务或旧引擎调用；返回原始事实，不能缓存某项过滤后的候选。
 * 暂不注册 Bean，随新执行器消费者迁移装配。所有记忆存放在 RunState 内。
 */
final class QueryReadSupport {
    private final TypeResolutionService types;
    private final OperationPermissionDomainService operations;
    private final ResourceEntityDomainService resources;
    private final AbstractRoleMapper roles;
    private final RoleResourcePermissionMapper grants;
    private final CacheService cache;
    private final RolePermEntryMapper entries;

    QueryReadSupport(TypeResolutionService types, OperationPermissionDomainService operations,
                     ResourceEntityDomainService resources, AbstractRoleMapper roles,
                     RoleResourcePermissionMapper grants, CacheService cache, RolePermEntryMapper entries) {
        this.types = types;
        this.operations = operations;
        this.resources = resources;
        this.roles = roles;
        this.grants = grants;
        this.cache = cache;
        this.entries = entries;
    }

    /** 完整数据库类型目录；空桶也标记完整，按 ID 读取绝不写这个完整性标记。 */
    Map<Integer, List<OperationDefinition>> freshOperations(RunState run, Set<Integer> requestedTypes) {
        Memory memory = run.readMemory();
        Set<Integer> requested = nonNull(requestedTypes);
        Set<Integer> missing = missing(requested, memory.freshByType);
        if (!missing.isEmpty()) {
            List<OperationPermission> rows = loadOperationRows(run, missing);
            // 全批成功后再发布完整性标记；异常不伪装成空目录。
            Map<Integer, Map<Long, OperationDefinition>> loaded = new LinkedHashMap<>();
            missing.forEach(type -> loaded.put(type, new LinkedHashMap<>()));
            for (OperationPermission row : rows) {
                if (loaded.containsKey(row.getResourceType())) {
                    rememberDefinition(memory, row);
                }
            }
            // 同来源首次读取后复用，包括此前按 ID 已读到的定义；不拼出不同版本的同一行。
            for (Optional<OperationDefinition> value : memory.freshDefinitionIndex.values()) {
                value.ifPresent(definition -> {
                    Map<Long, OperationDefinition> bucket = loaded.get(definition.resourceType());
                    if (bucket != null) bucket.putIfAbsent(definition.id(), definition);
                });
            }
            loaded.forEach((type, values) -> memory.freshByType.put(type, List.copyOf(values.values())));
        }
        return subset(memory.freshByType, requested);
    }

    Map<Long, OperationDefinition> freshOperationsByIds(RunState run, Set<Long> ids) {
        Memory memory = run.readMemory();
        Set<Long> requested = nonNull(ids);
        Set<Long> missing = missing(requested, memory.freshDefinitionIndex);
        if (!missing.isEmpty()) {
            List<OperationPermission> rows = new ArrayList<>();
            SqlBatches.forEach(List.copyOf(missing), batch -> rows.addAll(
                operations.selectValidByIds(run.request().tenantId(), new LinkedHashSet<>(batch))));
            rows.forEach(row -> rememberDefinition(memory, row));
            missing.forEach(id -> memory.freshDefinitionIndex.putIfAbsent(id, Optional.empty()));
        }
        return present(memory.freshDefinitionIndex, requested);
    }

    private Optional<OperationDefinition> rememberDefinition(Memory memory, OperationPermission row) {
        memory.freshDefinitionIndex.putIfAbsent(row.getId(), Optional.of(OperationDefinition.from(row)));
        return memory.freshDefinitionIndex.get(row.getId());
    }

    private List<OperationPermission> loadOperationRows(RunState run, Set<Integer> requestedTypes) {
        List<OperationPermission> rows = new ArrayList<>();
        SqlBatches.forEach(List.copyOf(requestedTypes), batch -> rows.addAll(
            operations.selectByTenantAndResourceTypes(run.request().tenantId(), new LinkedHashSet<>(batch))));
        return rows;
    }

    /** 普通判定的长 TTL 掩码目录，与 freshDefinitionIndex 单向隔离：缓存命中不填新鲜桶。 */
    Map<Integer, List<OperationDefinition>> maskOperations(RunState run, Set<Integer> requestedTypes) {
        Memory memory = run.readMemory();
        Set<Integer> requested = nonNull(requestedTypes);
        Set<Integer> unloaded = missing(requested, memory.maskByType);
        if (!unloaded.isEmpty()) {
            Set<String> keys = unloaded.stream().map(AccessCacheCatalog::operationPermissionsByTypeKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, Map<Long, OperationPermission>> cached = cache.getBatch(
                AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, run.request().tenantId(), keys);
            Set<Integer> misses = new LinkedHashSet<>();
            for (Integer type : unloaded) {
                Map<Long, OperationPermission> hit = cached.get(AccessCacheCatalog.operationPermissionsByTypeKey(type));
                if (hit == null) {
                    misses.add(type);
                } else {
                    memory.maskByType.put(type, hit.values().stream().map(OperationDefinition::from).toList());
                }
            }
            if (!misses.isEmpty()) {
                // 共享缓存回填必须使用本次数据库读取，不能发布 RunState 的旧值/负记忆。
                List<OperationPermission> rows = loadOperationRows(run, misses);
                Map<Integer, List<OperationDefinition>> loaded = new LinkedHashMap<>();
                misses.forEach(type -> loaded.put(type, new ArrayList<>()));
                rows.forEach(row -> loaded.get(row.getResourceType()).add(OperationDefinition.from(row)));
                Map<String, Map<Long, OperationPermission>> refill = new LinkedHashMap<>();
                loaded.forEach((type, definitions) -> {
                    memory.maskByType.put(type, List.copyOf(definitions));
                    Map<Long, OperationPermission> byId = new LinkedHashMap<>();
                    definitions.forEach(definition -> byId.put(definition.id(), definition.toCacheRow()));
                    refill.put(AccessCacheCatalog.operationPermissionsByTypeKey(type), byId);
                });
                // 普通操作目录保持原 mode/TTL；授权快照令牌只用于对应 L2 授权目录。
                cache.putBatch(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, run.request().tenantId(), refill);
            }
        }
        return subset(memory.maskByType, requested);
    }

    Map<String, Integer> resolveTypes(RunState run, Set<String> codes) {
        Memory memory = run.readMemory();
        Set<String> requested = nonNull(codes);
        Set<String> missing = missing(requested, memory.typeValues);
        if (!missing.isEmpty()) {
            Map<String, Integer> loaded = types.batchResolveTypeValues(run.request().tenantId(), "resource_type", missing);
            missing.forEach(code -> memory.typeValues.put(code, Optional.ofNullable(loaded.get(code))));
        }
        return present(memory.typeValues, requested);
    }

    /** 保留类型—操作配对；仅复用数据库定义，不能从长 TTL 掩码桶反向认定操作存在。 */
    Map<TypeOperation, OperationDefinition> resolveOperations(RunState run, Set<TypeOperation> keys) {
        Memory memory = run.readMemory();
        Set<TypeOperation> requested = nonNull(keys);
        Set<TypeOperation> missing = missing(requested, memory.operationKeys);
        if (!missing.isEmpty()) {
            Set<String> codes = missing.stream().map(TypeOperation::resourceTypeCode).collect(Collectors.toSet());
            Map<String, Integer> resolved = resolveTypes(run, codes);
            Map<Integer, List<OperationDefinition>> definitions = freshOperations(run, new LinkedHashSet<>(resolved.values()));
            for (TypeOperation key : missing) {
                List<OperationDefinition> bucket = definitions.getOrDefault(resolved.get(key.resourceTypeCode()), List.of());
                memory.operationKeys.put(key, bucket.stream().filter(op -> op.code().equals(key.operationCode())).findFirst());
            }
        }
        return present(memory.operationKeys, requested);
    }

    Map<Integer, List<OperationDefinition>> outputOperations(RunState run, OutputSpec output, Set<Integer> requestedTypes) {
        run.readMemory();
        if (!output.descriptions() && !output.effectiveOperations()) return Map.of();
        Set<Integer> allTypes = new LinkedHashSet<>(nonNull(requestedTypes));
        Set<String> extraTypes = output.extraOperationKeys().stream().map(TypeOperation::resourceTypeCode)
            .collect(Collectors.toSet());
        allTypes.addAll(resolveTypes(run, extraTypes).values());
        return freshOperations(run, allTypes);
    }

    Map<ResourceResolveKey, Long> resolveResources(RunState run, List<ResourceResolveRequest> requests) {
        Memory memory = run.readMemory();
        Set<ResourceResolveKey> unique = requests.stream().map(ResourceResolveRequest::toKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<ResourceResolveKey> missing = missing(unique, memory.resourceIds);
        if (!missing.isEmpty()) {
            Map<String, Integer> typeValues = resolveTypes(run, missing.stream()
                .map(ResourceResolveKey::resourceTypeCode).collect(Collectors.toSet()));
            List<ResourceResolveKey> resolvable = missing.stream().filter(key -> typeValues.containsKey(key.resourceTypeCode())).toList();
            Map<ResourceIdentity, Long> loaded = new LinkedHashMap<>();
            SqlBatches.forEach(resolvable, batch -> {
                Set<Integer> resourceTypes = batch.stream().map(key -> typeValues.get(key.resourceTypeCode())).collect(Collectors.toSet());
                Set<String> codes = batch.stream().map(ResourceResolveKey::resourceCode).collect(Collectors.toSet());
                Set<String> codeTypes = batch.stream().map(key -> defaultCodeType(key.codeType())).collect(Collectors.toSet());
                resources.selectByTypesAndCodesAndCodeTypes(run.request().tenantId(), resourceTypes, codes, codeTypes)
                    .forEach(row -> loaded.put(new ResourceIdentity(row.getResourceType(),
                        row.getCode(), defaultCodeType(row.getCodeType())), row.getId()));
            });
            // SQL 可装载笛卡尔超集，按原始完整配对取回；域分类不进入鉴权查询管线。
            missing.forEach(key -> {
                Integer type = typeValues.get(key.resourceTypeCode());
                Long id = type == null ? null : loaded.get(new ResourceIdentity(type,
                    key.resourceCode(), defaultCodeType(key.codeType())));
                memory.resourceIds.put(key, Optional.ofNullable(id));
            });
        }
        return present(memory.resourceIds, unique);
    }

    private static String defaultCodeType(String codeType) {
        return codeType == null || codeType.isBlank() ? PermConstants.CodeType.DEFAULT : codeType;
    }

    /** 描述读取入口独立于判定；最小输出在解析目标前短路。 */
    Map<Long, ResourceEntity> resourceDescriptions(RunState run, OutputSpec output, Set<Long> ids) {
        Memory memory = run.readMemory();
        if (!output.descriptions()) return Map.of();
        Set<Long> requested = nonNull(ids);
        Set<Long> missing = missing(requested, memory.resourceDescriptions);
        if (!missing.isEmpty()) {
            List<ResourceEntity> loaded = new ArrayList<>();
            SqlBatches.forEach(List.copyOf(missing), batch -> loaded.addAll(
                resources.selectValidByIds(run.request().tenantId(), new LinkedHashSet<>(batch))));
            rememberRows(memory.resourceDescriptions, missing, loaded, ResourceEntity::getId);
        }
        return present(memory.resourceDescriptions, requested);
    }

    Map<Long, AbstractRole> roleDescriptions(RunState run, OutputSpec output, Set<Long> ids) {
        Memory memory = run.readMemory();
        if (!output.descriptions()) return Map.of();
        Set<Long> requested = nonNull(ids);
        Set<Long> missing = missing(requested, memory.roleDescriptions);
        if (!missing.isEmpty()) {
            List<AbstractRole> loaded = new ArrayList<>();
            SqlBatches.forEach(List.copyOf(missing), batch -> loaded.addAll(
                roles.selectValidByIds(run.request().tenantId(), new LinkedHashSet<>(batch))));
            rememberRows(memory.roleDescriptions, missing, loaded, AbstractRole::getId);
        }
        return present(memory.roleDescriptions, requested);
    }

    List<GrantFact> listGrants(RunState run, Set<Long> roleIds) {
        Memory memory = run.readMemory();
        Set<Long> requested = nonNull(roleIds);
        Set<Long> unloaded = missing(requested, memory.listByRole);
        if (!unloaded.isEmpty()) {
            if (run.request().reads().listGrantRead() == ListGrantRead.DATABASE) {
                List<RoleResourcePermission> loaded = loadRoleRows(run, unloaded);
                Map<Long, List<GrantFact>> buckets = new LinkedHashMap<>();
                unloaded.forEach(role -> buckets.put(role, new ArrayList<>()));
                loaded.forEach(row -> buckets.get(row.getAbstractRoleId()).add(databaseFact(memory, row)));
                buckets.forEach((role, facts) -> memory.listByRole.put(role, List.copyOf(facts)));
            } else {
                readRoleSnapshots(run, unloaded, memory);
            }
        }
        return requested.stream().flatMap(role -> memory.listByRole.get(role).stream()).toList();
    }

    private List<RoleResourcePermission> loadRoleRows(RunState run, Set<Long> roleIds) {
        List<RoleResourcePermission> loaded = new ArrayList<>();
        SqlBatches.forEach(List.copyOf(roleIds), batch -> loaded.addAll(
            grants.selectValidByRoleIds(run.request().tenantId(), new LinkedHashSet<>(batch))));
        return loaded;
    }

    private void readRoleSnapshots(RunState run, Set<Long> unloaded, Memory memory) {
        Map<Long, List<RolePermEntry>> cached = cache.getBatch(
            AccessCacheCatalog.ROLE_PERM_SNAPSHOT, run.request().tenantId(), unloaded);
        Set<Long> misses = new LinkedHashSet<>();
        unloaded.forEach(role -> {
            List<RolePermEntry> hit = cached.get(role);
            if (hit == null) misses.add(role);
            else memory.listByRole.put(role, hit.stream().map(GrantFact::from).toList());
        });
        if (misses.isEmpty()) return;
        if (memory.snapshotToken == null) {
            memory.snapshotToken = cache.beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        }
        List<RoleResourcePermission> loaded = loadRoleRows(run, misses);
        Map<Long, List<RolePermEntry>> refill = new LinkedHashMap<>();
        misses.forEach(role -> refill.put(role, new ArrayList<>()));
        loaded.forEach(row -> refill.get(row.getAbstractRoleId()).add(entries.toEntry(row)));
        refill.replaceAll((role, values) -> List.copyOf(values));
        cache.putBatch(memory.snapshotToken, run.request().tenantId(), refill);
        refill.forEach((role, values) -> memory.listByRole.put(role, values.stream().map(GrantFact::from).toList()));
    }

    List<GrantFact> scopeGrants(RunState run, Set<Long> roleIds, Map<Integer, Long> masks) {
        return targetGrants(run, roleIds, Set.of(), masks, true);
    }

    List<GrantFact> instanceGrants(RunState run, Set<Long> roleIds, Set<Long> entityIds, Map<Integer, Long> masks) {
        return targetGrants(run, roleIds, entityIds, masks, false);
    }

    private List<GrantFact> targetGrants(RunState run, Set<Long> roleIds, Set<Long> entityIds,
                                       Map<Integer, Long> masks, boolean scopeAll) {
        Memory memory = run.readMemory();
        Set<Long> roleSet = nonNull(roleIds);
        Set<Long> entitySet = nonNull(entityIds);
        Map<Integer, Long> validMasks = new LinkedHashMap<>();
        masks.forEach((type, mask) -> {
            if (type != null && mask != null && mask != 0L) validMasks.put(type, mask);
        });
        if (roleSet.isEmpty() || validMasks.isEmpty() || (!scopeAll && entitySet.isEmpty())) return List.of();
        GrantLoad key = new GrantLoad(scopeAll, Set.copyOf(roleSet), Set.copyOf(entitySet), Map.copyOf(validMasks));
        if (!memory.targetLoads.containsKey(key)) {
            List<RoleResourcePermission> loaded = new ArrayList<>();
            List<BitMaskEntry> bits = validMasks.entrySet().stream().map(e -> new BitMaskEntry(e.getKey(), e.getValue())).toList();
            SqlBatches.forEach(List.copyOf(roleSet), roleBatch -> SqlBatches.forEach(bits, maskBatch -> {
                Set<Long> batchRoles = new LinkedHashSet<>(roleBatch);
                if (scopeAll) {
                    loaded.addAll(grants.selectScopeAllPermsByBitsBatch(run.request().tenantId(), batchRoles, maskBatch));
                } else {
                    SqlBatches.forEach(List.copyOf(entitySet), entityBatch -> loaded.addAll(grants.selectInstancePermsByBitsBatch(
                        run.request().tenantId(), batchRoles, new LinkedHashSet<>(entityBatch), maskBatch)));
                }
            }));
            // 块只负责装载，候选全部就绪后交 Selector/Evaluator，不在块内评估或短路。
            Map<Long, GrantFact> facts = new LinkedHashMap<>();
            loaded.forEach(row -> facts.putIfAbsent(row.getId(), databaseFact(memory, row)));
            memory.targetLoads.put(key, List.copyOf(facts.values()));
        }
        return memory.targetLoads.get(key);
    }

    private GrantFact databaseFact(Memory memory, RoleResourcePermission row) {
        return memory.databaseFacts.computeIfAbsent(row.getId(), ignored -> GrantFact.from(entries.toEntry(row)));
    }

    private static <K, V> void rememberRows(Map<K, Optional<V>> memory, Set<K> requested,
                                           List<V> rows, Function<V, K> key) {
        rows.forEach(row -> memory.putIfAbsent(key.apply(row), Optional.of(row)));
        requested.forEach(id -> memory.putIfAbsent(id, Optional.empty()));
    }

    private static <K> Set<K> nonNull(Set<K> keys) {
        if (keys == null) return Set.of();
        return keys.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static <K> Set<K> missing(Set<K> requested, Map<K, ?> memory) {
        Set<K> result = new LinkedHashSet<>(requested);
        result.removeAll(memory.keySet());
        return result;
    }

    private static <K, V> Map<K, V> subset(Map<K, V> memory, Set<K> requested) {
        Map<K, V> result = new LinkedHashMap<>();
        requested.forEach(key -> result.put(key, memory.get(key)));
        return Collections.unmodifiableMap(result);
    }

    private static <K, V> Map<K, V> present(Map<K, Optional<V>> memory, Set<K> requested) {
        Map<K, V> result = new LinkedHashMap<>();
        requested.forEach(key -> memory.get(key).ifPresent(value -> result.put(key, value)));
        return Collections.unmodifiableMap(result);
    }

    /** key 不存在=UNLOADED；Optional.empty/空集合=LOADED_EMPTY；其余=LOADED_VALUE。 */
    static final class Memory {
        final Map<String, Optional<Integer>> typeValues = new LinkedHashMap<>();
        final Map<Long, Optional<OperationDefinition>> freshDefinitionIndex = new LinkedHashMap<>();
        final Map<Integer, List<OperationDefinition>> freshByType = new LinkedHashMap<>();
        final Map<Integer, List<OperationDefinition>> maskByType = new LinkedHashMap<>();
        final Map<TypeOperation, Optional<OperationDefinition>> operationKeys = new LinkedHashMap<>();
        final Map<ResourceResolveKey, Optional<Long>> resourceIds = new LinkedHashMap<>();
        final Map<Long, Optional<ResourceEntity>> resourceDescriptions = new LinkedHashMap<>();
        final Map<Long, Optional<AbstractRole>> roleDescriptions = new LinkedHashMap<>();
        final Map<Long, List<GrantFact>> listByRole = new LinkedHashMap<>();
        final Map<Long, GrantFact> databaseFacts = new LinkedHashMap<>();
        final Map<GrantLoad, List<GrantFact>> targetLoads = new LinkedHashMap<>();
        CacheReadToken<List<RolePermEntry>> snapshotToken;
    }

    private record GrantLoad(boolean scopeAll, Set<Long> roles, Set<Long> entities, Map<Integer, Long> masks) {}

    /** 内存精确匹配用字段元组；code/codeType 可含冒号，不能用跨层编码字符串作身份键。 */
    private record ResourceIdentity(Integer resourceType, String code, String codeType) {}
}
