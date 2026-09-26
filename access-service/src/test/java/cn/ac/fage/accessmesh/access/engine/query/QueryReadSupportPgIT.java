package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.characterization.R2BaselineFixture;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 真 PG/Redis 验证 SQL 合批、租户/类型/目标实参、缓存载荷；不消费新 execute 的未实现阶段。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true", "logging.level.cn.ac.fage.accessmesh=WARN"
})
class QueryReadSupportPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, QueryReadSupportPgIT.class); }

    @Autowired private TypeResolutionService types;
    @Autowired private OperationPermissionDomainService operations;
    @Autowired private ResourceEntityDomainService resources;
    @Autowired private AbstractRoleMapper roles;
    @Autowired private RoleResourcePermissionMapper grants;
    @Autowired private RolePermEntryMapper entries;
    @Autowired private CacheService cache;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionFactory sessions;
    private QueryReadSupport reads;
    private SqlCounter counter;

    @BeforeEach
    void prepare() {
        new R2BaselineFixture(jdbc).seedBaselineGraph();
        counter = sessions.getConfiguration().getInterceptors().stream().filter(SqlCounter.class::isInstance)
            .map(SqlCounter.class::cast).findFirst().orElseGet(() -> {
                SqlCounter installed = new SqlCounter();
                sessions.getConfiguration().addInterceptor(installed);
                return installed;
            });
        counter.operations.set(0); counter.grants.set(0);
        reads = new QueryReadSupport(types, operations, resources, roles, grants, cache, entries);
    }

    private static RunState run(long tenant, ListGrantRead source) {
        return new RunState(new QueryRequest(tenant, new Roles(Set.of()), CallerContext.of(null),
            new ReadOptions(source), List.of()), Clock.systemUTC());
    }

    @Test
    void should_prepareOneSqlForTwentyTypesAndRememberEmpty_whenReadingFreshDefinitions() {
        Set<Integer> requested = IntStream.range(50000, 50019).boxed().collect(Collectors.toSet());
        requested.add(R2BaselineFixture.TYPE_T1);
        RunState run = run(R2BaselineFixture.TENANT, ListGrantRead.DATABASE);
        Map<Integer, List<OperationDefinition>> result = reads.freshOperations(run, requested);
        assertThat(result).hasSize(20);
        assertThat(result.get(R2BaselineFixture.TYPE_T1)).extracting(OperationDefinition::code)
            .containsExactlyInAnyOrder("VIEW", "UPDATE", "CREATE", "DELETE");
        assertThat(result.get(50000)).isEmpty();
        reads.freshOperations(run, requested);
        assertThat(counter.operations.get()).as("实际 JDBC prepare 次数，非 DomainService 调用数").isEqualTo(1);
    }

    @Test
    void should_pushDownTenantRolesTypesAndTargets_whenLoadingRawGrants() {
        RunState run = run(R2BaselineFixture.TENANT, ListGrantRead.DATABASE);
        assertThat(reads.scopeGrants(run, Set.of(R2BaselineFixture.ROLE_B), Map.of(R2BaselineFixture.TYPE_T2, 2L)))
            .extracting(GrantFact::permissionId).containsExactly(R2BaselineFixture.PERM_T2_SCOPE_ALL);
        assertThat(reads.instanceGrants(run, Set.of(R2BaselineFixture.ROLE_A), Set.of(R2BaselineFixture.RES_R2),
            Map.of(R2BaselineFixture.TYPE_T1, 2L))).extracting(GrantFact::permissionId).containsExactly(R2BaselineFixture.PERM_R2_VIEW);
        assertThat(reads.instanceGrants(run, Set.of(R2BaselineFixture.ROLE_A), Set.of(R2BaselineFixture.RES_R2),
            Map.of(R2BaselineFixture.TYPE_T2, 2L))).isEmpty();
        assertThat(reads.listGrants(run(R2BaselineFixture.TENANT + 1, ListGrantRead.DATABASE), Set.of(R2BaselineFixture.ROLE_A)))
            .isEmpty();
        int before = counter.grants.get();
        reads.instanceGrants(run, Set.of(R2BaselineFixture.ROLE_A), Set.of(), Map.of(R2BaselineFixture.TYPE_T1, 2L));
        reads.scopeGrants(run, Set.of(R2BaselineFixture.ROLE_A), Map.of());
        assertThat(counter.grants.get()).isEqualTo(before);
    }

    @Test
    void should_roundTripExistingSnapshotPayloadAndServeHotReads_whenUsingRedis() {
        long tenant = R2BaselineFixture.TENANT;
        long role = R2BaselineFixture.ROLE_A;
        cache.evict(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, tenant, role);
        List<GrantFact> cold = reads.listGrants(run(tenant, ListGrantRead.ROLE_SNAPSHOT), Set.of(role));
        assertThat(cold).isNotEmpty();
        List<RolePermEntry> payload = cache.get(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, tenant, role);
        assertThat(payload).isNotEmpty().allMatch(value -> value instanceof RolePermEntry);
        assertThat(payload.stream().map(GrantFact::from).toList()).isEqualTo(cold);
        int before = counter.grants.get();
        assertThat(reads.listGrants(run(tenant, ListGrantRead.ROLE_SNAPSHOT), Set.of(role))).isEqualTo(cold);
        assertThat(counter.grants.get()).isEqualTo(before);
        assertThat(reads.listGrants(run(tenant, ListGrantRead.DATABASE), Set.of(role))).isEqualTo(cold);
        assertThat(counter.grants.get()).isEqualTo(before + 1);
    }

    @Test
    void should_resolveExactResourceKeysAndDescriptions_whenReadingRealRows() {
        ResourceResolveRequest valid = new ResourceResolveRequest(R2BaselineFixture.TYPE_T1_CODE,
            R2BaselineFixture.CODE_R1, null, null);
        ResourceResolveRequest wrongType = new ResourceResolveRequest(R2BaselineFixture.TYPE_T2_CODE,
            R2BaselineFixture.CODE_R1, null, null);
        ResourceResolveRequest wrongCodeType = new ResourceResolveRequest(R2BaselineFixture.TYPE_T1_CODE,
            R2BaselineFixture.CODE_R1, "other", null);
        RunState run = run(R2BaselineFixture.TENANT, ListGrantRead.DATABASE);
        assertThat(reads.resolveResources(run, List.of(valid, wrongType, wrongCodeType)))
            .containsOnlyKeys(valid.toKey()).containsEntry(valid.toKey(), R2BaselineFixture.RES_R1);
        assertThat(reads.resourceDescriptions(run, OutputSpec.full(), Set.of(R2BaselineFixture.RES_R1)))
            .containsKey(R2BaselineFixture.RES_R1);
        assertThat(reads.roleDescriptions(run, OutputSpec.full(), Set.of(R2BaselineFixture.ROLE_A)))
            .containsKey(R2BaselineFixture.ROLE_A);
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class SqlCounter implements Interceptor {
        final AtomicInteger operations = new AtomicInteger();
        final AtomicInteger grants = new AtomicInteger();
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            String sql = ((StatementHandler) invocation.getTarget()).getBoundSql().getSql().toLowerCase(java.util.Locale.ROOT);
            if (sql.contains("from operation_permission")) operations.incrementAndGet();
            if (sql.contains("from role_resource_permission")) grants.incrementAndGet();
            return invocation.proceed();
        }
    }
}
