package cn.ac.fage.accessmesh.access.engine.query;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 最小事实投影；只消费已完成阶段，不查库、不重新鉴权。复杂投影随 T-PERM-087 接入。 */
final class QueryProjector {
    private QueryProjector() {}

    static ResultDetails project(OutputSpec output, RunState.ItemExecution state) {
        Set<ResultDetails.DetailSection> sections = EnumSet.noneOf(ResultDetails.DetailSection.class);
        Set<Long> roleIds = new LinkedHashSet<>();
        Set<Long> permissionIds = new LinkedHashSet<>();
        if (output.matchedIds()) {
            sections.add(ResultDetails.DetailSection.MATCHED_IDS);
            state.stages.values().forEach(s -> s.retainedAfterEvaluation().forEach(f -> {
                roleIds.add(f.roleId()); permissionIds.add(f.permissionId());
            }));
        }
        List<StageFacts> facts = List.of();
        if (output.factDetail() != FactDetail.NONE) {
            sections.add(ResultDetails.DetailSection.FACTS_KEPT);
            boolean raw = output.factDetail() == FactDetail.RAW_AND_KEPT;
            if (raw) sections.add(ResultDetails.DetailSection.FACTS_RAW);
            facts = state.stages.values().stream().map(s -> new StageFacts(s.stage(),
                raw ? s.rawAfterContext() : List.of(), s.retainedAfterEvaluation(), s.stageStatus())).toList();
        }
        return new ResultDetails(sections, List.copyOf(roleIds), List.copyOf(permissionIds), facts);
    }

}
