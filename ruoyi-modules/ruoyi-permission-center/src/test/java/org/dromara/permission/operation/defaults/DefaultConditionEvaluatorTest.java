package org.dromara.permission.operation.defaults;

import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.condition.builtin.InternalIpConditionHandler;
import org.dromara.permission.condition.builtin.WorkdayOnlyConditionHandler;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class DefaultConditionEvaluatorTest {

    private final DefaultConditionEvaluator evaluator = new DefaultConditionEvaluator(
        new PermissionConditionPresetHandlerRegistry(List.of(
            new WorkdayOnlyConditionHandler(),
            new InternalIpConditionHandler()
        )),
        new PermissionConditionExpressionEvaluator()
    );

    @Test
    void evaluate_presetCondition_usesHandler() {
        MatchedPermission permission = new MatchedPermission();
        permission.setConditionId(1L);
        permission.setCondition(condition(1L, "WORKDAY_ONLY", "WORKDAY_ONLY", PermissionConstants.CONDITION_SOURCE_PRESET));

        PermissionContext context = new PermissionContext(1L, 2L, null, null, Map.of("isWorkday", true));

        assertTrue(evaluator.evaluate(permission, context));
    }

    @Test
    void evaluate_customCondition_usesExpression() {
        MatchedPermission permission = new MatchedPermission();
        permission.setConditionId(2L);
        permission.setCondition(condition(2L, "LEVEL_RULE", "['enabled'] and ['level'] >= 2",
            PermissionConstants.CONDITION_SOURCE_CUSTOM));

        PermissionContext context = new PermissionContext(1L, 2L, null, null, Map.of("enabled", true, "level", 2));

        assertTrue(evaluator.evaluate(permission, context));
    }

    @Test
    void evaluate_pendingCondition_denies() {
        MatchedPermission permission = new MatchedPermission();
        permission.setConditionId(3L);
        PcPermissionCondition condition = condition(3L, "WORKDAY_ONLY", "WORKDAY_ONLY", PermissionConstants.CONDITION_SOURCE_PRESET);
        condition.setStatus(PermissionConstants.CONDITION_STATUS_PENDING);
        permission.setCondition(condition);

        PermissionContext context = new PermissionContext(1L, 2L, null, null, Map.of("isWorkday", true));

        assertFalse(evaluator.evaluate(permission, context));
    }

    private PcPermissionCondition condition(Long id, String code, String expression, String source) {
        PcPermissionCondition condition = new PcPermissionCondition();
        condition.setId(id);
        condition.setCode(code);
        condition.setExpression(expression);
        condition.setConditionSource(source);
        condition.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        condition.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return condition;
    }
}
