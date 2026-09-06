package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/**
 * T-PERM-024 🔧3 确认型验收：system_config.config_value JSONB 列 ↔ entity String 映射
 * （{@code JsonbStringTypeHandler} + PostgreSQL 驱动）在真实 PostgreSQL 下的行为锁定。
 * <p>
 * 语义结论（本测试为回归锁）：写入的 JSON 字符串经 JSONB 存储后读出为 **DB 规范化后的 JSON 文本**
 * ——语义等价（解析后树相等，含中文/嵌套/数组/空格变体），但**不保证字节级回显**（JSONB 会规范化
 * 空白与键序）；已读出的规范化文本再次写入后读出保持稳定（幂等）。前端按字符串提交/展示、
 * 展示值可直接再提交，无静默截断/转义问题。
 * </p>
 * <p>
 * Docker 不可用时由 Testcontainers 自动跳过（与既有 PG 测试一致）。
 * </p>
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
class SystemConfigJsonbPgIT {

    private static final Long TENANT = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, SystemConfigJsonbPgIT.class);
    }

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    private SystemConfig insertConfig(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setTenantId(TENANT);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription("T-PERM-024 JSONB roundtrip");
        config.setIsSystem(false);
        LocalDateTime now = LocalDateTime.now();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        config.setDeleteFlag(0L);
        systemConfigMapper.insert(config);
        return config;
    }

    @Test
    @DisplayName("JSONB roundtrip 语义等价：中文/嵌套/数组/空格变体解析后树相等")
    void shouldRoundtripJsonValuesWithSemanticEquality() throws Exception {
        String compact = "{\"mode\":\"DOMAIN_UNIQUE\",\"threshold\":3}";
        String chinese = "{\"名称\":\"权限中心\",\"模块\":[\"授权\",\"查询\"],\"嵌套\":{\"层级\":2}}";
        String oddWhitespace = "{ \"a\" : 1,  \"b\" : [ ] ,\"c\":\"x y z\"}";

        for (String value : List.of(compact, chinese, oddWhitespace)) {
            String key = "admin.jsonb.roundtrip." + MAPPER.readTree(value).hashCode();
            insertConfig(key, value);

            SystemConfig read = systemConfigMapper.selectByConfigKey(TENANT, key);
            assertThat(read).as("insert 后可按 configKey 读回").isNotNull();
            String readValue = read.getConfigValue();
            assertThat(readValue).as("读出为合法 JSON 文本").isNotBlank();

            JsonNode expected = MAPPER.readTree(value);
            JsonNode actual = MAPPER.readTree(readValue);
            assertThat(actual).as("语义等价（解析树相等），原文=%s 读出=%s", value, readValue).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("JSONB 规范化幂等：读出的规范化文本再次写入后读出字节稳定")
    void shouldNormalizeIdempotentlyOnRewrite() throws Exception {
        String odd = "{ \"rewritten\" :  true, \"n\": [1, 2] }";
        String key = "admin.jsonb.rewrite";
        insertConfig(key, odd);

        SystemConfig first = systemConfigMapper.selectByConfigKey(TENANT, key);
        String normalized = first.getConfigValue();
        assertThat(MAPPER.readTree(normalized)).isEqualTo(MAPPER.readTree(odd));

        // 用读出的规范化文本覆盖写回（模拟前端把展示值原样再提交）
        first.setConfigValue(normalized);
        first.setUpdatedAt(LocalDateTime.now());
        systemConfigMapper.update(first);

        SystemConfig second = systemConfigMapper.selectByConfigKey(TENANT, key);
        assertThat(second.getConfigValue())
            .as("规范化文本再写入后字节稳定，前端展示值可直接再提交")
            .isEqualTo(normalized);
    }
}
