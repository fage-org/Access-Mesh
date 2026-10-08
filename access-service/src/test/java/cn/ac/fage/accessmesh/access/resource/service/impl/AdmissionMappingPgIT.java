package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigSyncV2Req;
import cn.ac.fage.accessmesh.access.resource.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.resource.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.access.resource.service.ServiceSyncAppService;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.type.service.OperationAppService;
import cn.ac.fage.accessmesh.access.type.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 映射声明、来源隔离、引用守卫与凭证入口的真实数据库验收。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "access.platform.bootstrap.enabled=false", "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN"
})
class AdmissionMappingPgIT {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.StringRedisTemplate tenantFixtureRedis;
    @org.junit.jupiter.api.BeforeEach
    void enableTenantFixture() {
        cn.ac.fage.accessmesh.access.it.TenantTestSupport.enableFixture(jdbc,tenantFixtureRedis,1L);
    }

    private static final String SERVICE = "admission-test";
    private static final String TYPE = "ADMISSION_REPORT";
    private static final int TYPE_VALUE = 10058;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, AdmissionMappingPgIT.class);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ServiceSyncAppService sync;
    @Autowired ServiceConfigAppService configs;
    @Autowired ResourceManageAppService resources;
    @Autowired OperationAppService operations;
    @Autowired TypeDefinitionAppService types;
    @Autowired ServiceCredentialDomainService credentials;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean QueryGate gate;
    @SpyBean PermInvalidationPublisher publisher;
    private Long viewId;
    private Long typeId;

    @BeforeEach
    void setup() {
        // 本类独占 ItInfra 数据库；每个用例重建自身事实，不依赖执行顺序。
        jdbc.update("DELETE FROM resource_api_mapping WHERE service_code IN (?, 'admission-other')", SERVICE);
        jdbc.update("DELETE FROM resource_entity WHERE code LIKE 'admission:%'");
        jdbc.update("DELETE FROM service_credential WHERE service_code = ?", SERVICE);
        jdbc.update("DELETE FROM service_config WHERE service_code IN (?, 'admission-other')", SERVICE);
        jdbc.update("DELETE FROM operation_permission WHERE resource_type = ?", TYPE_VALUE);
        jdbc.update("DELETE FROM type_definition WHERE type_key = 'resource_type' AND type_code = ?", TYPE);
        typeId = jdbc.queryForObject("INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system) "
            + "VALUES (1, 'resource_type', ?, ?, '准入测试', false) RETURNING id", Long.class, TYPE, TYPE_VALUE);
        viewId = jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'VIEW', '查看', 2, 0) RETURNING id", Long.class, TYPE_VALUE);
        jdbc.update("INSERT INTO service_config (tenant_id, service_code, name, base_path) VALUES (1, ?, '准入测试', '/old')", SERVICE);
        when(gate.hasPermissionByCode(any(), any(), any(), any(), any())).thenReturn(true);
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        clearInvocations(publisher);
    }

    @AfterEach
    void teardown() {
        AccessRequestContext.clear();
    }

    @Test
    void should_migrateOldRowsConservativelyAndBackfillBootstrapService_whenSchemaIsUpgraded() throws Exception {
        String migration = java.nio.file.Files.readString(java.nio.file.Path.of("..", "docs", "ops", "operation-admission-migrate-058.sql"));
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA admission_migration_test");
                statement.execute("SET search_path TO admission_migration_test");
                try {
                    statement.execute("CREATE TABLE resource_api_mapping (id bigint, tenant_id bigint, extra jsonb, delete_flag bigint default 0)");
                    statement.execute("CREATE TABLE service_config (tenant_id bigint, service_code varchar(128), name text, status int, delete_flag bigint default 0)");
                    statement.execute("CREATE UNIQUE INDEX service_key ON service_config (tenant_id, service_code) WHERE delete_flag = 0");
                    statement.execute("CREATE TABLE type_definition (tenant_id bigint, type_key text, type_code text, type_value int, delete_flag bigint)");
                    statement.execute("CREATE TABLE resource_entity (tenant_id bigint, resource_type int, code text, code_type text, name text, delete_flag bigint)");
                    statement.execute("INSERT INTO type_definition VALUES (1, 'resource_type', 'SERVICE', 3, 0)");
                    statement.execute("INSERT INTO resource_entity VALUES (1, 3, 'access-service', 'default', 'Access', 0)");
                    statement.execute("INSERT INTO resource_api_mapping (id, tenant_id, extra) VALUES (1, 1, '{\"syncKey\":\"old-marker\"}')");
                    statement.execute(migration);
                    try (var rows = statement.executeQuery("SELECT maintain_source, required_operation_id FROM resource_api_mapping")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("MANUAL");
                        assertThat(rows.getObject(2)).isNull();
                    }
                    try (var rows = statement.executeQuery("SELECT service_code, api_auth_mode FROM service_config")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("access-service");
                        assertThat(rows.getString(2)).isEqualTo("LEGACY_API");
                    }
                } finally {
                    statement.execute("ROLLBACK");
                    statement.execute("SET search_path TO public");
                    statement.execute("DROP SCHEMA admission_migration_test CASCADE");
                }
            }
            return null;
        });
    }

    @Test
    void should_persistAndReadRequirement_whenAdminPublishesV2() {
        var result = sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        assertThat(result.createdMappings()).isEqualTo(1);
        var row = resources.listApiMappings(1L, null, SERVICE).get(0);
        assertThat(row.requiredPermission()).isEqualTo(new RequiredPermission(TYPE, "VIEW"));
        assertThat(row.maintainSource()).isEqualTo("SERVICE_SYNC");
        assertThat(jdbc.queryForObject("SELECT required_operation_id FROM resource_api_mapping WHERE id = ?",
            Long.class, row.id())).isEqualTo(viewId);
    }

    @Test
    void should_rollbackResourcesMappingsAndServiceUpdate_whenOneRequirementIsInvalid() {
        assertThatThrownBy(() -> sync.syncInterfacesV2(1L, request(SERVICE, api("good", "VIEW"), api("bad", "MISSING"))))
            .isInstanceOf(BizException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code LIKE 'admission:%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_api_mapping WHERE service_code = ?", Long.class, SERVICE)).isZero();
        assertThat(jdbc.queryForObject("SELECT base_path FROM service_config WHERE service_code = ?", String.class, SERVICE)).isEqualTo("/old");
        verify(publisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void should_preserveOtherSourcesAndTheirRegistration_whenFullRemovesSyncedDeclaration() {
        sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        Long apiId = jdbc.queryForObject("SELECT id FROM resource_entity WHERE code = 'admission:demo'", Long.class);
        resources.addApiMapping(1L, new ApiMappingAddReq(apiId, SERVICE, "GET", "/manual", 0, true, null,
            new RequiredPermission(TYPE, "VIEW")));
        jdbc.update("INSERT INTO resource_api_mapping (tenant_id, resource_entity_id, service_code, http_method, path_pattern, maintain_source) "
            + "VALUES (1, ?, ?, 'GET', '/bootstrap', 'BOOTSTRAP')", apiId, SERVICE);
        jdbc.update("INSERT INTO resource_api_mapping (tenant_id, resource_entity_id, service_code, http_method, path_pattern, maintain_source) "
            + "VALUES (1, ?, 'admission-other', 'GET', '/other', 'SERVICE_SYNC')", apiId);
        var result = sync.syncInterfacesV2(1L, request(SERVICE));
        assertThat(result.deletedMappings()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT maintain_source FROM resource_api_mapping WHERE service_code = ? AND delete_flag = 0",
            String.class, SERVICE)).containsExactlyInAnyOrder("MANUAL", "BOOTSTRAP");
        assertThat(jdbc.queryForObject("SELECT delete_flag FROM resource_entity WHERE id = ?", Long.class, apiId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_api_mapping WHERE service_code = 'admission-other' AND delete_flag = 0", Long.class)).isEqualTo(1);
    }

    @Test
    void should_rejectCrossSourceRouteWriteAndRollback_whenManualRouteAlreadyExists() {
        Long apiId = registerManualApi();
        resources.addApiMapping(1L, new ApiMappingAddReq(apiId, SERVICE, "GET", "/base/demo", 0, true, null,
            new RequiredPermission(TYPE, "VIEW")));
        assertThatThrownBy(() -> sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW"))))
            .isInstanceOf(BizException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE code = 'admission:demo'", Long.class)).isZero();
        assertThat(resources.listApiMappings(1L, null, SERVICE)).singleElement()
            .satisfies(row -> assertThat(row.maintainSource()).isEqualTo("MANUAL"));
    }

    @Test
    void should_guardDirectAndTypeCascadeDeletion_whenOperationIsReferenced() {
        sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        assertThatThrownBy(() -> operations.deleteOperations(1L, List.of(new OperationKeyReq(TYPE, "VIEW")), 100L))
            .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(20069));
        assertThatThrownBy(() -> operations.updateOperation(1L, new OperationUpdateReq(TYPE, "VIEW", null, 8L, null), 100L))
            .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(20069));
        assertThatThrownBy(() -> types.deleteTypesByIds(1L, List.of(typeId), 100L))
            .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(20069));
        assertThat(jdbc.queryForObject("SELECT delete_flag FROM type_definition WHERE id = ?", Long.class, typeId)).isZero();
        assertThat(jdbc.queryForObject("SELECT delete_flag FROM operation_permission WHERE id = ?", Long.class, viewId)).isZero();
    }

    @Test
    void should_invalidateServiceProjection_whenSameOperationChangesItsCoverage() {
        sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        clearInvocations(publisher);
        operations.updateOperation(1L, new OperationUpdateReq(TYPE, "VIEW", null, null, -1L), 100L);
        assertThat(jdbc.queryForObject("SELECT inherit_mask FROM operation_permission WHERE id = ?", Long.class, viewId)).isEqualTo(-1L);
        verify(publisher).publish(eq(1L), any(), any(), argThat(codes -> codes.contains(SERVICE)));
    }

    @Test
    void should_rebindManualRequirementAndReleaseOldReference_whenUpdated() {
        Long apiId = registerManualApi();
        Long exportId = jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'EXPORT', '导出', 4, 2) RETURNING id", Long.class, TYPE_VALUE);
        var row = resources.addApiMapping(1L, new ApiMappingAddReq(apiId, SERVICE, "GET", "/manual", 0, true, "{}",
            new RequiredPermission(TYPE, "VIEW")));
        var updated = resources.updateApiMapping(1L, new ApiMappingUpdateReq(apiId, row.id(), null, null, null, null,
            null, true, new RequiredPermission(TYPE, "EXPORT")));
        assertThat(updated.requiredPermission()).isEqualTo(new RequiredPermission(TYPE, "EXPORT"));
        assertThat(updated.extra()).isNull();
        assertThat(jdbc.queryForObject("SELECT required_operation_id FROM resource_api_mapping WHERE id = ?", Long.class, row.id())).isEqualTo(exportId);
        operations.deleteOperations(1L, List.of(new OperationKeyReq(TYPE, "VIEW")), 100L);
        assertThat(jdbc.queryForObject("SELECT delete_flag FROM operation_permission WHERE id = ?", Long.class, viewId)).isEqualTo(viewId);
    }

    @Test
    void should_acceptOwnCredentialAndRejectAnotherService_whenCallingV2OverHttp() throws Exception {
        var issued = credentials.issue(1L, SERVICE, null, 100L);
        when(gate.hasPermissionByCode(any(), any(), any(), any(), any())).thenReturn(false);
        AccessRequestContext.clear();
        mvc.perform(post("/api/access/service-config/sync-v2").contentType(MediaType.APPLICATION_JSON)
                .header("X-Credential-Id", issued.entity().getCredentialId()).header("X-Credential-Secret", issued.plainSecret())
                .header("X-Tenant-Id", "999").header("X-Service-Code", "attacker")
                .content(json.writeValueAsString(request(SERVICE, api("demo", "VIEW")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/access/service-config/sync-v2").contentType(MediaType.APPLICATION_JSON)
                .header("X-Credential-Id", issued.entity().getCredentialId()).header("X-Credential-Secret", issued.plainSecret())
                .content(json.writeValueAsString(request("admission-other", api("other", "VIEW")))))
            .andExpect(jsonPath("$.code").value(403));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_api_mapping WHERE service_code = 'admission-other'", Long.class)).isZero();
    }

    @Test
    void should_rejectManualMappingSave_whenAdmissionModeRequiresOperation() {
        configs.saveServiceConfig(1L, new ServiceConfigReq(SERVICE, "准入测试", null, null, 1,
            null, null, null, null), 100L);
        Long apiId = registerManualApi();
        assertThatThrownBy(() -> resources.addApiMapping(1L,
            new ApiMappingAddReq(apiId, SERVICE, "GET", "/base/demo", 0, true, null, null)))
            .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(20071));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_api_mapping WHERE service_code = ?",
            Long.class, SERVICE)).isZero();
    }

    @Test
    void should_keepManagerUpdatedBy_whenCredentialIdentityRepublishes() {
        sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        AccessRequestContext.bind(RequestContext.service(1L, SERVICE));
        try {
            sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        } finally {
            AccessRequestContext.bind(RequestContext.user(1L, 100L));
        }
        assertThat(jdbc.queryForObject(
            "SELECT updated_by FROM resource_api_mapping WHERE service_code = ? AND delete_flag = 0",
            Long.class, SERVICE)).isEqualTo(100L);
    }

    @Test
    void should_normalizeHttpMethodAndRejectCrossSourceSquatter_whenManualUsesLowercase() {
        sync.syncInterfacesV2(1L, request(SERVICE, api("demo", "VIEW")));
        Long apiId = registerManualApi();
        assertThatThrownBy(() -> resources.addApiMapping(1L,
            new ApiMappingAddReq(apiId, SERVICE, "get", "/base/demo", 0, true, null, null)))
            .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(20025));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_api_mapping WHERE service_code = ? AND delete_flag = 0",
            Long.class, SERVICE)).isEqualTo(1);
    }

    private Long registerManualApi() {
        return jdbc.queryForObject("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status) "
            + "SELECT 1, type_value, 'admission:manual', 'default', '人工登记', 1 FROM type_definition "
            + "WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API' AND delete_flag = 0 RETURNING id", Long.class);
    }

    private ServiceConfigSyncV2Req request(String service, ServiceConfigSyncV2Req.ApiItem... items) {
        return new ServiceConfigSyncV2Req(service, "/base", "FULL",
            List.of(new ServiceConfigSyncV2Req.GroupItem("default", "默认", List.of(items))));
    }

    private ServiceConfigSyncV2Req.ApiItem api(String name, String operation) {
        return new ServiceConfigSyncV2Req.ApiItem(name, "GET", "/" + name, "admission:" + name, null,
            new RequiredPermission(TYPE, operation));
    }
}
