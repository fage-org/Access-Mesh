package cn.ac.fage.accessmesh.gateway.config;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gateway 缓存安全边界启动校验测试（T-ACCESS-008）。
 * <p>
 * 快照 L1 有效 TTL≤15s、加载截止≤5s；超限启动失败。
 * </p>
 */
class GatewayCacheBoundaryValidatorTest {

    private CacheProperties cacheProperties() {
        return new CacheProperties();
    }

    private GatewayProperties gatewayProperties(Duration deadline) {
        GatewayProperties props = new GatewayProperties();
        props.getPermission().setSnapshotLoadDeadline(deadline);
        return props;
    }

    @Test
    void shouldPassWithDefaults() {
        GatewayCacheBoundaryValidator validator =
            new GatewayCacheBoundaryValidator(cacheProperties(), gatewayProperties(Duration.ofSeconds(5)));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenL1TtlOverrideExceeds15s() {
        CacheProperties properties = new CacheProperties();
        CacheProperties.CatalogOverride override = new CacheProperties.CatalogOverride();
        override.setL1Ttl(Duration.ofSeconds(16));
        properties.getCatalogs().put("gw:interface-snapshot", override);

        GatewayCacheBoundaryValidator validator =
            new GatewayCacheBoundaryValidator(properties, gatewayProperties(Duration.ofSeconds(5)));

        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("L1 TTL");
    }

    @Test
    void shouldFailWhenDeadlineExceeds5s() {
        GatewayCacheBoundaryValidator validator =
            new GatewayCacheBoundaryValidator(cacheProperties(), gatewayProperties(Duration.ofSeconds(6)));

        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("snapshot-load-deadline");
    }

    @Test
    void shouldFailWhenDeadlineZero() {
        GatewayCacheBoundaryValidator validator =
            new GatewayCacheBoundaryValidator(cacheProperties(), gatewayProperties(Duration.ZERO));

        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("snapshot-load-deadline");
    }
}
