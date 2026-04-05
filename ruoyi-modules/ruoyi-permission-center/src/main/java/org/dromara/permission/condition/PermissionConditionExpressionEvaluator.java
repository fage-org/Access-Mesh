package org.dromara.permission.condition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PermissionConditionExpressionEvaluator {

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public Boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Expression spelExpression = expressionParser.parseExpression(expression);
            SimpleEvaluationContext evaluationContext = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .withRootObject(context)
                .build();
            return spelExpression.getValue(evaluationContext, context, Boolean.class);
        } catch (Exception ex) {
            log.warn("evaluate custom permission condition failed, expression={}", expression, ex);
            return Boolean.FALSE;
        }
    }
}
