package org.dromara.permission.condition;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PermissionConditionPresetHandlerRegistry {

    private final Map<String, PermissionConditionPresetHandler> handlers;

    public PermissionConditionPresetHandlerRegistry(List<PermissionConditionPresetHandler> handlers) {
        this.handlers = handlers.stream()
            .collect(Collectors.toMap(
                handler -> handler.getCode().toUpperCase(Locale.ROOT),
                Function.identity(),
                (left, right) -> left));
    }

    public Optional<PermissionConditionPresetHandler> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(code.toUpperCase(Locale.ROOT)));
    }
}
