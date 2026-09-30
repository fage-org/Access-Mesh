package cn.ac.fage.accessmesh.access.engine.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateSelectorTest {
    @Test
    void should_enableIndexOnlyForMeasuredSparseBatches() {
        List<GrantFact> rows = new ArrayList<>();
        List<List<CandidateSelector.Clause>> sparse = new ArrayList<>();
        for (int item = 0; item < 100; item++) {
            sparse.add(List.of(new CandidateSelector.Clause(1, 2, Set.of((long) item))));
            for (int grant = 0; grant < 4; grant++) rows.add(fact(rows.size(), 1, (long) item, 2L, false));
        }
        var selector = CandidateSelector.prepare(rows, Stage.INSTANCE, sparse);
        assertThat(selector).isNotNull();
        for (var clauses : sparse) assertThat(selector.select(clauses)).isEqualTo(CandidateSelector.select(rows, clauses, Stage.INSTANCE));
        assertThat(CandidateSelector.prepare(rows, Stage.INSTANCE, sparse.subList(0, 1))).isNull();
        assertThat(CandidateSelector.prepare(rows, Stage.INSTANCE, sparse.subList(0, 99))).isNull();
        assertThat(CandidateSelector.prepare(rows.subList(0, 399), Stage.INSTANCE, sparse)).isNull();
        Set<Long> everyEntity = java.util.stream.LongStream.range(0, 100).boxed().collect(java.util.stream.Collectors.toSet());
        var dense = Collections.nCopies(100, List.of(new CandidateSelector.Clause(1, 2, everyEntity)));
        assertThat(CandidateSelector.prepare(rows, Stage.INSTANCE, dense)).isNull();
    }

    @Test
    void should_preserveFirstMatchingRowAndOrder_whenBucketsOverlap() {
        var first = fact(9, 1, 20L, 2L, false);
        var second = fact(3, 2, 10L, 4L, false);
        var wrongMaskDuplicate = fact(9, 1, 20L, 8L, false);
        var rows = List.of(wrongMaskDuplicate, first, second, fact(9, 2, 10L, 4L, false));
        var clauses = List.of(new CandidateSelector.Clause(2, 4, Set.of(10L)),
            new CandidateSelector.Clause(1, 2, Set.of(20L)), new CandidateSelector.Clause(1, 6, Set.of(20L)));
        assertThat(new CandidateSelector(rows, Stage.INSTANCE).select(clauses)).containsExactly(first, second);
        assertThat(CandidateSelector.select(rows, clauses, Stage.INSTANCE)).containsExactly(first, second);
    }

    @Test
    void should_keepItemsIndependent_whenTheyShareLoadedRowsAndAncestors() {
        var rows = List.of(fact(1, 1, 10L, 2L, false), fact(2, 2, 20L, 4L, false),
            fact(3, 1, 30L, 2L, false), fact(4, 1, null, 2L, true));
        var selector = new CandidateSelector(rows, Stage.INSTANCE);
        assertThat(selector.select(List.of(new CandidateSelector.Clause(1, 2, Set.of(10L, 30L)))))
            .extracting(GrantFact::permissionId).containsExactly(1L, 3L);
        assertThat(selector.select(List.of(new CandidateSelector.Clause(2, 2, Set.of(20L, 30L))))).isEmpty();
        assertThat(selector.select(List.of(new CandidateSelector.Clause(2, 4, Set.of(20L)))))
            .extracting(GrantFact::permissionId).containsExactly(2L);
    }

    @ParameterizedTest
    @EnumSource(value = Stage.class, names = {"TYPE_GRANT", "INSTANCE", "ADMISSION_CANDIDATES"})
    void should_matchScanIncludingOrder_whenRowsAreMixedAndClausesOverlap(Stage stage) {
        Random random = new Random(93);
        List<GrantFact> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(fact(random.nextInt(700), random.nextInt(5), (long) random.nextInt(20),
                i % 17 == 0 ? null : (long) random.nextInt(16), i % 3 == 0 ? null : i % 3 == 1));
        }
        Collections.shuffle(rows, random);
        var selector = new CandidateSelector(rows, stage);
        for (int i = 0; i < 100; i++) {
            var clauses = List.of(new CandidateSelector.Clause(random.nextInt(5), random.nextInt(16),
                Set.of((long) random.nextInt(20), 30L)),
                new CandidateSelector.Clause(random.nextInt(5), random.nextInt(16), Set.of(1L, 2L, 3L)));
            assertThat(selector.select(clauses)).isEqualTo(CandidateSelector.select(rows, clauses, stage));
        }
        assertThat(selector.select(List.of())).isEmpty();
    }

    private static GrantFact fact(long id, int type, Long entity, Long mask, Boolean all) {
        return new GrantFact(id, 1L, type, entity, mask, all, false, null, false, null, "DIRECT");
    }
}
