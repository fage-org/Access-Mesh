package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.DependencyEdge;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.Fact;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.Result;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动授权共享推导核心单测（T-PERM-072，用例对照设计 §6.3.1「推导与撤销的可检验预期」全表）。
 * <p>
 * 约定：资源 ID 101/102/103/104/105（A/B/C/D/X），类型值 910；操作位 VIEW=1、READ=2、WRITE=4、
 * EXPORT=8；条件身份 C1=11L、C2=12L。
 * </p>
 */
class AutoGrantDerivationTest {

    private final AutoGrantDerivation derivation = new AutoGrantDerivation();

    private static final long A = 101L;
    private static final long B = 102L;
    private static final long C = 103L;
    private static final long D = 104L;
    private static final long X = 105L;
    private static final long VIEW = 1L;
    private static final long READ = 2L;
    private static final long WRITE = 4L;
    private static final long EXPORT = 8L;
    private static final Long C1 = 11L;
    private static final Long C2 = 12L;

    private static Fact fact(long resource, long bit, Long condition) {
        return new Fact(resource, bit, condition);
    }

    private static DependencyEdge edge(long source, long target, Long trigger, long required) {
        return new DependencyEdge(source, target, trigger, required);
    }

    private static OperationPermission op(long bit, long inheritMask) {
        OperationPermission operation = new OperationPermission();
        operation.setResourceType(910);
        operation.setBinaryBit(bit);
        operation.setInheritMask(inheritMask);
        return operation;
    }

    private static final Map<Long, Integer> TYPES = Map.of(
        A, 910, B, 910, C, 910, D, 910, X, 910, 999L, 910);

    /** §6.3.1 行 1：菱形图——条件变体与无条件变体各自闭包，全部四事实推导。 */
    @Test
    void shouldDeriveFullClosureWithConditionAndNullVariants_diamondGraph() {
        List<DependencyEdge> edges = List.of(
            edge(A, B, VIEW, READ),
            edge(B, C, READ, VIEW),
            edge(D, B, VIEW, READ));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, C1), fact(D, VIEW, null)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0)));

        assertThat(result.desiredFacts()).containsExactlyInAnyOrder(
            fact(B, READ, C1), fact(C, VIEW, C1), fact(B, READ, null), fact(C, VIEW, null));
    }

    /** §6.3.1 行 2：共享来源——同一事实一条，两条直接支持边都可回溯。 */
    @Test
    void shouldKeepSingleFactWithBothDirectPredecessors_sharedSources() {
        List<DependencyEdge> edges = List.of(
            edge(A, B, VIEW, READ),
            edge(D, B, VIEW, READ));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, C1), fact(D, VIEW, C1)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0)));

        assertThat(result.desiredFacts()).containsExactly(fact(B, READ, C1));
        assertThat(result.directPredecessors().get(fact(B, READ, C1)))
            .containsExactlyInAnyOrder(fact(A, VIEW, C1), fact(D, VIEW, C1));
    }

    /** §6.3.1 行 3：不同条件身份（表达式相同）必须保留两条事实。 */
    @Test
    void shouldKeepDistinctConditionIdentities_whenExpressionsEqual() {
        List<DependencyEdge> edges = List.of(
            edge(A, B, VIEW, READ),
            edge(D, B, VIEW, READ));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, C1), fact(D, VIEW, C2)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0)));

        assertThat(result.desiredFacts()).containsExactlyInAnyOrder(fact(B, READ, C1), fact(B, READ, C2));
    }

    /** §6.3.1 行 4：不得从同资源的无条件 VIEW 借用 NONE——只有 EXPORT/C1 派生。 */
    @Test
    void shouldNotBorrowUnconditionalVariantFromOtherOperation() {
        List<DependencyEdge> edges = List.of(edge(A, B, EXPORT, READ));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, null), fact(A, EXPORT, C1)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0), op(EXPORT, 0)));

        assertThat(result.desiredFacts()).containsExactly(fact(B, READ, C1));
    }

    /** §6.3.1 行 5：推导出与 MANUAL 种子同键的事实时 AUTO 事实仍在 desired（AUTO/MANUAL 并列形态）。 */
    @Test
    void shouldKeepDerivedFactAlongsideManualSeed_sameFactKey() {
        List<DependencyEdge> edges = List.of(edge(X, A, VIEW, VIEW));

        Result result = derivation.derive(
            List.of(fact(X, VIEW, C1), fact(A, VIEW, C1)),
            TYPES, edges, List.of(op(VIEW, 0)));

        assertThat(result.desiredFacts()).containsExactly(fact(A, VIEW, C1));
        assertThat(result.directPredecessors().get(fact(A, VIEW, C1)))
            .containsExactly(fact(X, VIEW, C1));
    }

    /** 撤 D 后（种子只剩 A）：中间事实 B 仍继续向 C 传播，不因来源减少而断链。 */
    @Test
    void shouldContinuePropagationThroughIntermediateFacts_afterSharedSourceRemoved() {
        List<DependencyEdge> edges = List.of(
            edge(A, B, VIEW, READ),
            edge(B, C, READ, VIEW));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, C1)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0)));

        assertThat(result.desiredFacts()).containsExactlyInAnyOrder(fact(B, READ, C1), fact(C, VIEW, C1));
    }

    /** NULL 触发=任意有效操作；目标操作集合多位展开为多条事实、条件直传。 */
    @Test
    void shouldTriggerOnAnyOperationAndExpandRequiredBits_whenSourceBitsNull() {
        List<DependencyEdge> edges = List.of(edge(A, B, null, READ | WRITE));

        Result result = derivation.derive(
            List.of(fact(A, EXPORT, C2)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0), op(WRITE, 0), op(EXPORT, 0)));

        assertThat(result.desiredFacts()).containsExactlyInAnyOrder(fact(B, READ, C2), fact(B, WRITE, C2));
    }

    /** 触发判定用有效位（binaryBit|inheritMask）：VIEW 继承 EXPORT 时按 EXPORT 触发边命中。 */
    @Test
    void shouldTriggerViaInheritedEffectiveBits() {
        List<DependencyEdge> edges = List.of(edge(A, B, EXPORT, READ));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, C1)),
            TYPES, edges, List.of(op(VIEW, EXPORT), op(READ, 0)));

        assertThat(result.desiredFacts()).containsExactly(fact(B, READ, C1));
    }

    /** 触发位未被有效位覆盖（无继承）时不推导——操作覆盖不跨位借用。 */
    @Test
    void shouldNotTrigger_whenTriggerBitNotCovered() {
        List<DependencyEdge> edges = List.of(edge(A, B, EXPORT, READ));

        Result result = derivation.derive(
            List.of(fact(A, VIEW, null)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0), op(EXPORT, 0)));

        assertThat(result.desiredFacts()).isEmpty();
    }

    /** 操作定义缺失（防御面）回退裸位判定，不放大继承。 */
    @Test
    void shouldFallBackToBareBit_whenOperationDefinitionMissing() {
        List<DependencyEdge> edges = List.of(edge(999L, B, EXPORT, READ));

        Result result = derivation.derive(
            List.of(fact(999L, EXPORT, C1)),
            TYPES, edges, List.of(op(VIEW, 0), op(READ, 0)));

        assertThat(result.desiredFacts()).containsExactly(fact(B, READ, C1));
    }

    /** 空图 / 无种子：desired 为空（物化器据此短路）。 */
    @Test
    void shouldReturnEmptyDesired_whenGraphOrSeedsEmpty() {
        assertThat(derivation.derive(List.of(fact(A, VIEW, C1)), TYPES, List.of(), List.of())
            .desiredFacts()).isEmpty();
        assertThat(derivation.derive(List.of(), TYPES,
                List.of(edge(A, B, VIEW, READ)), List.of(op(VIEW, 0), op(READ, 0)))
            .desiredFacts()).isEmpty();
    }
}
