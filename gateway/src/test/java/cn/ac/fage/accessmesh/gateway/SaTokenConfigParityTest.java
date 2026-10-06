package cn.ac.fage.accessmesh.gateway;

import cn.dev33.satoken.config.SaTokenConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/** 实际 YAML 绑定值对照；显式列举公共/单侧键，避免新增配置静默漏检。 */
class SaTokenConfigParityTest {
    private static final Set<String> COMMON = Set.of("token-name", "token-prefix", "token-style",
        "timeout", "active-timeout", "is-concurrent", "is-share", "is-read-cookie", "is-log");

    @Test
    void effectiveSessionValuesMatchAndSideOnlyKeysAreRegistered() throws IOException {
        verify(load("gateway"), load("access-service"));
    }

    @Test
    void changedSharedValueAndUnregisteredKeyAreDetected() throws IOException {
        var gateway = load("gateway");
        var access = load("access-service");
        gateway.getPropertySources().addFirst(new MapPropertySource("changed",
            Map.of("sa-token.timeout", "17")));
        assertThatThrownBy(() -> verify(gateway, access)).isInstanceOf(AssertionError.class);
        gateway.getPropertySources().remove("changed");
        gateway.getPropertySources().addFirst(new MapPropertySource("added",
            Map.of("sa-token.new-unregistered-key", "true")));
        assertThatThrownBy(() -> verify(gateway, access)).isInstanceOf(AssertionError.class);
    }

    @Test
    void placeholdersAreComparedAfterResolution() throws IOException {
        var gateway = load("gateway");
        gateway.getPropertySources().addFirst(new MapPropertySource("placeholder",
            Map.of("sa-token.timeout", "${session-test-timeout}", "session-test-timeout", "7200")));
        verify(gateway, load("access-service"));
    }

    private static void verify(StandardEnvironment gateway, StandardEnvironment access) {
        assertThat(keys(gateway)).containsExactlyInAnyOrderElementsOf(with("is-read-header"));
        assertThat(keys(access)).containsExactlyInAnyOrderElementsOf(with("jwt-secret-key"));
        var gw = bind(gateway);
        var svc = bind(access);
        assertThat(values(gw)).isEqualTo(values(svc));
        assertThat(gw.getIsReadHeader()).isTrue();
        assertThat(svc.getIsReadHeader()).isTrue();
    }

    private static Map<String, Object> values(SaTokenConfig c) {
        return Map.of("token-name", c.getTokenName(), "token-prefix", c.getTokenPrefix(),
            "token-style", c.getTokenStyle(), "timeout", c.getTimeout(),
            "active-timeout", c.getActiveTimeout(), "is-concurrent", c.getIsConcurrent(),
            "is-share", c.getIsShare(), "is-read-cookie", c.getIsReadCookie(), "is-log", c.getIsLog());
    }

    private static SaTokenConfig bind(StandardEnvironment environment) {
        return Binder.get(environment).bind("sa-token", Bindable.of(SaTokenConfig.class)).get();
    }

    private static Set<String> with(String sideOnlyKey) {
        var keys = new LinkedHashSet<>(COMMON);
        keys.add(sideOnlyKey);
        return keys;
    }

    private static Set<String> keys(StandardEnvironment env) {
        var result = new LinkedHashSet<String>();
        env.getPropertySources().forEach(source -> {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                Arrays.stream(enumerable.getPropertyNames()).filter(k -> k.startsWith("sa-token."))
                    .map(k -> k.substring("sa-token.".length())).forEach(result::add);
            }
        });
        return result;
    }

    private static StandardEnvironment load(String module) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        if (!root.resolve("gateway").toFile().isDirectory()) root = root.getParent();
        var env = new StandardEnvironment();
        // 不受运行机器的环境变量影响；仅测试仓库发布的配置及其显式测试占位符。
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("test-secret",
            Map.of("JWT_SECRET_KEY", "parity-test-only")));
        for (var source : new YamlPropertySourceLoader().load(module,
                new FileSystemResource(root.resolve(module + "/src/main/resources/application.yml")))) {
            env.getPropertySources().addLast(source);
        }
        return env;
    }
}
