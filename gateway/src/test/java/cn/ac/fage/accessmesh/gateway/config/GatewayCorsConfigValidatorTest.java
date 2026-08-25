package cn.ac.fage.accessmesh.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GatewayCorsConfigValidator 启动校验语义测试（T-GW-007）。
 * <p>
 * 固化三条规则：空/未配置=允许（CORS 禁用，同源部署终态）；
 * 通配 origin + allow-credentials=true = 启动 fail-fast（含 Nacos 远端覆盖后的最终值语义——
 * 校验对象是 GlobalCorsProperties 绑定结果）；明确列表 = 通过。
 * </p>
 */
class GatewayCorsConfigValidatorTest {

    private GlobalCorsProperties propsWith(CorsConfiguration cfg) {
        GlobalCorsProperties props = new GlobalCorsProperties();
        if (cfg != null) {
            props.getCorsConfigurations().put("/**", cfg);
        }
        return props;
    }

    private CorsConfiguration cors(List<String> patterns, Boolean credentials) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(patterns);
        cfg.setAllowCredentials(credentials);
        return cfg;
    }

    @Test
    @DisplayName("明确 origin 列表 + credentials=true → 通过")
    void explicitOriginListPasses() {
        GatewayCorsConfigValidator validator =
            new GatewayCorsConfigValidator(propsWith(cors(List.of("http://localhost:5173"), true)));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("未配置 [/**] CORS 项 → 允许（同源部署，未配置即禁用）")
    void missingCorsConfigurationPasses() {
        GatewayCorsConfigValidator validator = new GatewayCorsConfigValidator(propsWith(null));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空 origin 列表（显式置空） → 允许（CORS 禁用终态）")
    void emptyOriginListPassesAsDisabled() {
        GatewayCorsConfigValidator validator =
            new GatewayCorsConfigValidator(propsWith(cors(List.of(), true)));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("含空白元素列表 → 过滤后为空允许（兼容空串绑定为 [\"\"] 的形态）")
    void blankOnlyElementsTreatedAsEmpty() {
        GatewayCorsConfigValidator validator =
            new GatewayCorsConfigValidator(propsWith(cors(List.of(""), null)));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("通配 \"*\" + credentials=true → 启动 fail-fast（T-GW-007 核心拒绝项）")
    void wildcardOriginWithCredentialsFailsFast() {
        GatewayCorsConfigValidator validator =
            new GatewayCorsConfigValidator(propsWith(cors(List.of("*"), true)));
        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("禁止通配 origin");
    }

    @Test
    @DisplayName("子域通配 \"https://*.example.com\" + credentials=true → 启动 fail-fast（任意含 * 均拦）")
    void subdomainWildcardWithCredentialsFailsFast() {
        GatewayCorsConfigValidator validator =
            new GatewayCorsConfigValidator(propsWith(cors(List.of("http://localhost:5173", "https://*.example.com"), true)));
        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("禁止通配 origin");
    }

    @Test
    @DisplayName("通配 \"*\" + credentials 未开启 → 允许（校验仅约束凭证模式组合）")
    void wildcardWithoutCredentialsPasses() {
        GatewayCorsConfigValidator validator =
            new GatewayCorsConfigValidator(propsWith(cors(List.of("*"), null)));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allowed-origins(exact) 含 \"*\" + credentials=true → 启动 fail-fast（评审收口 D-2：兄弟键同样拦）")
    void exactOriginsWildcardWithCredentialsFailsFast() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("*"));
        cfg.setAllowCredentials(true);
        GatewayCorsConfigValidator validator = new GatewayCorsConfigValidator(propsWith(cfg));
        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("禁止通配 origin");
    }

    @Test
    @DisplayName("allowed-origins(exact) 明确列表 + credentials=true → 通过")
    void exactOriginsExplicitListWithCredentialsPasses() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("http://localhost:8848"));
        cfg.setAllowedOrigins(List.of("http://localhost:8848"));
        cfg.setAllowCredentials(true);
        GatewayCorsConfigValidator validator = new GatewayCorsConfigValidator(propsWith(cfg));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }
}
