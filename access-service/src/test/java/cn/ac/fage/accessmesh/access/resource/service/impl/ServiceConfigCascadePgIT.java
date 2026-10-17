package cn.ac.fage.accessmesh.access.resource.service.impl;

import tools.jackson.databind.json.JsonMapper;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import cn.ac.fage.accessmesh.access.resource.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class ServiceConfigCascadePgIT {
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService credentials;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void shouldRollbackCredentialRetirement_withServiceDeletion() {
        String code = "rollback-" + java.util.UUID.randomUUID();
        ServiceConfig config = insertService(code);
        var issued = credentials.issue(TENANT, code, null, 100L);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            newServiceConfigAppService(permitAllEngine()).deleteServiceConfigsByIds(TENANT, List.of(config.getId()), 100L);
            throw new IllegalStateException("rollback probe");
        })).hasMessage("rollback probe");
        assertThat(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, code)).isNotNull();
        assertThat(credentials.verify(issued.entity().getCredentialId(), issued.plainSecret()).success()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldSerializeIssuanceAndDeletion_onServiceRow(boolean deleteFirst) throws Exception {
        String code = "race-" + java.util.UUID.randomUUID();
        ServiceConfig config = insertService(code);
        QueryGate engine = permitAllEngine();
        var service = newServiceConfigAppService(engine);
        var issuer = new cn.ac.fage.accessmesh.access.infrastructure.credential.service.impl.ServiceCredentialAppServiceImpl(
            credentials, serviceConfigDomainService, engine);
        var req = new cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialCreateReq(code, null);
        var held = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> inTenantTransaction(() -> {
                if (deleteFirst) service.deleteServiceConfigsByIds(TENANT, List.of(config.getId()), 100L);
                else issuer.create(TENANT, req, 100L);
                held.countDown();
                try {
                    assertThat(release.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(held.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                Runnable operation = () -> inTenantTransaction(() -> {
                    if (deleteFirst) issuer.create(TENANT, req, 100L);
                    else service.deleteServiceConfigsByIds(TENANT, List.of(config.getId()), 100L);
                });
                if (deleteFirst) assertThatThrownBy(operation::run).isInstanceOf(BizException.class)
                    .hasMessageContaining("未注册或已停用");
                else operation.run();
            });
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() "
                    + "AND query LIKE '%service_config%' AND cardinality(pg_blocking_pids(pid))>0", Integer.class) > 0);
            release.countDown();
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, code)).isNull();
            assertThat(credentials.list(TENANT, code)).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private void inTenantTransaction(Runnable action) {
        TenantContextHolder.setTenantId(TENANT);
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> action.run());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void shouldRetireAllCredentials_whenServiceDeletedAndRecreated() {
        String code = "retire-" + java.util.UUID.randomUUID();
        ServiceConfig config = insertService(code);
        var active = credentials.issue(TENANT, code, null, 100L);
        var disabled = credentials.issue(TENANT, code, null, 100L);
        credentials.changeStatus(TENANT, disabled.entity().getId(), 0, 100L);
        var expired = credentials.issue(TENANT, code, LocalDateTime.now().minusDays(1), 100L);
        var other = credentials.issue(TENANT, code + "-other", null, 100L);
        var otherTenant = credentials.issue(2L, code, null, 100L);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> newServiceConfigAppService(permitAllEngine())
            .deleteServiceConfigsByIds(TENANT, List.of(config.getId()), 100L));
        assertThat(credentials.list(TENANT, code)).isEmpty();
        assertThat(credentials.findById(TENANT, other.entity().getId())).isNotNull();
        assertThat(credentials.findById(2L, otherTenant.entity().getId())).isNotNull();
        insertService(code);
        for (var old : List.of(active, disabled, expired)) {
            assertThat(credentials.verify(old.entity().getCredentialId(), old.plainSecret()).success()).isFalse();
            assertThat(credentials.changeStatus(TENANT, old.entity().getId(), 1, 100L)).isZero();
        }
        var fresh = credentials.issue(TENANT, code, null, 100L);
        assertThat(credentials.verify(fresh.entity().getCredentialId(), fresh.plainSecret()).success()).isTrue();
    }
    @org.springframework.beans.factory.annotation.Autowired
    private cn.ac.fage.accessmesh.access.resource.service.domain.ApiMappingWriteDomainService admMappingWriter;
    @org.springframework.beans.factory.annotation.Autowired
    private cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService admOperations;


    private static final Long TENANT = 1L;

    @BeforeEach
    void bindTenantContext() {
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }


    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ServiceConfigCascadePgIT.class);
    }

    @Autowired
    private ServiceConfigMapper serviceConfigMapper;
    @Autowired
    private ResourceApiMappingMapper resourceApiMappingMapper;
    @Autowired
    private ServiceConfigDomainService serviceConfigDomainService;
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
    private RoleResourcePermissionDomainService rolePermMapper;
    @Autowired
    private cn.ac.fage.accessmesh.access.type.mapper.TypeDefinitionMapper typeDefinitionMapper;

    private cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard ownershipGuard() {
        return new cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard(
                typeDefinitionMapper, serviceConfigDomainService, resourceEntityDomainService, JsonMapper.builder().configureForJackson2().build());
    }

    private QueryGate permitAllEngine() {
        QueryGate engine = mock(QueryGate.class);
        lenient().when(engine.hasPermissionByCode(anyLong(), any(), any(),
            any(), any())).thenReturn(true);
        lenient().when(engine.getDeniedResourceCodes(anyLong(), any(), any(),
            any(), any())).thenReturn(java.util.Set.of());
        return engine;
    }

    private ServiceConfigAppServiceImpl newServiceConfigAppService(QueryGate engine) {
        return new ServiceConfigAppServiceImpl(serviceConfigMapper, engine,
            resourceApiMappingMapper, syncTypeGuard, resourceSyncHandler,
            typeResolutionService, mock(ResourceManageAppService.class), org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.class), credentials);
    }

    private ResourceManageAppService newResourceManageAppService(QueryGate engine) {
        return new ResourceManageAppServiceImpl(resourceEntityMapper, resourceApiMappingMapper,
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper.class),
            resourceEntityDomainService, typeResolutionService, domainClassifyService,
            engine, rolePermMapper, org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService.class), ownershipGuard(),
            mock(TreeWriteLockSupport.class), admMappingWriter, admOperations);
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

        QueryGate engine = permitAllEngine();
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

            List<cn.ac.fage.accessmesh.access.resource.dto.resp.ApiMappingResp> result =
                manageService.listApiMappings(TENANT, null, "PGIT27SVC_C");

            assertThat(result).hasSize(1);
            var resp = result.get(0);
            assertThat(resp.resourceCode()).isEqualTo("PGIT27_RESC");
            assertThat(resp.resourceName()).isEqualTo("PGIT27_RESC 资源");
            assertThat(resp.resourceTypeCode()).isEqualTo(ResourceTypeCode.API);
            assertThat(resp.maintainSource()).isEqualTo("MANUAL");
        }
    }

    /**
     * T-PERM-069 回归锁（Q-008 定案「仅 API 收紧」，真实 DDL 种子驱动）：API 类型由种子声明
     * SYNC+access-service——唯一事实入口=service-config/sync-v2 接口声明通道+bootstrap 固定图
     * （两通道领域直写不经管理面门禁），资源管理面写入口须一律 20055 且文案指向事实入口；
     * 旧种子（API 缺省 MANAGED）下 rejectIfSyncManagedType 返回类型行不抛，本用例即失败。
     * SERVICE 维持 MANAGED 对照锁：管理面手工建行仍是新 SERVICE 行唯一通道（按服务实例级
     * 授权目标行），收紧即零 writer（对齐 TypeDefinitionProjectionPgIT 先例形态）。
     */
    @Test
    @DisplayName("T-PERM-069：API 种子声明 SYNC+access-service——管理面写入口 20055 指向 service-config 事实入口；SERVICE 维持 MANAGED 门禁放行")
    void apiTypeSeedShouldRejectManagementCrudWhileServiceStaysManaged() {
        assertThatThrownBy(() -> ownershipGuard().rejectIfSyncManagedType(TENANT, ResourceTypeCode.API))
            .isInstanceOf(BizException.class)
            .extracting("errorCode")
            .isEqualTo(20055)
            .isNotNull();
        assertThatThrownBy(() -> ownershipGuard().rejectIfSyncManagedType(TENANT, ResourceTypeCode.API))
            .hasMessageContaining("资源由系统事实链路维护")
            .hasMessageContaining("服务接口同步 service-config/sync-v2")
            .hasMessageContaining("API");
        assertThat(ownershipGuard().rejectIfSyncManagedType(TENANT, ResourceTypeCode.SERVICE))
            .isNotNull()
            .extracting(TypeDefinition::getTypeCode)
            .isEqualTo(ResourceTypeCode.SERVICE);
    }

    /**
     * 双轨评审 P1 回归锁：updateApiMapping 首查曾把 selectValidById 实参顺序颠倒
     * （(tenantId, mappingId) vs 签名 (id, tenantId)），单测 mock mapper 无法暴露——
     * 真库上除 mappingId==tenantId 巧合外必 RESOURCE_NOT_FOUND，更新端点端到端不可用。
     */
    @Test
    @DisplayName("updateApiMapping 首查参数序在真实库走通（换参修复回归锁）")
    @org.springframework.transaction.annotation.Transactional
    void shouldUpdateMappingWithCorrectArgumentOrderOnRealPostgres() {
        insertService("PGIT27SVC_D");
        ResourceEntity resource = insertApiResource("PGIT27_RESD", "PGIT27SVC_D", "MANUAL");
        ResourceApiMapping mapping = insertMapping(resource.getId(), "PGIT27SVC_D", "/pgit27/d/1");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            ResourceManageAppService manageService = newResourceManageAppService(permitAllEngine());

            var resp = manageService.updateApiMapping(TENANT, new cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingUpdateReq(
                resource.getId(), mapping.getId(), "PUT", "/pgit27/d/1-v2", 5, true, null, null,
                new cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission("RESOURCE", "VIEW")));

            assertThat(resp.httpMethod()).isEqualTo("PUT");
            assertThat(resp.pathPattern()).isEqualTo("/pgit27/d/1-v2");
            assertThat(resp.matchOrder()).isEqualTo(5);
            assertThat(resp.resourceCode()).isEqualTo("PGIT27_RESD");
        }
    }
}
