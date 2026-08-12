package cn.ac.fage.accessmesh.access.admin.sync.handler;

import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link SyncTaskHandlerRegistry} 按 syncAction 路由到正确的 handler。
 */
class SyncTaskHandlerRegistryTest {

    @Test
    void resolve_returnsCorrectHandler_forEachAction() {
        ObjectMapper mapper = new ObjectMapper();
        var feign = mock(cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient.class);
        SyncTaskHandler user = new PermAbstractUserSyncHandler(feign, mapper);
        SyncTaskHandler role = new PermAbstractRoleSyncHandler(feign, mapper);
        SyncTaskHandler userRole = new PermUserRoleSyncHandler(feign, mapper);
        SyncTaskHandler resource = new PermResourceEntitySyncHandler(feign, mapper);

        SyncTaskHandlerRegistry registry = new SyncTaskHandlerRegistry(List.of(user, role, userRole, resource));

        assertThat(registry.resolve(SyncTaskBuilder.ACTION_ABSTRACT_USER_SYNC)).isSameAs(user);
        assertThat(registry.resolve(SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC)).isSameAs(role);
        assertThat(registry.resolve(SyncTaskBuilder.ACTION_USER_ROLE_SYNC)).isSameAs(userRole);
        assertThat(registry.resolve(SyncTaskBuilder.ACTION_RESOURCE_ENTITY_SYNC)).isSameAs(resource);
    }

    @Test
    void resolve_throwsBizException_whenActionUnknown() {
        SyncTaskHandlerRegistry registry = new SyncTaskHandlerRegistry(List.of());
        assertThatThrownBy(() -> registry.resolve("UNKNOWN_ACTION"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("UNKNOWN_ACTION");
    }

    @Test
    void constructor_rejectsDuplicateAction() {
        ObjectMapper mapper = new ObjectMapper();
        var feign = mock(cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient.class);
        SyncTaskHandler a = new PermAbstractUserSyncHandler(feign, mapper);
        SyncTaskHandler b = new PermAbstractUserSyncHandler(feign, mapper);
        assertThatThrownBy(() -> new SyncTaskHandlerRegistry(List.of(a, b)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
    }
}
