package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.access.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService.ApplyVersionResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SyncMetadataConcurrencyTest {

    /** PostgreSQL 测试容器 */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("perm_sync_meta_test")
            .withUsername("perm")
            .withPassword("perm");

    /** Redis 测试容器（Spring 上下文需要） */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private static final Long TENANT_ID = 1L;
    private static final String ENTITY_KIND = "ABSTRACT_USER";
    private static final String SOURCE_SERVICE = "admin-service";
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
     * 期望：每轮恰好一次 APPLIED + 一次 STALE，最终态固定为 (T2, seq=1)。
     * 即使两个调用 select 时同步看到 “无现存记录” 也不会发生 lost update：
     * 第二个 INSERT 命中唯一索引冲突进入 ON CONFLICT，受 WHERE 子句保护仅当严格新于时才更新。
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
                assertThat(appliedCount)
                        .as("round %d: exactly one APPLIED, results=%s", round, results)
                        .isEqualTo(1);
                assertThat(staleCount)
                        .as("round %d: exactly one STALE, results=%s", round, results)
                        .isEqualTo(1);

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
}
