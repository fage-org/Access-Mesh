package org.dromara.permission.handler;
import org.dromara.permission.constant.ResourceTypeConstants;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ResourceTypeHandlerRegistry {

    private final ResourceTypeHandler defaultHandler;
    private final Map<String, ResourceTypeHandler> handlers;
    private final TypeDefinitionReader typeDefinitionReader;

    public ResourceTypeHandlerRegistry(List<ResourceTypeHandler> allHandlers, TypeDefinitionReader typeDefinitionReader) {
        this.defaultHandler = allHandlers.stream()
            .filter(handler -> handler.getResourceType() == null)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("missing default resource type handler"));
        this.handlers = allHandlers.stream()
            .filter(handler -> handler.getResourceType() != null)
            .collect(Collectors.toMap(handler -> handler.getResourceType().toUpperCase(Locale.ROOT), Function.identity(), (left, right) -> right));
        this.typeDefinitionReader = typeDefinitionReader;
    }

    public ResourceTypeHandler getHandler(Long tenantId, Integer resourceType) {
        if (resourceType == null) {
            return defaultHandler;
        }
        String typeName = typeDefinitionReader.findTypeName(tenantId, ResourceTypeConstants.TYPE_KEY, resourceType)
            .orElseThrow(() -> new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST,
                "resource_type definition not found: " + resourceType));
        return handlers.getOrDefault(typeName, defaultHandler);
    }
}
