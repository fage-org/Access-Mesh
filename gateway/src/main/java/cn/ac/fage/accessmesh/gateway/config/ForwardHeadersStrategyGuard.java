package cn.ac.fage.accessmesh.gateway.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * forward-headers-strategy 启动护栏（T-GW-008 codex 外评 P1 处置，2026-09-10）。
 * <p>
 * {@code server.forward-headers-strategy=framework} 时 Spring Boot 注册
 * {@code ForwardedHeaderTransformer}，其在 {@code HttpWebHandlerAdapter.handle}
 * 首指令（全部 WebFilter 之前）把外部 {@code X-Forwarded-For} 解析进
 * {@code request.getRemoteAddress()} 并删除转发头——HeaderCleanFilter 的清洗与
 * XFF 重建、PermissionFilter.resolveClientIp 直用 remoteAddr 所依赖的
 * 「socket 对端 = 唯一可信 IP 来源」前提被整体打破（清洗列表来不及参与，
 * 持合法凭证 + 伪造 XFF 即可进入 IP 白/黑名单条件评估）。native 在
 * WebFlux/Reactor-Netty 下无 transformer、不污染 remoteAddress，但一并拒绝
 * 以防语义混淆与未来容器行为差异。允许值：缺省与 {@code none}（Spring Boot 默认）。
 * </p>
 */
@Component
public class ForwardHeadersStrategyGuard implements InitializingBean {

    /** Spring Boot 标准属性（ServerProperties.forwardHeadersStrategy）。 */
    static final String PROPERTY = "server.forward-headers-strategy";

    private final Environment environment;

    public ForwardHeadersStrategyGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String strategy = environment.getProperty(PROPERTY);
        if (strategy == null || strategy.isBlank() || "none".equalsIgnoreCase(strategy.trim())) {
            return;
        }
        throw new IllegalStateException(PROPERTY + "=" + strategy
            + " 被 XFF 信任面收口（T-GW-008）禁止：framework 会在全部过滤器之前把外部 X-Forwarded-For"
            + " 解析进 remoteAddress（HeaderCleanFilter 清洗来不及参与，伪造 IP 将进入条件评估/日志/快照重评），"
            + " native 一并拒绝防语义混淆；允许缺省/none（socket 对端=唯一可信 IP 来源）。"
            + " 多层代理拓扑的 IP 采信处置见 security-standards §7 与 access-service-rebuild-runbook。");
    }
}
