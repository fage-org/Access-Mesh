package org.dromara.permission.condition.builtin;

import org.dromara.permission.condition.PermissionConditionPresetHandler;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.model.permission.PermissionContext;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class WorkdayOnlyConditionHandler implements PermissionConditionPresetHandler {

    @Override
    public String getCode() {
        return "WORKDAY_ONLY";
    }

    @Override
    public boolean evaluate(PcPermissionCondition condition, PermissionContext context) {
        Boolean explicitByCode = readExplicitBoolean(condition, context);
        if (explicitByCode != null) {
            return explicitByCode;
        }
        Object explicit = context.getEvalContext().get("isWorkday");
        if (explicit instanceof Boolean value) {
            return value;
        }
        Object dateValue = context.getEvalContext().getOrDefault("currentDate", context.getEvalContext().get("currentDateTime"));
        LocalDate date = toLocalDate(dateValue);
        if (date == null) {
            return false;
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private Boolean readExplicitBoolean(PcPermissionCondition condition, PermissionContext context) {
        Object direct = context.getEvalContext().get(condition.getCode());
        if (direct instanceof Boolean bool) {
            return bool;
        }
        Object namespaced = context.getEvalContext().get("condition:" + condition.getCode());
        if (namespaced instanceof Boolean bool) {
            return bool;
        }
        return null;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDate.parse(text);
            } catch (Exception ignored) {
                try {
                    return LocalDateTime.parse(text).toLocalDate();
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }
}
