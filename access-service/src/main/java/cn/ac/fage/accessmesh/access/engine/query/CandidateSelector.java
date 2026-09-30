package cn.ac.fage.accessmesh.access.engine.query;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 无 I/O 的精确候选选择；物理装载超集不改变 item 的类型—操作—目标配对。 */
final class CandidateSelector {
    private final Stage stage;
    private final Map<Integer, Map<Long, List<Row>>> index;

    /** 小批零建桶；大批仅在保守访问预算明显低于扫描时使用索引。 */
    static CandidateSelector prepare(List<GrantFact> loaded, Stage stage, Collection<List<Clause>> items) {
        if (items.size() < 100 || loaded.size() < 400) return null;
        long scanVisits = (long) loaded.size() * items.size();
        long maximumCost = (scanVisits - 1) / 4;
        long cost = loaded.size();
        CandidateSelector selector = new CandidateSelector(loaded, stage);
        for (List<Clause> clauses : items) {
            for (Clause clause : clauses) {
                Map<Long, List<Row>> type = selector.index.get(clause.type());
                if (type == null) continue;
                if (stage == Stage.INSTANCE) {
                    for (Long entity : clause.closure()) {
                        List<Row> bucket = type.get(entity);
                        cost += bucket == null ? 0 : bucket.size();
                        if (cost > maximumCost) return null;
                    }
                } else {
                    List<Row> bucket = type.get(null);
                    cost += bucket == null ? 0 : bucket.size();
                    if (cost > maximumCost) return null;
                }
            }
        }
        return selector;
    }

    /** 索引生命周期限于一个物理装载批；未启用时不建桶，保留扫描基线。 */
    CandidateSelector(List<GrantFact> loaded, Stage stage) {
        this.stage = stage;
        this.index = new HashMap<>();
        for (int position = 0; position < loaded.size(); position++) {
            GrantFact fact = loaded.get(position);
            if (!matchesStage(fact, stage)) continue;
            Long entity = stage == Stage.INSTANCE ? fact.resourceEntityId() : null;
            index.computeIfAbsent(fact.resourceType(), ignored -> new HashMap<>())
                .computeIfAbsent(entity, ignored -> new ArrayList<>()).add(new Row(position, fact));
        }
    }

    List<GrantFact> select(List<Clause> clauses) {
        List<Row> matching = new ArrayList<>();
        for (Clause clause : clauses) {
            Map<Long, List<Row>> type = index.get(clause.type());
            if (type == null) continue;
            if (stage == Stage.INSTANCE) {
                for (Long entity : clause.closure()) collect(type.get(entity), clause.mask(), matching);
            } else {
                collect(type.get(null), clause.mask(), matching);
            }
        }
        // 以输入位置归并；不能按实体/授权 ID 排序，否则会改变 first 来源。
        matching.sort(Comparator.comparingInt(Row::position));
        Map<Long, GrantFact> selected = new LinkedHashMap<>();
        matching.forEach(row -> selected.putIfAbsent(row.fact().permissionId(), row.fact()));
        return List.copyOf(selected.values());
    }

    private void collect(List<Row> bucket, long mask, List<Row> matching) {
        if (bucket == null) return;
        for (Row row : bucket) {
            Long bits = row.fact().grantedBits();
            if (bits != null && (bits & mask) != 0) matching.add(row);
        }
    }

    private record Row(int position, GrantFact fact) {}

    record Clause(int type, long mask, Set<Long> closure) {
        Clause { closure = Set.copyOf(closure); }
    }

    static List<GrantFact> select(List<GrantFact> loaded, List<Clause> clauses, Stage stage) {
        Map<Long, GrantFact> selected = new LinkedHashMap<>();
        for (GrantFact fact : loaded) {
            if (!matchesStage(fact, stage)) continue;
            for (Clause clause : clauses) {
                if (Objects.equals(fact.resourceType(), clause.type()) && fact.grantedBits() != null
                    && (fact.grantedBits() & clause.mask()) != 0
                    && (stage == Stage.TYPE_GRANT || stage == Stage.ADMISSION_CANDIDATES
                        || clause.closure().contains(fact.resourceEntityId()))) {
                    selected.putIfAbsent(fact.permissionId(), fact);
                    break;
                }
            }
        }
        return List.copyOf(selected.values());
    }

    private static boolean matchesStage(GrantFact fact, Stage stage) {
        return stage == Stage.ADMISSION_CANDIDATES
            || (stage == Stage.TYPE_GRANT) == Boolean.TRUE.equals(fact.scopeAll());
    }
}
