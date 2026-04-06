package org.dromara.permission.condition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PermissionConditionExpressionEvaluatorTest {

    @Test
    void evaluate_supportsNamespacedPhase7Context() {
        PermissionConditionExpressionEvaluator evaluator = new PermissionConditionExpressionEvaluator(Duration.ofSeconds(1));
        Map<String, Object> context = PermissionConditionContextSupport.normalizeBaseContext(
            1L,
            2L,
            3L,
            Map.of(
                "network", Map.of("clientIp", "10.0.0.9"),
                "business", Map.of("enabled", true)
            )
        );

        Boolean result = evaluator.evaluate(
            "['subject']['userId'] == 2 and ['network']['clientIp'] == '10.0.0.9' and ['business']['enabled']",
            context
        );

        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void evaluate_timeoutReturnsFalse() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                return null;
            }
        });
        PermissionConditionExpressionEvaluator evaluator =
            new PermissionConditionExpressionEvaluator(Duration.ofMillis(10), executor);

        Boolean result = evaluator.evaluate("true", Map.of());

        executor.shutdownNow();

        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void validateExpression_rejectsBrokenSyntax() {
        PermissionConditionExpressionEvaluator evaluator = new PermissionConditionExpressionEvaluator(Duration.ofMillis(100));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> evaluator.validateExpression("['broken'"));

        assertTrue(ex.getMessage() != null);
    }

    @Test
    void evaluate_rejectedByExecutor_returnsFalse() {
        ExecutorService executor = new AbstractExecutorService() {
            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("queue full");
            }
        };
        PermissionConditionExpressionEvaluator evaluator =
            new PermissionConditionExpressionEvaluator(Duration.ofMillis(10), executor);

        Boolean result = evaluator.evaluate("true", Map.of());

        assertEquals(Boolean.FALSE, result);
    }
}
