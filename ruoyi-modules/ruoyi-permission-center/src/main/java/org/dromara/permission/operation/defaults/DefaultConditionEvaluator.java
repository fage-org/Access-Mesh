package org.dromara.permission.operation.defaults;

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
public class DefaultConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean evaluate(MatchedPermission permission, PermissionContext ctx) {
        if (permission.getConditionId() == null) {
            return true;
        }
        PcPermissionCondition condition = permission.getCondition();
        if (condition == null || !PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus())) {
            return false;
        }
        if (condition.getExpression() == null || condition.getExpression().isBlank()) {
            return true;
        }
        Boolean resolved = resolveConditionValue(condition, ctx);
        return Boolean.TRUE.equals(resolved);
    }

    private Boolean resolveConditionValue(PcPermissionCondition condition, PermissionContext ctx) {
        String expression = condition.getExpression();
        if ("true".equalsIgnoreCase(expression)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(expression)) {
            return Boolean.FALSE;
        }
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
