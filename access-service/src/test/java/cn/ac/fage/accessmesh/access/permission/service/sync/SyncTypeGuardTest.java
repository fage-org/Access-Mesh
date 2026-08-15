package cn.ac.fage.accessmesh.access.permission.service.sync;

import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncTypeGuard.SyncTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SyncTypeGuard} 服务-类型同步白名单测试（service_config.extra.syncTypes，fail-closed）。
 */
@ExtendWith(MockitoExtension.class)
class SyncTypeGuardTest {

    private static final Long TENANT = 1L;
    private static final String SERVICE = "hr-service";
    private static final String DECLARED =
            "{\"syncTypes\": {\"subjectTypeCodes\": [\"EMP\"], \"roleTypeCodes\": [\"TEAM_ROLE\"],"
                    + " \"resourceTypeCodes\": [\"HR_ORG\"], \"sourceTypes\": [\"HR_MEMBER\"]}}";

    @Mock
    private ServiceConfigMapper serviceConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SyncTypeGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SyncTypeGuard(serviceConfigMapper, objectMapper);
    }

    private ServiceConfig config(String extra, Integer status, Long deleteFlag) {
        ServiceConfig c = new ServiceConfig();
        c.setTenantId(TENANT);
        c.setServiceCode(SERVICE);
        c.setStatus(status);
        c.setDeleteFlag(deleteFlag);
        c.setExtra(extra);
        return c;
    }

    @Test
    @DisplayName("服务未注册 → 拒绝")
    void shouldReject_whenServiceNotRegistered() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE)).thenReturn(null);

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
    }

    @Test
    @DisplayName("服务已删除 → 拒绝")
    void shouldReject_whenServiceDeleted() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(DECLARED, 1, 9L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
    }

    @Test
    @DisplayName("服务禁用（status=0）→ 拒绝")
    void shouldReject_whenServiceDisabled() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(DECLARED, 0, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
    }

    @Test
    @DisplayName("extra 缺失 → 拒绝（配置缺失按无权限处理）")
    void shouldReject_whenExtraMissing() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(null, 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
    }

    @Test
    @DisplayName("extra 非法 JSON → 拒绝（配置损坏按无权限处理）")
    void shouldReject_whenExtraInvalid() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config("not-json", 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
    }

    @Test
    @DisplayName("syncTypes 缺失 → 拒绝")
    void shouldReject_whenSyncTypesMissing() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config("{\"name\": \"hr\"}", 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
    }

    @Test
    @DisplayName("对应分类缺失 → 该分类任何请求拒绝")
    void shouldReject_whenCategoryMissing() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config("{\"syncTypes\": {\"roleTypeCodes\": [\"TEAM_ROLE\"]}}", 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isFalse();
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.resource("HR_ORG"))).isFalse();
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.role("TEAM_ROLE"))).isTrue();
    }

    @Test
    @DisplayName("类型已声明 → 允许")
    void shouldAllow_whenTypeDeclared() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(DECLARED, 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isTrue();
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.role("TEAM_ROLE"))).isTrue();
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.resource("HR_ORG"))).isTrue();
        assertThat(guard.validate(TENANT, SERVICE,
                SyncTypes.userRole(java.util.Set.of("EMP"), java.util.Set.of("TEAM_ROLE"),
                        java.util.Set.of("HR_MEMBER")))).isTrue();
    }

    @Test
    @DisplayName("类型未声明 → 拒绝（不得使用其他服务的类型空间）")
    void shouldReject_whenTypeNotDeclared() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(DECLARED, 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("PROJ_USER"))).isFalse();
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.resource("PROJ_ORG"))).isFalse();
    }

    @Test
    @DisplayName("请求无类型要求 → 直接允许（不查配置；仅引用类型不校验）")
    void shouldAllow_whenNoTypeRequired() {
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.NONE)).isTrue();
        verify(serviceConfigMapper, never()).selectByTenantAndServiceCode(anyLong(), anyString());
    }

    @Test
    @DisplayName("单次请求只查询一次 service_config（禁止按 item 查询）")
    void shouldQueryConfigOnce() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(DECLARED, 1, 0L));

        guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"));
        guard.validate(TENANT, SERVICE, SyncTypes.role("TEAM_ROLE"));

        verify(serviceConfigMapper, times(2)).selectByTenantAndServiceCode(TENANT, SERVICE);
    }

    @Test
    @DisplayName("声明值去首尾空白、忽略空白项（不做大小写转换，精确匹配）")
    void shouldTrimDeclaredValues() {
        String padded = "{\"syncTypes\": {\"subjectTypeCodes\": [\"  EMP  \", \" \", \"PROJ_USER\"]}}";
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
                .thenReturn(config(padded, 1, 0L));

        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("EMP"))).isTrue();
        // 大小写敏感：声明 "EMP" 不匹配 "emp"
        assertThat(guard.validate(TENANT, SERVICE, SyncTypes.subject("emp"))).isFalse();
    }
}
