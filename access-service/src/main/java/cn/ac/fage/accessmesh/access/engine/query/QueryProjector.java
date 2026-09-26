package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation.*;

/** 判定完成后的输出投影；装载经读取部件批量完成，不重新评估、不修改阶段事实。 */
final class QueryProjector {
    private QueryProjector() {}

    static Map<QueryItem, ResultDetails> project(RunState run, QueryReadSupport reads, ResourceEntityMapper mapper) {
        Set<Integer> operationTypes = new LinkedHashSet<>();
        Set<TypeOperation> extraKeys = new LinkedHashSet<>();
        Set<Long> parents = new LinkedHashSet<>(), children = new LinkedHashSet<>();
        Set<Long> resourceIds = new LinkedHashSet<>(), roleIds = new LinkedHashSet<>();
        for (var entry : run.items().entrySet()) {
            OutputSpec output = entry.getKey().output();
            List<GrantFact> raw = allFacts(entry.getValue(), true), kept = allFacts(entry.getValue(), false);
            if (output.descriptions() || output.effectiveOperations()) {
                raw.stream().map(GrantFact::resourceType).filter(Objects::nonNull).forEach(operationTypes::add);
                extraKeys.addAll(output.extraOperationKeys());
            }
            if (output.presentationExpansion().parents()) parents.addAll(entities(kept));
            if (output.presentationExpansion().children()) children.addAll(entities(kept));
            if (output.descriptions()) {
                resourceIds.addAll(entities(raw));
                raw.stream().map(GrantFact::roleId).filter(Objects::nonNull).forEach(roleIds::add);
            }
        }
        // 完整输出定义合批；各项随后只取同来源子集，不覆盖判定掩码桶。
        OutputSpec definitions = new OutputSpec(FactDetail.NONE, false, true, false,
            PresentationExpansion.NONE, extraKeys, false);
        reads.outputOperations(run, definitions, operationTypes);
        reads.resolveOperations(run, extraKeys);
        Map<Long, Set<Long>> ancestorIds = reads.ancestorClosures(run, mapper, parents);
        Map<Long, Set<Long>> descendantIds = reads.descendantClosures(run, mapper, children);
        Map<QueryItem, List<PresentationEntry>> presentations = new LinkedHashMap<>();
        run.items().forEach((item, state) -> {
            List<PresentationEntry> entries = present(item.output(), allFacts(state, false), ancestorIds, descendantIds);
            presentations.put(item, entries);
            if (item.output().descriptions()) entries.stream().map(PresentationEntry::displayedEntityId)
                .filter(Objects::nonNull).forEach(resourceIds::add);
        });
        reads.resourceDescriptions(run, definitions, resourceIds);
        reads.roleDescriptions(run, definitions, roleIds);
        Map<RunState.ParentExecution, ResultDetails.ParentCheckSummary> parentSummaries = new LinkedHashMap<>();
        run.parents().values().forEach(parent -> parentSummaries.put(parent, projectParent(run, reads, parent)));
        Map<QueryItem, ResultDetails> results = new LinkedHashMap<>();
        run.items().forEach((item, state) -> results.put(item,
            projectItem(run, reads, item.output(), state, presentations.get(item),
                parentSummaries.getOrDefault(state.parent, ResultDetails.ParentCheckSummary.empty()))));
        return results;
    }

    private static ResultDetails projectItem(RunState run, QueryReadSupport reads, OutputSpec output,
                                              RunState.ItemExecution state, List<PresentationEntry> presentation,
                                              ResultDetails.ParentCheckSummary parentSummary) {
        Set<ResultDetails.DetailSection> sections = EnumSet.noneOf(ResultDetails.DetailSection.class);
        if (state.parent != null) sections.add(ResultDetails.DetailSection.PARENT_CHECK);
        List<GrantFact> raw = allFacts(state, true), kept = allFacts(state, false);
        Set<Long> roleIds = new LinkedHashSet<>(), permissionIds = new LinkedHashSet<>();
        if (output.matchedIds()) {
            sections.add(ResultDetails.DetailSection.MATCHED_IDS);
            kept.forEach(f -> { roleIds.add(f.roleId()); permissionIds.add(f.permissionId()); });
        }
        List<StageFacts> facts = List.of();
        if (output.factDetail() != FactDetail.NONE) {
            sections.add(ResultDetails.DetailSection.FACTS_KEPT);
            boolean includeRaw = output.factDetail() == FactDetail.RAW_AND_KEPT;
            if (includeRaw) sections.add(ResultDetails.DetailSection.FACTS_RAW);
            facts = state.stages.values().stream().map(s -> new StageFacts(s.stage(),
                includeRaw ? s.rawAfterContext() : List.of(), s.retainedAfterEvaluation(), s.stageStatus())).toList();
        }
        Set<Integer> types = new LinkedHashSet<>();
        raw.stream().map(GrantFact::resourceType).filter(Objects::nonNull).forEach(types::add);
        Map<Integer, List<OperationDefinition>> operations = reads.outputOperations(run, output, types);
        ResultDetails.Descriptions descriptions = ResultDetails.Descriptions.empty();
        if (output.descriptions()) {
            sections.add(ResultDetails.DetailSection.DESCRIPTIONS);
            Set<Long> ids = entities(raw), roles = new LinkedHashSet<>();
            presentation.stream().map(PresentationEntry::displayedEntityId).filter(Objects::nonNull).forEach(ids::add);
            raw.stream().map(GrantFact::roleId).filter(Objects::nonNull).forEach(roles::add);
            Map<Long, ResultDetails.ResourceDescription> resources = new LinkedHashMap<>();
            reads.resourceDescriptions(run, output, ids).forEach((id, row) ->
                resources.put(id, ResultDetails.ResourceDescription.from(row)));
            Map<Long, ResultDetails.RoleDescription> roleDescriptions = new LinkedHashMap<>();
            reads.roleDescriptions(run, output, roles).forEach((id, row) ->
                roleDescriptions.put(id, ResultDetails.RoleDescription.from(row)));
            Map<Long, OperationDefinition> definitions = new LinkedHashMap<>();
            operations.values().forEach(values -> values.forEach(op -> definitions.put(op.id(), op)));
            descriptions = new ResultDetails.Descriptions(resources, roleDescriptions, definitions,
                reads.resolveOperations(run, output.extraOperationKeys()));
        }
        List<ResultDetails.EffectiveOperationEntry> effective = List.of();
        if (output.effectiveOperations()) {
            sections.add(ResultDetails.DetailSection.EFFECTIVE_OPERATIONS);
            effective = effectiveOperations(kept, presentation, operations);
        }
        if (output.presentationExpansion() != PresentationExpansion.NONE) sections.add(ResultDetails.DetailSection.PRESENTATION);
        return new ResultDetails(sections, List.copyOf(roleIds), List.copyOf(permissionIds), facts, descriptions,
            effective, output.presentationExpansion() == PresentationExpansion.NONE ? List.of() : presentation,
            parentSummary);
    }

    /** 父判断已装载全部要求的定义；复用同源记忆与 retained，不补跑短路阶段或条件。 */
    private static ResultDetails.ParentCheckSummary projectParent(RunState run, QueryReadSupport reads,
                                                                   RunState.ParentExecution parent) {
        List<GrantFact> retained = allFacts(parent.execution, false);
        if (retained.isEmpty()) return ResultDetails.ParentCheckSummary.empty();
        Set<TypeOperation> requirements = new LinkedHashSet<>();
        ((TargetSet) parent.item.selection()).clauses().forEach(clause -> requirements.add(clause.operation()));
        Map<TypeOperation, OperationDefinition> targets = reads.resolveOperations(run, requirements);
        Set<Integer> types = new LinkedHashSet<>();
        targets.values().forEach(target -> types.add(target.resourceType()));
        Map<String, OperationPermission> grantedIndex = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(
            reads.freshOperations(run, types).values().stream().flatMap(List::stream)
                .map(OperationDefinition::toCacheRow).toList());
        List<String> matched = targets.entrySet().stream().filter(target -> {
            OperationPermission requested = target.getValue().toCacheRow();
            return retained.stream().filter(fact -> Objects.equals(fact.resourceType(), requested.getResourceType()))
                .anyMatch(fact -> OperationPermissionUtils.covers(
                    OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(grantedIndex,
                        fact.resourceType(), fact.grantedBits()), requested));
        }).map(target -> target.getKey().operationCode()).distinct().sorted().toList();
        return new ResultDetails.ParentCheckSummary(matched);
    }

    private static List<GrantFact> allFacts(RunState.ItemExecution state, boolean raw) {
        Map<Long, GrantFact> facts = new LinkedHashMap<>();
        state.stages.values().forEach(stage -> (raw ? stage.rawAfterContext() : stage.retainedAfterEvaluation())
            .forEach(fact -> facts.putIfAbsent(fact.permissionId(), fact)));
        return List.copyOf(facts.values());
    }

    private static Set<Long> entities(List<GrantFact> facts) {
        Set<Long> ids = new LinkedHashSet<>();
        facts.stream().filter(f -> !Boolean.TRUE.equals(f.scopeAll())).map(GrantFact::resourceEntityId)
            .filter(Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private static List<PresentationEntry> present(OutputSpec output, List<GrantFact> facts,
        Map<Long, Set<Long>> parents, Map<Long, Set<Long>> children) {
        if (output.presentationExpansion() == PresentationExpansion.NONE && !output.effectiveOperations()) return List.of();
        Set<PresentationEntry> entries = new LinkedHashSet<>();
        for (GrantFact fact : facts) {
            Long id = fact.resourceEntityId();
            entries.add(new PresentationEntry(fact.permissionId(), fact.roleId(), id, ORIGINAL));
            if (id == null || Boolean.TRUE.equals(fact.scopeAll())) continue;
            if (output.presentationExpansion().parents()) parents.getOrDefault(id, Set.of()).stream()
                .filter(parent -> !id.equals(parent)).sorted().forEach(parent ->
                    entries.add(new PresentationEntry(fact.permissionId(), fact.roleId(), parent, PARENT)));
            if (output.presentationExpansion().children()) children.getOrDefault(id, Set.of()).stream()
                .sorted().forEach(child -> entries.add(new PresentationEntry(fact.permissionId(), fact.roleId(), child, CHILD)));
        }
        return List.copyOf(entries);
    }

    private static List<ResultDetails.EffectiveOperationEntry> effectiveOperations(List<GrantFact> facts,
        List<PresentationEntry> presentation, Map<Integer, List<OperationDefinition>> definitions) {
        Map<Long, GrantFact> sources = new LinkedHashMap<>();
        facts.forEach(f -> sources.put(f.permissionId(), f));
        Map<Integer, List<OperationPermission>> operations = new LinkedHashMap<>();
        definitions.forEach((type, values) -> operations.put(type, values.stream().map(OperationDefinition::toCacheRow).toList()));
        List<ResultDetails.EffectiveOperationEntry> result = new ArrayList<>();
        for (PresentationEntry entry : presentation) {
            GrantFact fact = sources.get(entry.sourcePermissionId());
            List<OperationPermission> catalog = operations.getOrDefault(fact.resourceType(), List.of());
            OperationPermission granted = OperationPermissionUtils.findByResourceTypeAndBinaryBit(catalog,
                fact.resourceType(), fact.grantedBits());
            if (granted == null) continue;
            for (OperationPermission covered : OperationPermissionUtils.coveredOperations(granted, catalog)) {
                if (covered.getCode() == null || covered.getBinaryBit() == null) continue;
                var derivation = entry.derivation() == ORIGINAL && !Objects.equals(granted.getId(), covered.getId())
                    ? OPERATION_COVERAGE : entry.derivation();
                result.add(new ResultDetails.EffectiveOperationEntry(fact.permissionId(), fact.roleId(),
                    entry.displayedEntityId(), derivation, fact.resourceType(), fact.grantedBits(), granted.getCode(),
                    OperationPermissionUtils.effectiveBits(granted), covered.getCode(), covered.getBinaryBit()));
            }
        }
        return List.copyOf(result);
    }
}
