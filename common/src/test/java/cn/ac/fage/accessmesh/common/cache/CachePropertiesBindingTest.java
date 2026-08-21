package cn.ac.fage.accessmesh.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CacheProperties 绑定契约测试（T-ACCESS-008 复评 P2 修复：统一配置能力闭环）。
 * <p>
 * Java 属性名为 {@code defaultConfig}，Spring Boot 宽松绑定的规范路径是
 * {@code accessmesh.cache.default-config.*}（早期文档误写为 {@code default.*}，
 * 该路径不会绑定到任何字段）。本测试锁定：default-config 与 catalogs 覆盖真实生效，
 * 且 {@code default.*} 不产生绑定（防止文档回归）。
 * </p>
 */
class CachePropertiesBindingTest {

    private CacheCatalogEntry<String> catalog() {
        return CacheCatalogEntry.<String>builder()
            .code("test:binding")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<String>() {})
            .build();
    }

    private CacheProperties bind(Map<String, Object> source) {
        Binder binder = new Binder(ConfigurationPropertySources.from(
            new MapPropertySource("test", source)));
        // 无任何可绑定键时 BindResult 为空（如仅存在不认识的 default.* 前缀），
        // 此时返回默认实例——等价于配置完全不生效
        return binder.bind("accessmesh.cache", Bindable.of(CacheProperties.class))
            .orElseGet(CacheProperties::new);
    }

    @Test
    void defaultConfigPrefix_shouldBindGlobalDefaults() {
        CacheProperties properties = bind(Map.of(
            "accessmesh.cache.default-config.l1-ttl", "15s",
            "accessmesh.cache.default-config.l2-ttl", "5m"));

        assertThat(properties.getDefaultConfig().getL1Ttl()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getDefaultConfig().getL2Ttl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void defaultConfigPrefix_shouldDriveEffectiveTtlFallback() {
        CacheProperties properties = bind(Map.of(
            "accessmesh.cache.default-config.l1-ttl", "15s"));

        // catalog 无 L1 默认（L2_ONLY）时走全局默认——统一配置闭环
        assertThat(properties.getEffectiveL1Ttl(catalog().getCode(), null))
            .isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void catalogOverridePrefix_shouldBindQuotedCodeKey() {
        CacheProperties properties = bind(Map.of(
            "accessmesh.cache.catalogs.[test:binding].l2-ttl", "8s"));

        assertThat(properties.getCatalogs()).containsKey("test:binding");
        assertThat(properties.getEffectiveL2Ttl("test:binding", Duration.ofSeconds(10)))
            .isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void legacyDefaultPrefix_shouldNotBindAnything() {
        // 文档曾误写 accessmesh.cache.default.*：该路径不绑定 defaultConfig（防文档回归）
        CacheProperties properties = bind(Map.of(
            "accessmesh.cache.default.l1-ttl", "15s"));

        assertThat(properties.getDefaultConfig().getL1Ttl())
            .isEqualTo(Duration.ofMinutes(10)); // 代码默认值未被覆盖
    }
}
