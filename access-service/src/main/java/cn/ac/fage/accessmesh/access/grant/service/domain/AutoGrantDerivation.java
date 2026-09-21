package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 自动授权共享推导核心（T-PERM-072）。
 * <p>
 * 已装载事实的纯推导：从角色 MANUAL 实例主授权种子出发，沿编译图 {@code resource_dependency}
 * 做逻辑闭包 BFS，产出 desired 自动事实与直接前驱共享 DAG。物化（072）、来源解释与对账（073）
 * 复用同一推导与精确去重核心；不读取缓存/数据库，不按资源启停过滤，不枚举完整路径，不持久化推导图。
 * </p>
 * <p>
 * 事实键 = (资源实体, canonical 操作位, 条件身份)；条件身份是条件行 ID（null 表示无条件变体），
 * 不同 ID 相同表达式视为不同身份。触发判定用源操作有效位（binaryBit|inheritMask），
 * 目标位展开沿用源事实条件直传；NULL 与带条件变体并存、不同操作不因覆盖压缩（M2 定案）。
 * </p>
 */
@Component
public class AutoGrantDerivation {

    /** 逻辑事实键（资源实体 + canonical 操作位 + 条件身份）；conditionId=null 表示无条件变体。 */
    public record Fact(long resourceEntityId, long operationBit, Long conditionId)
        implements Comparable<Fact> {
        @Override
        public int compareTo(Fact other) {
            int byResource = Long.compare(resourceEntityId, other.resourceEntityId);
            if (byResource != 0) return byResource;
            int byBit = Long.compare(operationBit, other.operationBit);
            if (byBit != 0) return byBit;
            return Objects.compare(conditionId, other.conditionId, Comparator.nullsFirst(Comparator.naturalOrder()));
        }
    }

    /** 编译图边（resource_dependency 有效行形态）；sourceOperationBits=null 表示任意有效操作触发。 */
    public record DependencyEdge(Long sourceId, Long targetId, Long sourceOperationBits, long requiredOperationBits) {}

    /**
     * 推导结果：desired 自动事实（按事实键排序）与每个事实的直接前驱集合（共享 DAG；
     * 解释/对账沿直接边按需展开，不返回全路径枚举）。desired = 至少有一个直接前驱的事实
     * ——种子本身无前驱，被推导命中的与种子同键事实（AUTO/MANUAL 并列形态）在 desired 内。
     */
    public record Result(List<Fact> desiredFacts, Map<Fact, Set<Fact>> directPredecessors) {}

    /**
     * 预构建只读图索引（源实体→出边 + 操作有效位）：多角色批量重算（recompute/
     * reconcile/preview 三次推导）对同一张图只构建一次索引、逐角色复用，免 N 角色×
     * E 边的重复索引构建。索引与输入列表一样按不可变对待——构建后共享、绝不修改。
     */
    public static final class PreparedGraph {
        private final Map<Long, List<DependencyEdge>> edgesBySource;
        private final Map<OperationKey, Long> effectiveBits;

        private PreparedGraph(Map<Long, List<DependencyEdge>> edgesBySource,
                              Map<OperationKey, Long> effectiveBits) {
            this.edgesBySource = edgesBySource;
            this.effectiveBits = effectiveBits;
        }
    }

    /** 构建只读图索引（边按源实体分组 + 操作有效位）；同图多角色推导共享一次构建。 */
    public PreparedGraph prepareGraph(List<DependencyEdge> edges, Collection<OperationPermission> operations) {
        Map<Long, List<DependencyEdge>> edgesBySource = new HashMap<>();
        for (DependencyEdge edge : edges) {
            edgesBySource.computeIfAbsent(edge.sourceId(), key -> new ArrayList<>()).add(edge);
        }
        Map<OperationKey, Long> effectiveBits = new HashMap<>();
        for (OperationPermission operation : operations) {
            if (operation == null || operation.getResourceType() == null || operation.getBinaryBit() == null) {
                continue;
            }
            effectiveBits.put(new OperationKey(operation.getResourceType(), operation.getBinaryBit()),
                OperationPermissionUtils.effectiveBits(operation));
        }
        return new PreparedGraph(edgesBySource, effectiveBits);
    }

    /** 单次推导便捷形态：内部构建一次索引后走 {@link #derive(List, Map, PreparedGraph)}。 */
    public Result derive(List<Fact> seeds,
                         Map<Long, Integer> resourceTypes,
                         List<DependencyEdge> edges,
                         Collection<OperationPermission> operations) {
        return derive(seeds, resourceTypes, prepareGraph(edges, operations));
    }

    /** 共享索引推导形态：多角色对同一 {@link PreparedGraph} 逐角色调用。 */
    public Result derive(List<Fact> seeds, Map<Long, Integer> resourceTypes, PreparedGraph graph) {
        Map<Fact, Set<Fact>> predecessors = new TreeMap<>();
        Set<Fact> visited = new LinkedHashSet<>();
        ArrayDeque<Fact> pending = new ArrayDeque<>();
        for (Fact seed : seeds) {
            if (seed == null) continue;
            if (visited.add(seed)) pending.offer(seed);
        }
        while (!pending.isEmpty()) {
            Fact current = pending.poll();
            for (DependencyEdge edge : graph.edgesBySource.getOrDefault(current.resourceEntityId(), List.of())) {
                if (!triggers(current, edge, resourceTypes, graph.effectiveBits)) continue;
                long bits = edge.requiredOperationBits();
                while (bits != 0L) {
                    long bit = Long.lowestOneBit(bits);
                    bits ^= bit;
                    Fact next = new Fact(edge.targetId(), bit, current.conditionId());
                    predecessors.computeIfAbsent(next, key -> new LinkedHashSet<>()).add(current);
                    if (visited.add(next)) pending.offer(next);
                }
            }
        }
        List<Fact> desired = new ArrayList<>(predecessors.keySet());
        desired.sort(Comparator.naturalOrder());
        return new Result(List.copyOf(desired), new LinkedHashMap<>(predecessors));
    }

    /** 触发判定：NULL 触发=任意有效操作；否则源操作有效位与触发位按位与命中。 */
    private boolean triggers(Fact fact, DependencyEdge edge, Map<Long, Integer> resourceTypes,
                             Map<OperationKey, Long> effectiveBits) {
        if (edge.sourceOperationBits() == null) return true;
        Integer resourceType = resourceTypes.get(fact.resourceEntityId());
        Long effective = effectiveBits.get(new OperationKey(resourceType, fact.operationBit()));
        // 操作定义缺失（防御：有效引用守卫下不应出现）回退位本身，不放大继承
        long bits = effective == null ? fact.operationBit() : effective;
        return (bits & edge.sourceOperationBits()) != 0L;
    }

    private record OperationKey(Integer resourceType, long binaryBit) {}
}
