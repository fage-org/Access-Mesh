package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 范围四态的纯投影：只消费完整 raw/kept 结果与描述，不装载、不重新鉴权。 */
public final class ScopeCoverageProjector {
    private ScopeCoverageProjector() {}

    public static List<QueryScopesResp.ScopeGroup> project(GrantSetResult result, List<TypeOperation> requirements) {
        ResultDetails details = result.details();
        if (!details.loadedSections().containsAll(List.of(ResultDetails.DetailSection.FACTS_RAW,
            ResultDetails.DetailSection.FACTS_KEPT, ResultDetails.DetailSection.DESCRIPTIONS))) {
            throw new IllegalArgumentException("范围投影要求 RAW_AND_KEPT 和描述块");
        }
        boolean denied = result.collectionStatus() == GrantSetResult.CollectionStatus.NO_ROLE
            || result.collectionStatus() == GrantSetResult.CollectionStatus.PARENT_DENIED;
        if (!denied && (!result.coverage().requestedSelectionComplete()
            || result.coverage().authorizationStage() != EvaluationCoverage.AuthorizationStage.FACT_COLLECTION
            || result.coverage().conditions() == EvaluationCoverage.ConditionCoverage.PRESERVED
            || result.coverage().permissionMutex() == EvaluationCoverage.MutexCoverage.SKIPPED)) {
            throw new IllegalArgumentException("范围投影要求完整 EVALUATE/ENFORCE 事实");
        }
        List<GrantFact> raw = details.stageFacts().stream().flatMap(s -> s.rawAfterContext().stream()).toList();
        List<GrantFact> kept = details.stageFacts().stream().flatMap(s -> s.retainedAfterEvaluation().stream()).toList();
        Map<String, OperationPermission> index = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(
            details.descriptions().operations().values().stream().map(OperationDefinition::toCacheRow).toList());
        return requirements.stream().map(requirement -> {
            OperationDefinition target = details.descriptions().requestedOperations().get(requirement);
            if (denied || target == null || raw.stream().noneMatch(f -> covers(index, f, target))) {
                return group(requirement, ScopeMode.DENIED, List.of());
            }
            List<GrantFact> covered = kept.stream().filter(f -> covers(index, f, target)).toList();
            if (covered.stream().anyMatch(f -> Boolean.TRUE.equals(f.scopeAll()) && f.resourceEntityId() == null)) {
                return group(requirement, ScopeMode.ALL, List.of());
            }
            Map<ResourceKey, QueryScopesResp.ScopeItem> items = new LinkedHashMap<>();
            for (GrantFact fact : covered) {
                if (fact.resourceEntityId() == null) continue;
                var resource = details.descriptions().resources().get(fact.resourceEntityId());
                if (resource == null || !Objects.equals(resource.resourceType(), target.resourceType())) continue;
                items.putIfAbsent(new ResourceKey(resource.codeType(), resource.code()),
                    new QueryScopesResp.ScopeItem(resource.code(), resource.codeType(), resource.name()));
            }
            return group(requirement, items.isEmpty() ? ScopeMode.EMPTY : ScopeMode.INSTANCE, List.copyOf(items.values()));
        }).toList();
    }

    private static boolean covers(Map<String, OperationPermission> index, GrantFact fact, OperationDefinition target) {
        if (!Objects.equals(fact.resourceType(), target.resourceType())) return false;
        OperationPermission granted = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(index,
            fact.resourceType(), fact.grantedBits());
        return OperationPermissionUtils.covers(granted, target.toCacheRow());
    }

    private static QueryScopesResp.ScopeGroup group(TypeOperation key, ScopeMode mode, List<QueryScopesResp.ScopeItem> items) {
        return new QueryScopesResp.ScopeGroup(key.resourceTypeCode(), key.operationCode(), mode, items);
    }

    private record ResourceKey(String codeType, String code) {}
}
