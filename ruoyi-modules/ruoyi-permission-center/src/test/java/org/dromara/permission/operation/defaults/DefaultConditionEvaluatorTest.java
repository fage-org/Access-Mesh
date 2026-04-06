package org.dromara.permission.operation.defaults;

import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.condition.builtin.InternalIpConditionHandler;
import org.dromara.permission.condition.builtin.WorkdayOnlyConditionHandler;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcResourceEntity;
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

        PermissionContext context = new PermissionContext(1L, 2L, null, null,
            Map.of(),
            Map.of("request", Map.of("currentDate", "2026-04-06")));

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

        PermissionContext context = new PermissionContext(1L, 2L, null, null,
            Map.of(),
            Map.of("request", Map.of("currentDate", "2026-04-06")));

        assertFalse(evaluator.evaluate(permission, context));
    }

    @Test
    void evaluate_disabledCondition_denies() {
        MatchedPermission permission = new MatchedPermission();
        permission.setConditionId(5L);
        PcPermissionCondition condition = condition(5L, "WORKDAY_ONLY", "WORKDAY_ONLY", PermissionConstants.CONDITION_SOURCE_PRESET);
        condition.setEnabled(Boolean.FALSE);
        permission.setCondition(condition);

        PermissionContext context = new PermissionContext(1L, 2L, null, null,
            Map.of(),
            Map.of("request", Map.of("currentDate", "2026-04-06")));

        assertFalse(evaluator.evaluate(permission, context));
    }

    @Test
    void evaluate_presetCondition_ignoresExplicitOverrideKeys() {
        MatchedPermission permission = new MatchedPermission();
        permission.setConditionId(6L);
        permission.setCondition(condition(6L, "WORKDAY_ONLY", "WORKDAY_ONLY", PermissionConstants.CONDITION_SOURCE_PRESET));

        PermissionContext context = new PermissionContext(1L, 2L, null, null,
            Map.of(
                "WORKDAY_ONLY", false,
                "condition:WORKDAY_ONLY", false
            ),
            Map.of("request", Map.of("currentDate", "2026-04-06")));

        assertTrue(evaluator.evaluate(permission, context));
    }

    @Test
    void evaluate_customCondition_supportsPhase7NamespacedContext() {
        MatchedPermission permission = new MatchedPermission();
        permission.setConditionId(4L);
        permission.setCondition(condition(4L, "CTX_RULE",
            "['subject']['userId'] == 2 and ['network']['clientIp'] == '10.0.0.8' and ['resource']['resourceCode'] == 'API_ORDER' and ['business']['enabled']",
            PermissionConstants.CONDITION_SOURCE_CUSTOM));

        PermissionContext context = new PermissionContext(1L, 2L, 3L, null,
            Map.of("business", Map.of("enabled", true)),
            Map.of("network", Map.of("clientIp", "10.0.0.8")));
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(10L);
        resource.setCode("API_ORDER");
        context.setResource(resource);
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(20L);
        operation.setCode("ACCESS");
        context.setOperation(operation);
        context.bindResourceContext();

        assertTrue(evaluator.evaluate(permission, context));
    }

    private PcPermissionCondition condition(Long id, String code, String expression, String source) {
        PcPermissionCondition condition = new PcPermissionCondition();
        condition.setId(id);
        condition.setCode(code);
        condition.setExpression(expression);
        condition.setConditionSource(source);
        condition.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        condition.setEnabled(Boolean.TRUE);
        condition.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return condition;
    }
}
