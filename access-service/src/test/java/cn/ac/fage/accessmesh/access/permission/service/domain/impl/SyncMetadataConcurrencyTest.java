package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.access.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService.ApplyVersionResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SyncMetadataDomainServiceImpl#applyVersion} 的 PostgreSQL 并发集成测试。
 * <p>
 * 验证版本比较已下沉到 PG ON CONFLICT DO UPDATE ... WHERE 子句：
 * <ul>
 *   <li>旧版本写入返回 STALE，metadata 保持现存值；</li>
 *   <li>新版本写入返回 APPLIED，metadata 更新；</li>
 *   <li>并发 (新+旧) 写入：恰好一次 APPLIED，最终态为新版本，避免 select-then-upsert
 *       的 lost update。</li>
 * </ul>
 * 测试在 Docker 可用时运行；CI 无 Docker 时由 Testcontainers 自动跳过
 * （{@code disabledWithoutDocker = true}）。
 * </p>
 *
 * @author AccessMesh Team
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class SyncMetadataConcurrencyTest {

    /** PostgreSQL 测试容器 */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("perm_sync_meta_test")
            .withUsername("perm")
            .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐：主配置 ${REDIS_PASSWORD:} 解析为空串而非 null，
     * Redisson 对空串仍发 AUTH，无密码 Redis 会拒绝（ERR AUTH called without any password） */
    private static final String REDIS_TEST_PASSWORD = "accessmesh-test";

    /** Redis 测试容器（Spring 上下文需要） */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }

    private static final Long TENANT_ID = 1L;
    private static final String ENTITY_KIND = "ABSTRACT_USER";
    private static final String SOURCE_SERVICE = "example-service";
    private static final String SCOPE_KEY = "subjectTypeCode=USER";
    private static final String SCOPE_KEY_HASH = "scope-hash-conc";
    private static final String BUSINESS_KEY = "subjectTypeCode=USER&subjectExternalId=conc-1";
    private static final String BUSINESS_KEY_HASH = "biz-hash-conc";
    private static final String SYNC_KEY = SOURCE_SERVICE + "|" + ENTITY_KIND + "|" + BUSINESS_KEY;
    private static final String SYNC_KEY_HASH = "sync-hash-conc";

    @Autowired
    private SyncMetadataMapper syncMetadataMapper;

    @Autowired
    private SyncMetadataDomainServiceImpl service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static boolean schemaInitialized = false;

    @BeforeAll
    static void resetSchemaFlag() {
        schemaInitialized = false;
    }

    @BeforeEach
    void initSchemaAndCleanup() {
        if (!schemaInitialized) {
            // 仅创建 sync_metadata 与必要索引，避免拉起完整 schema
            jdbcTemplate.execute("DROP TABLE IF EXISTS sync_metadata");
            jdbcTemplate.execute("""
                    CREATE TABLE sync_metadata (
                        id                    BIGSERIAL PRIMARY KEY,
                        tenant_id             BIGINT NOT NULL,
                        entity_kind           VARCHAR(64) NOT NULL,
                        source_service        VARCHAR(128) NOT NULL,
                        scope_key             TEXT NOT NULL,
                        scope_key_hash        CHAR(64) NOT NULL,
                        business_key          TEXT NOT NULL,
                        business_key_hash     CHAR(64) NOT NULL,
                        sync_key              TEXT NOT NULL,
                        sync_key_hash         CHAR(64) NOT NULL,
                        target_id             BIGINT,
                        target_status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        last_sync_occurred_at TIMESTAMPTZ NOT NULL,
                        last_sync_sequence_no BIGINT NOT NULL,
                        extra                 JSONB DEFAULT '{}',
                        created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
                        deleted_at            TIMESTAMPTZ,
                        delete_flag           BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_sync_metadata_key ON sync_metadata "
                    + "(tenant_id, entity_kind, source_service, scope_key_hash, business_key_hash) "
                    + "WHERE delete_flag = 0");
            schemaInitialized = true;
        }
        jdbcTemplate.execute("TRUNCATE TABLE sync_metadata RESTART IDENTITY");
    }

    /**
     * 用例 1：(T1, seq=10) 已存在；写入 (T1, seq=5) → STALE，metadata 保持 seq=10。
     */
    @Test
    void applyVersion_olderSequenceNo_shouldKeepExistingRecord() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 12, 0, 0);
        ApplyVersionResult first = service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t1, 10L);
        assertThat(first).isEqualTo(ApplyVersionResult.APPLIED);

        ApplyVersionResult older = service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t1, 5L);
        assertThat(older).isEqualTo(ApplyVersionResult.STALE);

        SyncMetadata current = syncMetadataMapper.selectByBusinessKey(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE, SCOPE_KEY_HASH, BUSINESS_KEY_HASH);
        assertThat(current).isNotNull();
        assertThat(current.getLastSyncOccurredAt()).isEqualTo(t1);
        assertThat(current.getLastSyncSequenceNo()).isEqualTo(10L);
    }

    /**
     * 用例 2：(T1, seq=10) 已存在；写入 (T2>T1, seq=1) → APPLIED，metadata 更新为 (T2, 1)。
     */
    @Test
    void applyVersion_newerOccurredAt_shouldOverwrite() {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 12, 0, 0);
        service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t1, 10L);

        LocalDateTime t2 = t1.plusSeconds(1);
        ApplyVersionResult result = service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t2, 1L);
        assertThat(result).isEqualTo(ApplyVersionResult.APPLIED);

        SyncMetadata current = syncMetadataMapper.selectByBusinessKey(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE, SCOPE_KEY_HASH, BUSINESS_KEY_HASH);
        assertThat(current.getLastSyncOccurredAt()).isEqualTo(t2);
        assertThat(current.getLastSyncSequenceNo()).isEqualTo(1L);
    }

    /**
     * 用例 3：并发 (T2, seq=1) 与 (T1, seq=99)（旧），多次重复触发以提高并发覆盖度。
     * <p>
     * 期望：至少一次 APPLIED，两个写均有裁决，最终态固定为 (T2, seq=1)。
     * 即使两个调用 select 时同步看到 “无现存记录” 也不会发生 lost update：
     * 第二个 INSERT 命中唯一索引冲突进入 ON CONFLICT，受 WHERE 子句保护仅当严格新于时才更新。
     * 旧版本先落库时两个写均合法返回 APPLIED（旧先插入、新覆盖），属正确交错。
     * </p>
     */
    @Test
    void applyVersion_concurrentNewAndOld_shouldSettleOnNewerVersion() throws Exception {
        LocalDateTime t1 = LocalDateTime.of(2026, 6, 13, 12, 0, 0);
        LocalDateTime t2 = t1.plusSeconds(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                jdbcTemplate.execute("TRUNCATE TABLE sync_metadata RESTART IDENTITY");

                Callable<ApplyVersionResult> newer = () -> service.applyVersion(
                        TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                        SCOPE_KEY_HASH, SCOPE_KEY,
                        BUSINESS_KEY_HASH, BUSINESS_KEY,
                        SYNC_KEY, SYNC_KEY_HASH,
                        t2, 1L);
                Callable<ApplyVersionResult> older = () -> service.applyVersion(
                        TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                        SCOPE_KEY_HASH, SCOPE_KEY,
                        BUSINESS_KEY_HASH, BUSINESS_KEY,
                        SYNC_KEY, SYNC_KEY_HASH,
                        t1, 99L);

                List<Future<ApplyVersionResult>> futures = new ArrayList<>();
                futures.add(pool.submit(newer));
                futures.add(pool.submit(older));

                List<ApplyVersionResult> results = new ArrayList<>();
                for (Future<ApplyVersionResult> f : futures) {
                    results.add(f.get(10, TimeUnit.SECONDS));
                }

                long appliedCount = results.stream().filter(r -> r == ApplyVersionResult.APPLIED).count();
                long staleCount = results.stream().filter(r -> r == ApplyVersionResult.STALE).count();
                // 2026-08-22 用户决策：旧版本先落库的合法交错下两个写均返回 APPLIED
                // （旧先 INSERT 成功、新经 ON CONFLICT 覆盖），"恰好一次 APPLIED"不可能跨交错成立；
                // 确定不变量为：至少一次 APPLIED、两个写均有裁决、最终态为新版本（防丢失更新）。
                assertThat(appliedCount)
                        .as("round %d: at least one APPLIED, results=%s", round, results)
                        .isGreaterThanOrEqualTo(1);
                assertThat(appliedCount + staleCount)
                        .as("round %d: every writer got a verdict, results=%s", round, results)
                        .isEqualTo(2);

                SyncMetadata current = syncMetadataMapper.selectByBusinessKey(
                        TENANT_ID, ENTITY_KIND, SOURCE_SERVICE, SCOPE_KEY_HASH, BUSINESS_KEY_HASH);
                assertThat(current).as("round %d", round).isNotNull();
                // 最终态必须是新版本（T2, seq=1），而不是旧版本 (T1, seq=99)
                assertThat(current.getLastSyncOccurredAt())
                        .as("round %d: final occurredAt should be t2", round)
                        .isEqualTo(t2);
                assertThat(current.getLastSyncSequenceNo())
                        .as("round %d: final sequenceNo should be 1", round)
                        .isEqualTo(1L);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 用例 4：相同 occurredAt 时 sequenceNo 严格新于 → APPLIED；相等 → STALE。
     */
    @Test
    void applyVersion_sameOccurredAt_strictlyGreaterSequenceNoOnly() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 13, 12, 0, 0);
        assertThat(service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t, 10L)).isEqualTo(ApplyVersionResult.APPLIED);

        // 相同 occurredAt + 相同 sequenceNo -> STALE
        assertThat(service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t, 10L)).isEqualTo(ApplyVersionResult.STALE);

        // 相同 occurredAt + 严格更大 sequenceNo -> APPLIED
        assertThat(service.applyVersion(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY,
                BUSINESS_KEY_HASH, BUSINESS_KEY,
                SYNC_KEY, SYNC_KEY_HASH,
                t, 11L)).isEqualTo(ApplyVersionResult.APPLIED);

        SyncMetadata current = syncMetadataMapper.selectByBusinessKey(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE, SCOPE_KEY_HASH, BUSINESS_KEY_HASH);
        assertThat(current.getLastSyncSequenceNo()).isEqualTo(11L);
    }

    /**
     * 用例（评审修复）：只读版本预判须与库侧微秒化语义一致——亚微秒尾差经 PG TIMESTAMPTZ
     * 落库被舍入到微秒，两条同纳秒原值事件（.123456600，seq 1/2）库内同为 .123457：
     * 预判不得把第二条（更高序号）误判为旧；预判结论须与 applyVersion 实际结果全程等价。
     */
    @Test
    void isNewerVersion_shouldMatchDatabaseMicrosecondRounding() {
        LocalDateTime raw = LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_600);

        // 第一条落库：APPLIED，且读回为微秒对齐（亚微秒 .6 进位为 .123457）
        assertThat(service.applyVersion(TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY, BUSINESS_KEY_HASH, BUSINESS_KEY, SYNC_KEY, SYNC_KEY_HASH, raw, 1L))
                .isEqualTo(ApplyVersionResult.APPLIED);
        SyncMetadata stored = syncMetadataMapper.selectByBusinessKey(
                TENANT_ID, ENTITY_KIND, SOURCE_SERVICE, SCOPE_KEY_HASH, BUSINESS_KEY_HASH);
        assertThat(stored.getLastSyncOccurredAt().getNano() % 1_000).isZero();
        assertThat(stored.getLastSyncOccurredAt().getNano()).isEqualTo(123_457_000);

        // 第二条（同原值、更高序号）：预判=新，且与 applyVersion 实际结果等价（原实现此处误判 STALE）
        assertThat(service.isNewerVersion(stored, raw, 2L)).isTrue();
        assertThat(service.applyVersion(TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY, BUSINESS_KEY_HASH, BUSINESS_KEY, SYNC_KEY, SYNC_KEY_HASH, raw, 2L))
                .isEqualTo(ApplyVersionResult.APPLIED);

        // 对照（亚微秒更小 .123456499 → 微秒化 .123456 更旧）：预判=旧，与 applyVersion 等价
        LocalDateTime slightlyOlderRaw = LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_499);
        assertThat(service.isNewerVersion(stored, slightlyOlderRaw, 999L)).isFalse();
        assertThat(service.applyVersion(TENANT_ID, ENTITY_KIND, SOURCE_SERVICE,
                SCOPE_KEY_HASH, SCOPE_KEY, BUSINESS_KEY_HASH, BUSINESS_KEY, SYNC_KEY, SYNC_KEY_HASH,
                slightlyOlderRaw, 999L))
                .isEqualTo(ApplyVersionResult.STALE);
    }
}
