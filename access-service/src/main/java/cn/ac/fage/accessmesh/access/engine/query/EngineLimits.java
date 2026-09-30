package cn.ac.fage.accessmesh.access.engine.query;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Map;
import java.util.function.LongSupplier;

/** 服务端结构预算。零表示该项未设限；每次执行持有独立累计器，配置不进入客户端协议。 */
@ConfigurationProperties("accessmesh.query.limits")
public record EngineLimits(@DefaultValue("10000") int maxItems,
                           @DefaultValue("50000") long maxGrantRows,
                           @DefaultValue("200000") long maxClosureEntries,
                           @DefaultValue("250000") long maxCandidatePairs,
                           @DefaultValue("250000") long maxOutputEntries,
                           @DefaultValue("32") int maxContextDepth,
                           @DefaultValue("10000") long maxContextNodes,
                           @DefaultValue("262144") long maxContextCharacters,
                           @DefaultValue("10000") long maxAuditEvidence,
                           @DefaultValue("10000") long maxSnapshotRoutes,
                           @DefaultValue("245760") long maxSnapshotBytes,
                           @DefaultValue("5s") Duration deadline) {
    public EngineLimits {
        deadline = deadline == null ? Duration.ZERO : deadline;
        if (maxItems < 0 || maxGrantRows < 0 || maxClosureEntries < 0 || maxCandidatePairs < 0
            || maxOutputEntries < 0 || maxContextDepth < 0 || maxContextNodes < 0 || maxContextCharacters < 0
            || maxAuditEvidence < 0 || maxSnapshotRoutes < 0 || maxSnapshotBytes < 0 || deadline.isNegative()) {
            throw new IllegalArgumentException("查询预算不可为负数");
        }
        deadline.toNanos();
    }

    public static EngineLimits unlimited() {
        return new EngineLimits(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Duration.ZERO);
    }

    public enum Kind { ITEMS, GRANT_ROWS, CLOSURE_ENTRIES, CANDIDATE_PAIRS, OUTPUT_ENTRIES,
        CONTEXT_DEPTH, CONTEXT_NODES, CONTEXT_CHARACTERS, AUDIT_EVIDENCE, SNAPSHOT_ROUTES, SNAPSHOT_BYTES, DEADLINE }

    Budget openBudget(LongSupplier nanoTime) { return new Budget(this, nanoTime); }

    public void snapshotRoutes(long count) { check(Kind.SNAPSHOT_ROUTES, count, maxSnapshotRoutes); }
    public void snapshotBytes(long count) { check(Kind.SNAPSHOT_BYTES, count, maxSnapshotBytes); }

    private static void check(Kind kind, long count, long maximum) {
        if (count < 0 || (maximum > 0 && count > maximum)) throw new QueryBudgetExceededException(kind);
    }

    /** 只随 RunState 存活；没有 ThreadLocal/跨请求缓存，也不读取 JVM 全局空闲内存。 */
    static final class Budget {
        private final EngineLimits limits;
        private final LongSupplier nanoTime;
        private final long started;
        private long grants;
        private long closures;
        private long candidates;
        private long outputs;
        private long evidence;

        Budget(EngineLimits limits, LongSupplier nanoTime) {
            this.limits = limits;
            this.nanoTime = nanoTime;
            this.started = nanoTime.getAsLong();
        }

        void input(QueryRequest request) {
            check(Kind.ITEMS, request.items().size(), limits.maxItems());
            long selections = 0;
            boolean parentCounted = false;
            for (QueryItem item : request.items()) {
                selections += switch (item.selection()) {
                    case TypeLevel type -> type.requirements().size();
                    case TargetSet target -> target.clauses().size();
                    case OperationAdmission ignored -> 1;
                    case GrantList ignored -> 1;
                };
                selections += item.output().extraOperationKeys().size();
                ParentRequirement parent = item.selection() instanceof TargetSet target ? target.parent()
                    : item.selection() instanceof GrantList list ? list.requiredParent() : null;
                // 结构校验已限定至多一个不同父要求；共享父输入只计一次。
                if (parent != null && !parentCounted) {
                    selections += parent.operationCodes().size();
                    parentCounted = true;
                }
                check(Kind.ITEMS, selections, limits.maxItems());
            }
            if (limits.maxContextDepth() == 0 && limits.maxContextNodes() == 0 && limits.maxContextCharacters() == 0) return;
            ArrayDeque<ContextNode> pending = new ArrayDeque<>();
            pending.add(new ContextNode(request.context().attributes(), 1));
            long nodes = 0;
            boolean countCharacters = limits.maxContextCharacters() > 0;
            long characters = !countCharacters || request.context().clientIp() == null ? 0 : request.context().clientIp().length();
            while (!pending.isEmpty()) {
                ContextNode node = pending.removeLast();
                check(Kind.CONTEXT_DEPTH, node.depth(), limits.maxContextDepth());
                check(Kind.CONTEXT_NODES, ++nodes, limits.maxContextNodes());
                if (node.value() instanceof Map<?, ?> values) {
                    check(Kind.CONTEXT_NODES, nodes + pending.size() + values.size(), limits.maxContextNodes());
                    for (var entry : values.entrySet()) {
                        if (countCharacters) characters += ((String) entry.getKey()).length();
                        pending.add(new ContextNode(entry.getValue(), node.depth() + 1));
                    }
                } else if (node.value() instanceof Collection<?> values) {
                    check(Kind.CONTEXT_NODES, nodes + pending.size() + values.size(), limits.maxContextNodes());
                    for (Object value : values) pending.add(new ContextNode(value, node.depth() + 1));
                } else if (countCharacters && node.value() instanceof String value) {
                    characters += value.length();
                } else if (countCharacters && node.value() instanceof Number value) {
                    // 先用位数的十进制长度下界拒绝超大整数，避免为预算检查构造巨型临时字符串。
                    long minimumDigits = value instanceof java.math.BigInteger integer ? integer.bitLength() / 4L
                        : value instanceof java.math.BigDecimal decimal ? decimal.unscaledValue().bitLength() / 4L : 0;
                    check(Kind.CONTEXT_CHARACTERS, characters + minimumDigits, limits.maxContextCharacters());
                    characters += value.toString().length();
                }
                check(Kind.CONTEXT_CHARACTERS, characters, limits.maxContextCharacters());
            }
        }

        void grants(long count) { grants = add(Kind.GRANT_ROWS, grants, count, limits.maxGrantRows()); }
        void closures(long count) { closures = add(Kind.CLOSURE_ENTRIES, closures, count, limits.maxClosureEntries()); }
        void candidates(long count) { candidates = add(Kind.CANDIDATE_PAIRS, candidates, count, limits.maxCandidatePairs()); }
        void outputs(long count) { outputs = add(Kind.OUTPUT_ENTRIES, outputs, count, limits.maxOutputEntries()); }
        void evidence(long count) { evidence = add(Kind.AUDIT_EVIDENCE, evidence, count, limits.maxAuditEvidence()); }

        private static long add(Kind kind, long current, long count, long maximum) {
            if (count < 0 || count > Long.MAX_VALUE - current) throw new QueryBudgetExceededException(kind);
            long next = current + count;
            check(kind, next, maximum);
            return next;
        }

        void checkpoint() {
            if (!limits.deadline().isZero() && nanoTime.getAsLong() - started >= limits.deadline().toNanos()) {
                throw new QueryBudgetExceededException(Kind.DEADLINE);
            }
        }

        private record ContextNode(Object value, int depth) {}
    }
}
