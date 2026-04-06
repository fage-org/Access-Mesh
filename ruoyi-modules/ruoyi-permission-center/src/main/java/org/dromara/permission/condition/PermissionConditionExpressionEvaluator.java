package org.dromara.permission.condition;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class PermissionConditionExpressionEvaluator {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(200);

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final ExecutorService executorService;

    public PermissionConditionExpressionEvaluator() {
        this(DEFAULT_TIMEOUT);
    }

    PermissionConditionExpressionEvaluator(Duration timeout) {
        this(timeout, createDefaultExecutor());
    }

    PermissionConditionExpressionEvaluator(Duration timeout, ExecutorService executorService) {
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        this.executorService = executorService;
    }

    public void validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is blank");
        }
        try {
            resolveExpression(expression);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid expression", ex);
        }
    }

    public Boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        Map<String, Object> safeContext = PermissionConditionContextSupport.normalizeForExpression(context);
        Future<Boolean> future = null;
        try {
            Expression spelExpression = resolveExpression(expression);
            future = executorService.submit(() -> evaluate(spelExpression, safeContext));
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            if (future != null) {
                future.cancel(true);
            }
            log.warn("evaluate custom permission condition timed out, expression={}, timeout={}ms",
                expression, timeout.toMillis());
            return Boolean.FALSE;
        } catch (RejectedExecutionException ex) {
            log.warn("evaluate custom permission condition rejected by executor, expression={}", expression, ex);
            return Boolean.FALSE;
        } catch (Exception ex) {
            log.warn("evaluate custom permission condition failed, expression={}", expression, ex);
            return Boolean.FALSE;
        }
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdownNow();
    }

    private Expression resolveExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, expressionParser::parseExpression);
    }

    private Boolean evaluate(Expression spelExpression, Map<String, Object> context) {
        SimpleEvaluationContext evaluationContext = SimpleEvaluationContext
            .forPropertyAccessors(new MapAccessor())
            .withRootObject(context)
            .build();
        return spelExpression.getValue(evaluationContext, context, Boolean.class);
    }

    private static ExecutorService createDefaultExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            new CustomizableThreadFactory("perm-cond-eval-")
        );
        executor.prestartAllCoreThreads();
        return executor;
    }
}
