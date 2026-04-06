package org.dromara.permission.condition;

import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 构建 Phase 7 约定的条件上下文。
 */
public final class PermissionConditionContextSupport {

    private static final Set<String> NAMESPACE_KEYS = Set.of("request", "subject", "network", "resource", "business");

    private static final Set<String> REQUEST_ALIAS_KEYS = Set.of(
        "requestId", "currentDate", "currentDateTime", "httpMethod", "httpPath", "path", "method");

    private static final Set<String> SUBJECT_ALIAS_KEYS = Set.of("tenantId", "userId", "bizDomainId");

    private static final Set<String> NETWORK_ALIAS_KEYS = Set.of("clientIp", "remoteIp");

    private static final Set<String> RESOURCE_ALIAS_KEYS = Set.of(
        "resourceEntityId", "resourceCode", "resourceName", "resourceType", "resourcePath",
        "resourceBizDomainId", "operationPermissionId", "operationCode", "operationName", "operationResourceType");

    private static final Set<String> RESERVED_ALIAS_KEYS = Set.of(
        "requestId", "currentDate", "currentDateTime", "httpMethod", "httpPath", "path", "method",
        "tenantId", "userId", "bizDomainId",
        "clientIp", "remoteIp",
        "resourceEntityId", "resourceCode", "resourceName", "resourceType", "resourcePath", "resourceBizDomainId",
        "operationPermissionId", "operationCode", "operationName", "operationResourceType");

    private PermissionConditionContextSupport() {
    }

    public static Map<String, Object> normalizeBaseContext(Long tenantId, Long userId, Long bizDomainId,
                                                           Map<String, Object> rawContext) {
        return normalizeBaseContext(tenantId, userId, bizDomainId, rawContext, rawContext);
    }

    public static Map<String, Object> normalizeBaseContext(Long tenantId, Long userId, Long bizDomainId,
                                                           Map<String, Object> rawContext,
                                                           Map<String, Object> trustedContext) {
        Map<String, Object> safeRaw = rawContext == null ? Collections.emptyMap() : rawContext;
        Map<String, Object> safeTrusted = trustedContext == null ? Collections.emptyMap() : trustedContext;
        Map<String, Object> request = copyMap(safeTrusted.get("request"));
        Map<String, Object> subject = copyMap(safeTrusted.get("subject"));
        Map<String, Object> network = copyMap(safeTrusted.get("network"));
        Map<String, Object> resource = copyMap(safeTrusted.get("resource"));
        Map<String, Object> business = extractBusinessContext(safeRaw);

        overrideIfNotNull(subject, "tenantId", tenantId);
        overrideIfNotNull(subject, "userId", userId);
        overrideIfNotNull(subject, "bizDomainId", bizDomainId);

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.putAll(business);
        overrideIfNotNull(normalized, "tenantId", tenantId);
        overrideIfNotNull(normalized, "userId", userId);
        overrideIfNotNull(normalized, "bizDomainId", bizDomainId);
        syncAliases(normalized, request, REQUEST_ALIAS_KEYS, true);
        syncAliases(normalized, network, NETWORK_ALIAS_KEYS, true);
        syncAliases(normalized, resource, RESOURCE_ALIAS_KEYS, true);

        normalized.put("request", request);
        normalized.put("subject", subject);
        normalized.put("network", network);
        normalized.put("resource", resource);
        normalized.put("business", business);
        return normalized;
    }

    public static Map<String, Object> extractBusinessContext(Map<String, Object> rawContext) {
        Map<String, Object> safeRaw = rawContext == null ? Collections.emptyMap() : rawContext;
        Map<String, Object> business = copyMap(safeRaw.get("business"));
        assertNoReservedBusinessKeys(business.keySet());
        for (Map.Entry<String, Object> entry : safeRaw.entrySet()) {
            if (!NAMESPACE_KEYS.contains(entry.getKey()) && RESERVED_ALIAS_KEYS.contains(entry.getKey())) {
                throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST,
                    "business context contains reserved key: " + entry.getKey());
            }
            if (!NAMESPACE_KEYS.contains(entry.getKey()) && !RESERVED_ALIAS_KEYS.contains(entry.getKey())) {
                business.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return business;
    }

    public static Map<String, Object> normalizeForExpression(Map<String, Object> context) {
        if (isNormalizedContext(context)) {
            return context;
        }
        Long tenantId = toLong(context == null ? null : context.get("tenantId"));
        Long userId = toLong(context == null ? null : context.get("userId"));
        Long bizDomainId = toLong(context == null ? null : context.get("bizDomainId"));
        return normalizeBaseContext(tenantId, userId, bizDomainId, context);
    }

    public static void bindResourceContext(Map<String, Object> context, PcResourceEntity resource,
                                           PcOperationPermission operation) {
        if (context == null) {
            return;
        }
        Map<String, Object> resourceContext = copyMap(context.get("resource"));
        if (resource != null) {
            putIfNotNull(resourceContext, "resourceEntityId", resource.getId());
            putIfNotNull(resourceContext, "resourceCode", resource.getCode());
            putIfNotNull(resourceContext, "resourceName", resource.getName());
            putIfNotNull(resourceContext, "resourceType", resource.getResourceType());
            putIfNotNull(resourceContext, "resourcePath", resource.getPath());
            putIfNotNull(resourceContext, "resourceBizDomainId", resource.getBizDomainId());
        }
        if (operation != null) {
            putIfNotNull(resourceContext, "operationPermissionId", operation.getId());
            putIfNotNull(resourceContext, "operationCode", operation.getCode());
            putIfNotNull(resourceContext, "operationName", operation.getName());
            putIfNotNull(resourceContext, "operationResourceType", operation.getResourceType());
        }
        context.put("resource", resourceContext);
        syncAliases(context, resourceContext, RESOURCE_ALIAS_KEYS, true);
    }

    private static void syncAliases(Map<String, Object> target, Map<String, Object> source, Collection<String> keys,
                                    boolean overwrite) {
        for (String key : keys) {
            if (source.containsKey(key) && (overwrite || !target.containsKey(key))) {
                target.put(key, source.get(key));
            }
        }
    }

    private static Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copied.put(key, entry.getValue());
            }
        }
        return copied;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void overrideIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isNormalizedContext(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        for (String namespace : NAMESPACE_KEYS) {
            if (!context.containsKey(namespace)) {
                return false;
            }
        }
        return true;
    }

    private static void assertNoReservedBusinessKeys(Collection<String> keys) {
        for (String key : keys) {
            if (RESERVED_ALIAS_KEYS.contains(key)) {
                throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST,
                    "business context contains reserved key: " + key);
            }
        }
    }
}
