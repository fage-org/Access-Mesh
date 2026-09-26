package cn.ac.fage.accessmesh.access.engine.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 无 I/O 的精确候选选择；物理装载超集不改变 item 的类型—操作—目标配对。 */
final class CandidateSelector {
    private CandidateSelector() {}

    record Clause(int type, long mask, Set<Long> closure) {
        Clause { closure = Set.copyOf(closure); }
    }

    static List<GrantFact> select(List<GrantFact> loaded, List<Clause> clauses, Stage stage) {
        Map<Long, GrantFact> selected = new LinkedHashMap<>();
        for (GrantFact fact : loaded) {
            if ((stage == Stage.TYPE_GRANT) != Boolean.TRUE.equals(fact.scopeAll())) continue;
            for (Clause clause : clauses) {
                if (Objects.equals(fact.resourceType(), clause.type()) && fact.grantedBits() != null
                    && (fact.grantedBits() & clause.mask()) != 0
                    && (stage == Stage.TYPE_GRANT || clause.closure().contains(fact.resourceEntityId()))) {
                    selected.putIfAbsent(fact.permissionId(), fact);
                    break;
                }
            }
        }
        return List.copyOf(selected.values());
    }
}
