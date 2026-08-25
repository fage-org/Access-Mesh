package cn.ac.fage.accessmesh.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import java.util.Map;

/**
 * Gateway CORS 配置启动校验器（T-GW-007）
 * <p>
 * 校验 {@code spring.cloud.gateway.globalcors} 最终生效值（含 Nacos 远端覆盖后的值），
 * 规则按 2026-08-25 用户口径（生产 nginx 同源代理，CORS 无生产消费场景）：
 * <ul>
 *   <li>origin 列表为空或未配置：允许——CORS 禁用（同源部署终态），跨域请求不加 CORS 头
 *       被浏览器拒绝（fail-closed，不放行任意源），启动 INFO 声明；</li>
 *   <li>origin 列表含通配（任意含 {@code *} 的 pattern）且 {@code allow-credentials=true}：
 *       启动 fail-fast（任意源携带凭证为安全缺陷，含 Nacos 远端旧值回退场景）。</li>
 * </ul>
 * 默认 {@code http://localhost:8848}（前端 dev 实际端口，开发直连调试）恒通过。
 * 注意：yml 键 {@code '[/**]'} 经 Binder 绑定后 map key 为 {@code /**}，故按条目遍历校验
 * 而非依赖单一 key。
 * </p>
 */
@Component
public class GatewayCorsConfigValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(GatewayCorsConfigValidator.class);

    private final GlobalCorsProperties globalCorsProperties;

    public GatewayCorsConfigValidator(GlobalCorsProperties globalCorsProperties) {
        this.globalCorsProperties = globalCorsProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, CorsConfiguration> configs = globalCorsProperties.getCorsConfigurations();
        if (configs == null || configs.isEmpty()) {
            log.info("Gateway CORS not configured: same-origin deployment, cross-origin requests rejected by browser");
            return;
        }
        for (Map.Entry<String, CorsConfiguration> entry : configs.entrySet()) {
            validateEntry(entry.getKey(), entry.getValue());
        }
    }

    private void validateEntry(String pathKey, CorsConfiguration cfg) {
        if (cfg == null) {
            return;
        }
        List<String> rawPatterns = cfg.getAllowedOriginPatterns();
        List<String> patterns = rawPatterns == null ? List.of()
            : rawPatterns.stream().filter(p -> p != null && !p.isBlank()).toList();
        List<String> rawExact = cfg.getAllowedOrigins();
        List<String> exact = rawExact == null ? List.of()
            : rawExact.stream().filter(o -> o != null && !o.isBlank()).toList();
        boolean credentials = Boolean.TRUE.equals(cfg.getAllowCredentials());
        // 禁用判定必须两键皆空（评审收口 D-2 修复：exact 键含通配时 patterns 为 null——
        // spring-web 的 setAllowedOrigins 不做通配转移，仅以 patterns 空判定禁用会提前返回漏过 exact 校验）
        if (patterns.isEmpty() && exact.isEmpty()) {
            log.info("Gateway CORS disabled for {} (empty allowed-origin-patterns/origins): same-origin deployment, "
                + "cross-origin requests get no CORS headers", pathKey);
            return;
        }
        if (credentials && patterns.stream().anyMatch(p -> p.contains("*"))) {
            throw new IllegalStateException(
                "Gateway CORS 配置非法（" + pathKey + "）：allow-credentials=true 时禁止通配 origin"
                    + "（allowed-origin-patterns=" + patterns + "）；请改为明确 origin 列表或显式置空禁用 CORS（同源部署）");
        }
        // 兄弟键 allowed-origins（exact 列表）：Nacos 若覆写该键为 "*" 同样构成任意源携带凭证；
        // 且运行期 Spring 对 exact "*" + credentials 每请求抛 IllegalArgumentException（500），必须在启动期拦截
        if (credentials && exact.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                "Gateway CORS 配置非法（" + pathKey + "）：allow-credentials=true 时禁止通配 origin"
                    + "（allowed-origins=" + exact + "）；请改为明确 origin 列表或移除该键");
        }
        log.info("Gateway CORS validated for {}: allowed-origin-patterns={}, allowed-origins={}, allow-credentials={}",
            pathKey, patterns, exact, credentials);
    }
}
