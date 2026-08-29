package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-PERM-026 回归锁（真实 PostgreSQL，权威 DDL 原样执行）：
 * <ol>
 * <li><b>P0 列名修复</b>：{@code BizDomainMapper.selectByCode/selectByCodes} 原写成
 *     {@code domain_code} 列（DDL 列名为 {@code code}），真库必报 42703——该查询是
 *     {@code TypeResolutionService.resolveDomainId} 的底层（domain-config save/list/detail、
 *     DomainClassifyService、checkCanGrant 批量解析共同消费），单测 mock mapper 掩盖了它。
 *     本测试在真实 PG 上走通解析链路。</li>
 * <li><b>extra JSONB↔String 映射确认（🔧6）</b>：{@code domain_config.extra} JSONB 列 ↔
 *     entity String（{@code JsonbStringTypeHandler}）语义等价回读（SystemConfigJsonbPgIT 同范式）。</li>
 * <li><b>删除保护引用检查查询</b>：{@code selectValidByDomainIds} 批量引用检查与软删后清空。</li>
 * <li><b>list 过滤分页查询</b>：{@code countByCondition/selectPageByCondition} keyword LIKE 与
 *     ORDER BY code, id 分页。</li>
 * </ol>
 * Docker 不可用时由 Testcontainers 自动跳过（与既有 PG 测试一致）。
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
class BizDomainConfigPgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("biz_domain_config_test")
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
        // 原样执行权威 DDL + 种子数据
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private BizDomainMapper bizDomainMapper;

    @Autowired
    private DomainConfigMapper domainConfigMapper;

    @Autowired
    private TypeResolutionService typeResolutionService;

    @Autowired
    private DomainClassifyService domainClassifyService;

    private BizDomain insertDomain(String code, boolean global) {
        BizDomain domain = new BizDomain();
        domain.setTenantId(TENANT);
        domain.setCode(code);
        domain.setName(code + " 域");
        domain.setGlobal(global);
        domain.setDescription("T-PERM-026 PgIT");
        LocalDateTime now = LocalDateTime.now();
        domain.setCreatedAt(now);
        domain.setUpdatedAt(now);
        domain.setDeleteFlag(0L);
        bizDomainMapper.insert(domain);
        return domain;
    }

    @Test
    @DisplayName("resolveDomainId/selectByCodes 在真实 PG 走通（P0 列名修复回归锁）")
    void shouldResolveDomainByCodeOnRealPostgres() {
        BizDomain hr = insertDomain("PGITRESHR", false);
        insertDomain("PGITRESORDER", false);

        // resolveDomainId 是 domain-config save/detail 与 DomainClassifyService 的底层解析链路
        assertThat(typeResolutionService.resolveDomainId(TENANT, "PGITRESHR")).isEqualTo(hr.getId());
        assertThat(typeResolutionService.resolveDomainId(TENANT, "PGITRESNOPE")).isNull();

        List<BizDomain> batch = bizDomainMapper.selectByCodes(TENANT,
            Set.of("PGITRESHR", "PGITRESORDER", "PGITRESNOPE"));
        assertThat(batch).extracting(BizDomain::getCode)
            .containsExactlyInAnyOrder("PGITRESHR", "PGITRESORDER");
    }

    @Test
    @DisplayName("extra JSONB roundtrip 语义等价 + 引用检查查询与软删清空")
    void shouldRoundtripExtraAndCheckReferenceOnRealPostgres() throws Exception {
        BizDomain domain = insertDomain("PGIT_JSON", false);
        String compact = "{\"resourceTypeCodes\":[\"ORG\",\"用户\"],\"nested\":{\"k\":3}}";

        DomainConfig config = new DomainConfig();
        config.setTenantId(TENANT);
        config.setBizDomainId(domain.getId());
        config.setConfigType("CLASSIFY");
        config.setExtra(compact);
        LocalDateTime now = LocalDateTime.now();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        config.setDeleteFlag(0L);
        domainConfigMapper.insert(config);

        // JSONB 读出为 DB 规范化 JSON 文本：语义等价（解析树相等），不保证字节回显
        DomainConfig read = domainConfigMapper.selectValidByTypeString(TENANT, domain.getId(), "CLASSIFY");
        assertThat(read).isNotNull();
        assertThat(MAPPER.readTree(read.getExtra())).isEqualTo(MAPPER.readTree(compact));

        // 子表列表查询与删除保护引用检查查询
        assertThat(domainConfigMapper.selectByTenantAndDomainId(TENANT, domain.getId())).hasSize(1);
        assertThat(domainConfigMapper.selectValidByDomainIds(TENANT, Set.of(domain.getId()))).hasSize(1);

        // 跨层字段名契约锁（P1 回归）：CLASSIFY extra 的资源类型字段为 resourceTypeCodes——
        // DomainClassifyService.parseResourceTypeCodes 只读取该字段，typeCodes 等变体会静默
        // 保存成功但分类结果为空（save 仅校验 JSON 语法不校验 schema）
        assertThat(domainClassifyService.getClassifiedTypeCodes(TENANT, "PGIT_JSON"))
            .contains("ORG", "用户");

        domainConfigMapper.softDeleteBatch(TENANT, List.of(read.getId()), LocalDateTime.now());
        assertThat(domainConfigMapper.selectValidByDomainIds(TENANT, Set.of(domain.getId()))).isEmpty();
        assertThat(domainConfigMapper.selectValidByTypeString(TENANT, domain.getId(), "CLASSIFY")).isNull();
    }

    @Test
    @DisplayName("写链路真库锁：insert 落库（global=false）+ update 空串清空落库 / null 列忽略")
    void shouldPersistCreateAndUpdateSemanticsOnRealPostgres() {
        BizDomain domain = insertDomain("PGITWRI", false);

        // create 落库：global 列 NOT NULL DEFAULT false，显式 setGlobal(false) 必须真实落库
        BizDomain inserted = bizDomainMapper.selectByCode(TENANT, "PGITWRI");
        assertThat(inserted).isNotNull();
        assertThat(inserted.getGlobal()).isFalse();
        assertThat(inserted.getName()).isEqualTo("PGITWRI 域");

        // update：description 空串=显式清空（空串列被写入）
        inserted.setDescription("");
        inserted.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(inserted);
        assertThat(bizDomainMapper.selectByCode(TENANT, "PGITWRI").getDescription()).isEmpty();

        // update：字段 null=列被忽略不置 NULL（MyBatis-Flex 默认 ignore-nulls；name 不更新）
        inserted.setDescription(null);
        inserted.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(inserted);
        assertThat(bizDomainMapper.selectByCode(TENANT, "PGITWRI").getDescription()).isEmpty();
    }

    @Test
    @DisplayName("list keyword 过滤与 ORDER BY code, id 分页")
    void shouldFilterAndPageBizDomainsOnRealPostgres() {
        insertDomain("PGITPAGEAALPHA", false);
        insertDomain("PGITPAGEBBETA", false);
        insertDomain("PGITPAGEAGLOBAL", true);

        assertThat(bizDomainMapper.countByCondition(TENANT, null)).isGreaterThanOrEqualTo(3);
        // LIKE 大小写敏感，匹配 code（PGITPAGEA 前缀两条）
        assertThat(bizDomainMapper.countByCondition(TENANT, "PGITPAGEA")).isEqualTo(2);

        List<BizDomain> page = bizDomainMapper.selectPageByCondition(TENANT, "PGITPAGE", 2, 0);
        assertThat(page).extracting(BizDomain::getCode)
            .containsExactly("PGITPAGEAALPHA", "PGITPAGEAGLOBAL");

        List<BizDomain> page2 = bizDomainMapper.selectPageByCondition(TENANT, "PGITPAGE", 2, 2);
        assertThat(page2).extracting(BizDomain::getCode)
            .containsExactly("PGITPAGEBBETA");
    }
}
