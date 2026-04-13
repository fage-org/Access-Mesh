package org.dromara.permission.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PermissionMetricsCollector Tests")
@Tag("dev")
class PermissionMetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private PermissionMetricsCollector collector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collector = new PermissionMetricsCollector(meterRegistry);
    }

    @Test
    @DisplayName("recordCheck should increment check counter")
    void recordCheckShouldIncrementCheckCounter() {
        collector.recordCheck("tenant1", true, 100);

        assertEquals(1, meterRegistry.get(PermissionMetrics.CHECK_REQUESTS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordCheck with denied should increment denial counter")
    void recordCheckWithDeniedShouldIncrementDenialCounter() {
        collector.recordCheck("tenant1", false, 100);

        assertEquals(1, meterRegistry.get(PermissionMetrics.CHECK_DENIALS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordSnapshotRequest should increment snapshot counter")
    void recordSnapshotRequestShouldIncrementSnapshotCounter() {
        collector.recordSnapshotRequest("tenant1", true, 50);

        assertEquals(1, meterRegistry.get(PermissionMetrics.SNAPSHOT_REQUESTS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordCacheHit should increment cache hit counter")
    void recordCacheHitShouldIncrementCacheHitCounter() {
        collector.recordCacheHit();
        collector.recordCacheHit();
        collector.recordCacheMiss();

        assertEquals(2.0 / 3.0, collector.getCacheHitRate(), 0.001);
    }

    @Test
    @DisplayName("recordVersionQuery should increment version counter")
    void recordVersionQueryShouldIncrementVersionCounter() {
        collector.recordVersionQuery("tenant1", true);
        collector.recordVersionQuery("tenant1", true);

        // 两次成功调用，status=success 的 counter 应该是 2
        assertEquals(2, meterRegistry.get(PermissionMetrics.VERSION_REQUESTS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordVersionQuery failure should increment failure counter")
    void recordVersionQueryFailureShouldIncrementFailureCounter() {
        collector.recordVersionQuery("tenant1", false);

        assertEquals(1, meterRegistry.get(PermissionMetrics.VERSION_FAILURES).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordConditionEvaluation should increment condition counter")
    void recordConditionEvaluationShouldIncrementConditionCounter() {
        collector.recordConditionEvaluation("tenant1", "PRESET", true, 10);
        collector.recordConditionEvaluation("tenant1", "PRESET", true, 20);

        assertEquals(2, meterRegistry.get(PermissionMetrics.CONDITION_EVALUATIONS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordConditionEvaluation error should increment error counter")
    void recordConditionEvaluationErrorShouldIncrementErrorCounter() {
        collector.recordConditionEvaluation("tenant1", "CUSTOM", false, 20);

        assertEquals(1, meterRegistry.get(PermissionMetrics.CONDITION_ERRORS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordConflictCheck should increment conflict counter")
    void recordConflictCheckShouldIncrementConflictCounter() {
        collector.recordConflictCheck("tenant1", false);
        collector.recordConflictCheck("tenant1", false);

        assertEquals(2, meterRegistry.get(PermissionMetrics.CONFLICT_CHECKS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordConflictCheck with conflict should increment hit counter")
    void recordConflictCheckWithConflictShouldIncrementHitCounter() {
        collector.recordConflictCheck("tenant1", true);

        assertEquals(1, meterRegistry.get(PermissionMetrics.CONFLICT_HITS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordDependencyCheck should increment dependency counter")
    void recordDependencyCheckShouldIncrementDependencyCounter() {
        collector.recordDependencyCheck("tenant1", true);
        collector.recordDependencyCheck("tenant1", true);

        assertEquals(2, meterRegistry.get(PermissionMetrics.DEPENDENCY_CHECKS).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordDependencyCheck failure should increment failure counter")
    void recordDependencyCheckFailureShouldIncrementFailureCounter() {
        collector.recordDependencyCheck("tenant1", false);

        assertEquals(1, meterRegistry.get(PermissionMetrics.DEPENDENCY_FAILURES).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordAuditWrite should increment audit counter")
    void recordAuditWriteShouldIncrementAuditCounter() {
        collector.recordAuditWrite("tenant1", true);
        collector.recordAuditWrite("tenant1", true);

        assertEquals(2, meterRegistry.get(PermissionMetrics.AUDIT_WRITES).counter().count(), 0.001);
    }

    @Test
    @DisplayName("recordAuditWrite failure should increment failure counter")
    void recordAuditWriteFailureShouldIncrementFailureCounter() {
        collector.recordAuditWrite("tenant1", false);

        assertEquals(1, meterRegistry.get(PermissionMetrics.AUDIT_FAILURES).counter().count(), 0.001);
    }

    @Test
    @DisplayName("getStats should return formatted stats")
    void getStatsShouldReturnFormattedStats() {
        collector.recordCacheHit();
        collector.recordCacheMiss();
        collector.updateCacheSize(100);

        String stats = collector.getStats();
        assertTrue(stats.contains("cacheHits=1"));
        assertTrue(stats.contains("cacheMisses=1"));
        assertTrue(stats.contains("cacheSize=100"));
    }
}
