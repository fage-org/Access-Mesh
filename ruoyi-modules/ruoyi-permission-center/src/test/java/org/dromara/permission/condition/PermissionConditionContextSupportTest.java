package org.dromara.permission.condition;

import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class PermissionConditionContextSupportTest {

    @Test
    void normalizeBaseContext_topLevelReservedBusinessAlias_throwsInvalidRequest() {
        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () ->
            PermissionConditionContextSupport.normalizeBaseContext(
                1L,
                2L,
                3L,
                Map.of(
                    "clientIp", "8.8.8.8",
                    "business", Map.of("enabled", true),
                    "level", 3
                )
            ));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void normalizeBaseContext_nestedReservedBusinessAlias_throwsInvalidRequest() {
        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () ->
            PermissionConditionContextSupport.normalizeBaseContext(
                1L,
                2L,
                3L,
                Map.of("business", Map.of("resourceCode", "FAKE", "enabled", true))
            ));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void normalizeBaseContext_acceptsNonReservedBusinessFields() {
        Map<String, Object> context = PermissionConditionContextSupport.normalizeBaseContext(
            1L,
            2L,
            3L,
            Map.of(
                "business", Map.of("enabled", true),
                "level", 3
            )
        );

        assertEquals(1L, context.get("tenantId"));
        assertEquals(2L, context.get("userId"));
        assertEquals(3L, context.get("bizDomainId"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) context.get("business")).get("enabled"));
        assertEquals(3, ((Map<?, ?>) context.get("business")).get("level"));
    }

    @Test
    void bindResourceContext_overridesFlatAliasesWithBoundResource() {
        Map<String, Object> context = PermissionConditionContextSupport.normalizeBaseContext(
            1L,
            2L,
            3L,
            Map.of("resource", Map.of("resourceCode", "FAKE", "operationCode", "WRONG"))
        );
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(300L);
        resource.setCode("RES-300");
        resource.setName("Resource 300");
        resource.setResourceType(1);
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(400L);
        operation.setCode("ACCESS");
        operation.setName("Access");
        operation.setResourceType(1);

        PermissionConditionContextSupport.bindResourceContext(context, resource, operation);

        assertEquals("RES-300", ((Map<?, ?>) context.get("resource")).get("resourceCode"));
        assertEquals("RES-300", context.get("resourceCode"));
        assertEquals("ACCESS", context.get("operationCode"));
        assertEquals(300L, context.get("resourceEntityId"));
        assertEquals(400L, context.get("operationPermissionId"));
    }
}
