package cn.ac.fage.accessmesh.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ForwardHeadersStrategyGuard 启动校验语义测试（T-GW-008 codex 外评 P1 处置）。
 * <p>
 * 固化三条规则：缺省/none（Spring Boot 默认）= 放行（socket 对端=唯一可信 IP 来源）；
 * framework = 启动 fail-fast（ForwardedHeaderTransformer 在全部 WebFilter 之前把外部
 * X-Forwarded-For 解析进 remoteAddress，清洗链来不及参与——T-GW-008 收口前提被打破）；
 * native = 同拒（WebFlux 下无 transformer 不污染，拒绝防语义混淆）。
 * 属性值大小写不敏感（对齐 Spring Boot relaxed binding）。
 * </p>
 */
class ForwardHeadersStrategyGuardTest {

    private ForwardHeadersStrategyGuard guardWith(String strategy) {
        MockEnvironment env = new MockEnvironment();
        if (strategy != null) {
            env.setProperty(ForwardHeadersStrategyGuard.PROPERTY, strategy);
        }
        return new ForwardHeadersStrategyGuard(env);
    }

    @Test
    @DisplayName("缺省（未设置） → 放行")
    void missingPropertyPasses() {
        assertThatCode(guardWith(null)::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("none / NONE / 带空白 → 放行（Spring Boot 默认值）")
    void nonePasses() {
        assertThatCode(guardWith("none")::afterPropertiesSet).doesNotThrowAnyException();
        assertThatCode(guardWith("NONE")::afterPropertiesSet).doesNotThrowAnyException();
        assertThatCode(guardWith(" none ")::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("framework → 启动 fail-fast，拒绝消息含处置指引")
    void frameworkFailsStartup() {
        assertThatThrownBy(guardWith("framework")::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("server.forward-headers-strategy=framework")
            .hasMessageContaining("T-GW-008")
            .hasMessageContaining("remoteAddress");
    }

    @Test
    @DisplayName("framework 大写变体 → 同样拒绝（relaxed binding 同义）")
    void frameworkVariantFailsStartup() {
        assertThatThrownBy(guardWith("FRAMEWORK")::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("native → 同样拒绝（防语义混淆）")
    void nativeFailsStartup() {
        assertThatThrownBy(guardWith("native")::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("防语义混淆");
    }
}
