package cn.ac.fage.accessmesh.access.engine.query;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class EngineLimitsTest {
    @Test
    void should_includeSharedParentSelectionsInInputBudget() {
        var parent = new ParentRequirement("REPORT", new ByEntityId(1), Set.of("VIEW", "UPDATE"));
        var selection = new TargetSet(List.of(new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByEntityId(2))),
            Inheritance.SELF, TypeFallback.ALLOW, parent);
        var request = new QueryRequest(1, new Roles(Set.of(1L)), CallerContext.of(null), ReadOptions.defaults(),
            List.of(QueryItem.decision("child", selection, OutputSpec.minimal())));
        var limits = new EngineLimits(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Duration.ZERO);
        assertThatThrownBy(() -> limits.openBudget(System::nanoTime).input(request)).hasMessageContaining("ITEMS");
    }

    @Test
    void should_useOneMonotonicDeadlineWithoutResettingAtCheckpoints() {
        AtomicLong ticks = new AtomicLong(100);
        var budget = limits(0, 0, 0, 0, 0, 0, Duration.ofNanos(10)).openBudget(ticks::get);
        ticks.set(109);
        assertThatCode(budget::checkpoint).doesNotThrowAnyException();
        ticks.set(110);
        assertThatThrownBy(budget::checkpoint).isInstanceOf(QueryBudgetExceededException.class).hasMessageContaining("DEADLINE");
    }

    @Test
    void should_reachConfiguredDepthFailureInsteadOfOverflowingDuringContextCopy() {
        Object nested = "value";
        for (int depth = 0; depth < 10000; depth++) nested = Map.of("child", nested);
        var context = new CallerContext(null, Map.of("deep", nested));
        var request = new QueryRequest(1, new Roles(Set.of(1L)), context, ReadOptions.defaults(), List.of());
        assertThatThrownBy(() -> limits(0, 0, 0, 32, 0, 0, Duration.ZERO).openBudget(System::nanoTime).input(request))
            .isInstanceOf(QueryBudgetExceededException.class).hasMessageContaining("CONTEXT_DEPTH");
    }
    @Test
    void should_countCandidatePairsAcrossItemsAndResetBetweenExecutions() {
        var limits = limits(0, 0, 3, 0, 0, 0, Duration.ZERO);
        var budget = limits.openBudget(System::nanoTime);
        budget.candidates(2);
        budget.candidates(1);
        assertThatThrownBy(() -> budget.candidates(1)).isInstanceOf(QueryBudgetExceededException.class)
            .hasMessageContaining("CANDIDATE_PAIRS");
        assertThatCode(() -> limits.openBudget(System::nanoTime).candidates(3)).doesNotThrowAnyException();
    }

    @Test
    void should_rejectRowsAndClosureAtTheirOwnLimitsWithoutTruncation() {
        var budget = limits(2, 3, 0, 0, 0, 0, Duration.ZERO).openBudget(System::nanoTime);
        budget.grants(2);
        budget.closures(3);
        assertThatThrownBy(() -> budget.grants(1)).hasMessageContaining("GRANT_ROWS");
        assertThatThrownBy(() -> budget.closures(1)).hasMessageContaining("CLOSURE_ENTRIES");
    }

    @Test
    void should_limitNestedContextUsingStructureRatherThanObjectAllocation() {
        var context = new CallerContext(null, Map.of("report", Map.of("region", List.of("east"))));
        var request = new QueryRequest(1, new Roles(Set.of(1L)), context, ReadOptions.defaults(), List.of());
        assertThatThrownBy(() -> limits(0, 0, 0, 2, 0, 0, Duration.ZERO)
            .openBudget(System::nanoTime).input(request)).hasMessageContaining("CONTEXT_DEPTH");
        assertThatThrownBy(() -> limits(0, 0, 0, 0, 2, 0, Duration.ZERO)
            .openBudget(System::nanoTime).input(request)).hasMessageContaining("CONTEXT_NODES");
        assertThatThrownBy(() -> limits(0, 0, 0, 0, 0, 3, Duration.ZERO)
            .openBudget(System::nanoTime).input(request)).hasMessageContaining("CONTEXT_CHARACTERS");
    }

    @Test
    void should_rejectNegativeLimitsAtConfigurationTime() {
        assertThatThrownBy(() -> limits(-1, 0, 0, 0, 0, 0, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static EngineLimits limits(long grants, long closures, long candidates, int depth,
                                       long nodes, long characters, Duration timeout) {
        return new EngineLimits(0, grants, closures, candidates, 0, depth, nodes, characters, 0, 0, 0, timeout);
    }
}
