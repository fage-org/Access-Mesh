package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * T-PERM-027 回归锁（真实 PostgreSQL，权威 DDL 原样执行）：
 * <ol>
 * <li><b>服务删除级联（§7.2 设计定案）</b>：{@code deleteServiceConfigsByIds} 在真实库上
 *     走通「软删服务行 → selectValidByServiceCodes 批量取映射（新 SQL）→ 批量软删全部映射
 *     （含 MANUAL）→ cleanupServiceOwnedResources 软删 SERVICE_SYNC 孤立资源」。
 *     单测 mock mapper 掩盖真实 SQL，此处锁真库行为与清理边界：
 *     跨服务手工映射引用的资源保留、非 SERVICE_SYNC 维护的资源保留、其他服务数据不受影响。</li>
 * <li><b>映射响应资源业务字段补全（§7.3）</b>：{@code listApiMappings} 在真实库上
 *     返回 resourceCode/resourceName/resourceTypeCode/maintainSource（resolveTypeCode
 *     对 DDL type_definition 种子解析 API 类型码）。</li>
 * </ol>
 * 门禁用 mock 引擎放行（门禁语义由单测锁定）；Docker 不可用时自动跳过。
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class ServiceConfigCascadePgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("service_config_cascade_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（见 UserRoleWriteProjectionPgIT 同款说明） */
    private static final String REDIS_TEST_PASSWORD = "accessmesh-test";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }

    @BeforeAll
    static void setupSchema() throws Exception {
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private ServiceConfigMapper serviceConfigMapper;
    @Autowired
    private ResourceApiMappingMapper resourceApiMappingMapper;
    @Autowired
    private ResourceEntityMapper resourceEntityMapper;
    @Autowired
    private TypeResolutionService typeResolutionService;
    @Autowired
    private ResourceSyncHandler resourceSyncHandler;
    @Autowired
    private SyncTypeGuard syncTypeGuard;
    @Autowired
    private ResourceEntityDomainService resourceEntityDomainService;
    @Autowired
    private DomainClassifyService domainClassifyService;
    @Autowired
    private RoleResourcePermissionMapper rolePermMapper;
    @Autowired
    private cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper typeDefinitionMapper;

    private cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard ownershipGuard() {
        return new cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard(
                typeDefinitionMapper, serviceConfigMapper, resourceEntityDomainService, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private PermQueryEngine permitAllEngine() {
        PermQueryEngine engine = mock(PermQueryEngine.class);
        lenient().when(engine.hasPermissionByCode(anyLong(), any(), any(),
            any(), any())).thenReturn(true);
        lenient().when(engine.getDeniedResourceCodes(anyLong(), any(), any(),
            any(), any())).thenReturn(java.util.Set.of());
        return engine;
    }

    private ServiceConfigAppServiceImpl newServiceConfigAppService(PermQueryEngine engine) {
        return new ServiceConfigAppServiceImpl(serviceConfigMapper, engine,
            resourceApiMappingMapper, syncTypeGuard, resourceSyncHandler,
            typeResolutionService, mock(ResourceManageAppService.class));
    }

    private ResourceManageAppService newResourceManageAppService(PermQueryEngine engine) {
        return new ResourceManageAppServiceImpl(resourceEntityMapper, resourceApiMappingMapper,
            resourceEntityDomainService, typeResolutionService, domainClassifyService,
            engine, rolePermMapper, new LocalProjectionGuard(), ownershipGuard(),
            mock(TreeWriteLockSupport.class));
    }

    private final LocalDateTime now = LocalDateTime.now();

    private ServiceConfig insertService(String serviceCode) {
        ServiceConfig config = new ServiceConfig();
        config.setTenantId(TENANT);
        config.setServiceCode(serviceCode);
        config.setName(serviceCode + " 服务");
        config.setBasePath("/" + serviceCode.toLowerCase());
        config.setStatus(1);
        config.setExtra("{}");
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        config.setDeleteFlag(0L);
        serviceConfigMapper.insert(config);
        return config;
    }

    private ResourceEntity insertApiResource(String code, String ownerServiceCode, String maintainSource) {
        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(TENANT);
        entity.setResourceType(3); // API（DDL 权威值）
        entity.setCode(code);
        entity.setCodeType("default");
        entity.setName(code + " 资源");
        entity.setPath("/api/" + code.toLowerCase());
        entity.setStatus(1);
        entity.setSortOrder(0);
        entity.setExtra("{}");
        entity.setOwnerServiceCode(ownerServiceCode);
        entity.setMaintainSource(maintainSource);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleteFlag(0L);
        resourceEntityMapper.insert(entity);
        return entity;
    }

    private ResourceApiMapping insertMapping(Long resourceEntityId, String serviceCode, String path) {
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setTenantId(TENANT);
        mapping.setResourceEntityId(resourceEntityId);
        mapping.setServiceCode(serviceCode);
        mapping.setHttpMethod("POST");
        mapping.setPathPattern(path);
        mapping.setMatchOrder(0);
        mapping.setEnabled(true);
        mapping.setExtra("{}");
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);
        mapping.setDeleteFlag(0L);
        resourceApiMappingMapper.insert(mapping);
        return mapping;
    }

    @Test
    @DisplayName("删除服务：级联软删全部映射与 SERVICE_SYNC 孤立资源；跨服务引用与非同步资源保留")
    void shouldCascadeDeleteServiceDataOnRealPostgres() {
        ServiceConfig svcA = insertService("PGIT27SVC_A");
        ServiceConfig svcB = insertService("PGIT27SVC_B");

        ResourceEntity res1 = insertApiResource("PGIT27_RES1", "PGIT27SVC_A", "SERVICE_SYNC");
        ResourceEntity res2 = insertApiResource("PGIT27_RES2", "PGIT27SVC_A", "SERVICE_SYNC");
        ResourceEntity res3 = insertApiResource("PGIT27_RES3", null, "MANUAL");

        insertMapping(res1.getId(), "PGIT27SVC_A", "/pgit27/a/1");   // A 同步映射
        insertMapping(res2.getId(), "PGIT27SVC_A", "/pgit27/a/2");   // A 同步映射
        insertMapping(res3.getId(), "PGIT27SVC_A", "/pgit27/a/3");   // A 手工映射（MANUAL 资源）
        insertMapping(res2.getId(), "PGIT27SVC_B", "/pgit27/b/2");   // B 跨服务手工映射（引用 A 的同步资源）
        insertMapping(res3.getId(), "PGIT27SVC_B", "/pgit27/b/3");   // B 手工映射

        PermQueryEngine engine = permitAllEngine();
        ServiceConfigAppServiceImpl service = newServiceConfigAppService(engine);
        service.deleteServiceConfigsByIds(TENANT, List.of(svcA.getId()), 100L);

        // 服务行：A 软删、B 保留
        assertThat(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, "PGIT27SVC_A")).isNull();
        assertThat(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, "PGIT27SVC_B")).isNotNull();

        // A 的全部映射（含 MANUAL）软删；B 的映射不受影响
        assertThat(resourceApiMappingMapper.selectValidByServiceCodes(TENANT, java.util.Set.of("PGIT27SVC_A")))
            .isEmpty();
        List<ResourceApiMapping> remainB = resourceApiMappingMapper.selectByTenantAndServiceCode(TENANT, "PGIT27SVC_B");
        assertThat(remainB).hasSize(2);

        // 资源清理边界：res1（无剩余映射）软删；res2（B 跨服务手工映射引用）保留；res3（非 SERVICE_SYNC）保留
        assertThat(resourceEntityMapper.selectValidById(TENANT, res1.getId())).isNull();
        assertThat(resourceEntityMapper.selectValidById(TENANT, res2.getId())).isNotNull();
        assertThat(resourceEntityMapper.selectValidById(TENANT, res3.getId())).isNotNull();
    }

    @Test
    @DisplayName("映射列表在真实库补全资源业务字段（resolveTypeCode 解析 DDL API 类型码）")
    void shouldEnrichMappingResourceFieldsOnRealPostgres() {
        ServiceConfig svc = insertService("PGIT27SVC_C");
        ResourceEntity resource = insertApiResource("PGIT27_RESC", "PGIT27SVC_C", "SERVICE_SYNC");
        insertMapping(resource.getId(), "PGIT27SVC_C", "/pgit27/c/1");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            ResourceManageAppService manageService = newResourceManageAppService(permitAllEngine());

            List<cn.ac.fage.accessmesh.access.permission.dto.resp.ApiMappingResp> result =
                manageService.listApiMappings(TENANT, null, "PGIT27SVC_C");

            assertThat(result).hasSize(1);
            var resp = result.get(0);
            assertThat(resp.resourceCode()).isEqualTo("PGIT27_RESC");
            assertThat(resp.resourceName()).isEqualTo("PGIT27_RESC 资源");
            assertThat(resp.resourceTypeCode()).isEqualTo(ResourceTypeCode.API);
            assertThat(resp.maintainSource()).isEqualTo("SERVICE_SYNC");
        }
    }

    /**
     * 双轨评审 P1 回归锁：updateApiMapping 首查曾把 selectValidById 实参顺序颠倒
     * （(tenantId, mappingId) vs 签名 (id, tenantId)），单测 mock mapper 无法暴露——
     * 真库上除 mappingId==tenantId 巧合外必 RESOURCE_NOT_FOUND，更新端点端到端不可用。
     */
    @Test
    @DisplayName("updateApiMapping 首查参数序在真实库走通（换参修复回归锁）")
    void shouldUpdateMappingWithCorrectArgumentOrderOnRealPostgres() {
        insertService("PGIT27SVC_D");
        ResourceEntity resource = insertApiResource("PGIT27_RESD", "PGIT27SVC_D", "MANUAL");
        ResourceApiMapping mapping = insertMapping(resource.getId(), "PGIT27SVC_D", "/pgit27/d/1");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            ResourceManageAppService manageService = newResourceManageAppService(permitAllEngine());

            var resp = manageService.updateApiMapping(TENANT, new cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingUpdateReq(
                resource.getId(), mapping.getId(), "PUT", "/pgit27/d/1-v2", 5, true, null));

            assertThat(resp.httpMethod()).isEqualTo("PUT");
            assertThat(resp.pathPattern()).isEqualTo("/pgit27/d/1-v2");
            assertThat(resp.matchOrder()).isEqualTo(5);
            assertThat(resp.resourceCode()).isEqualTo("PGIT27_RESD");
        }
    }
}
