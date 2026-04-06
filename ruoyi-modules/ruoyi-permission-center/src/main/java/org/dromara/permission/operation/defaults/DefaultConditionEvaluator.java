package org.dromara.permission.operation.defaults;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.operation.ConditionEvaluator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Primary
@RequiredArgsConstructor
public class DefaultConditionEvaluator implements ConditionEvaluator {

    private final PermissionConditionPresetHandlerRegistry presetHandlerRegistry;
    private final PermissionConditionExpressionEvaluator expressionEvaluator;

    @Override
    public boolean evaluate(MatchedPermission permission, PermissionContext ctx) {
        if (permission.getConditionId() == null) {
            return true;
        }
        PcPermissionCondition condition = permission.getCondition();
        if (condition == null
            || !PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus())
            || Boolean.FALSE.equals(condition.getEnabled())) {
            return false;
        }
        if (condition.getExpression() == null || condition.getExpression().isBlank()) {
            return true;
        }
        Boolean resolved = resolveConditionValue(condition, ctx);
        return Boolean.TRUE.equals(resolved);
    }

    private Boolean resolveConditionValue(PcPermissionCondition condition, PermissionContext ctx) {
        if (PermissionConstants.CONDITION_SOURCE_PRESET.equals(condition.getConditionSource())) {
            return resolvePresetCondition(condition, ctx);
        }
        if (PermissionConstants.CONDITION_SOURCE_CUSTOM.equals(condition.getConditionSource())) {
            return resolveCustomCondition(condition, ctx);
        }
        return resolveLegacyCondition(condition, ctx);
    }

    private Boolean resolvePresetCondition(PcPermissionCondition condition, PermissionContext ctx) {
        String handlerCode = condition.getExpression() == null || condition.getExpression().isBlank()
            ? condition.getCode()
            : condition.getExpression();
        return presetHandlerRegistry.find(handlerCode)
            .map(handler -> handler.evaluate(condition, ctx))
            .orElse(Boolean.FALSE);
    }

    private Boolean resolveCustomCondition(PcPermissionCondition condition, PermissionContext ctx) {
        String expression = condition.getExpression();
        if ("true".equalsIgnoreCase(expression)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(expression)) {
            return Boolean.FALSE;
        }
        return expressionEvaluator.evaluate(expression, ctx.getEvalContext());
    }

    private Boolean resolveLegacyCondition(PcPermissionCondition condition, PermissionContext ctx) {
        String expression = condition.getExpression();
        if (ctx.getEvalContext() == null || ctx.getEvalContext().isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (condition.getCode() != null && !condition.getCode().isBlank()) {
            keys.add(condition.getCode());
            keys.add("condition:" + condition.getCode());
        }
        if (expression != null && !expression.isBlank()) {
            keys.add(expression);
            keys.add("condition:" + expression);
        }
        for (String key : keys) {
            Object value = ctx.getEvalContext().get(key);
            Boolean normalized = normalizeBoolean(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private Boolean normalizeBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            if ("true".equalsIgnoreCase(str)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(str)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }
}
