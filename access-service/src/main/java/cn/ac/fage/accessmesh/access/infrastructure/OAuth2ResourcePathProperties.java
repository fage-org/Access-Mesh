package cn.ac.fage.accessmesh.access.infrastructure;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * OAuth2 资源服务器开放路径配置（T-ACCESS-013，用户决策：application.yml 静态配置）。
 * <p>
 * OAuth2 委托令牌（JWT）只允许访问显式配置的开放路径（默认拒绝），每条路径可声明三重门禁：
 * </p>
 * <ul>
 *   <li>{@code requiredScopes}：令牌 scope（空格分隔委托范围）必须全部包含，独立映射模型
 *       （不接入 PermQueryEngine，2026-08-22 用户决策）</li>
 *   <li>{@code audience}：令牌 aud claim 必须包含该受众（业务开放路径强制；userinfo 默认豁免）</li>
 *   <li>{@code clientIds}：可选客户端限定；无论是否配置，验签时均按 client_id 动态校验客户端启用状态</li>
 * </ul>
 * <p>
 * 路径支持 Ant 通配（如 {@code /api/example/**}，与 Gateway 白名单同机制），但启动时
 * fail-fast 防护：配置模式不得匹配平台会话端点（/auth/userinfo、/auth/user-menu、
 * /auth/oauth2/authorize——JWT 分支覆盖会导致 authorize 内部会话依赖 NotLoginException→500，
 * 且构成越权面）与内部凭证路径（/api/perm/**——X-Internal-Secret 与 OAuth2 JWT 双认证机制冲突）；
 * 业务开放路径（非 userinfo 豁免路径）必须声明 {@code requiredScopes} 与 {@code audience}
 * （空值运行时跳过门禁，属配置遗漏放行面）。
 * 不得以 {@code /auth/oauth2/**} 通配放开（覆盖 authorize/token/refresh/revoke 非资源端点）。
 * </p>
 * <p>
 * yml 显式配置 {@code access.oauth2.resource-paths} 时为全量替换（非合并），新增业务开放路径
 * 时必须保留 userinfo 条目。开放路径同时需要 Gateway 侧 {@code gateway.oauth2.passthrough-paths}
 * 放行透传（仅 Bearer 三段式 JWT 启用；路径为 Gateway 外部口径，如 /admin/** StripPrefix 后的
 * /api/**），见架构文档 §6。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "access.oauth2")
public class OAuth2ResourcePathProperties implements InitializingBean {

    /** 默认开放路径：/auth/oauth2/userinfo（T-ACCESS-004 唯一消费方口径，不声明门禁=仅验签+黑名单+客户端启用）。 */
    public static final String DEFAULT_USERINFO_PATH = "/auth/oauth2/userinfo";

    /** 平台会话端点（JWT 分支不得覆盖，启动防护保留清单）。 */
    private static final Set<String> RESERVED_SESSION_PATHS = Set.of(
        "/auth/userinfo", "/auth/user-menu", "/auth/oauth2/authorize");

    /** 内部凭证路径前缀（OAuth2 与内部凭证双认证机制冲突，禁止开放）。 */
    private static final String INTERNAL_API_PREFIX = "/api/perm";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 开放路径规则清单（默认仅 userinfo；yml 显式配置为全量替换）。 */
    private List<ResourcePathRule> resourcePaths =
        new ArrayList<>(List.of(ResourcePathRule.exactPath(DEFAULT_USERINFO_PATH)));

    public List<ResourcePathRule> getResourcePaths() {
        return resourcePaths;
    }

    public void setResourcePaths(List<ResourcePathRule> resourcePaths) {
        this.resourcePaths = resourcePaths;
    }

    /**
     * 按请求 URI 匹配开放路径规则（配置顺序，首个命中）；未命中返回 empty —— 委托令牌默认拒绝。
     */
    public Optional<ResourcePathRule> match(String uri) {
        for (ResourcePathRule rule : resourcePaths) {
            if (rule.getPath() != null && pathMatcher.match(rule.getPath(), uri)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * 启动防护校验（fail-fast，与 PermCacheBoundaryValidator 同模式）：
     * <ul>
     *   <li>配置模式覆盖保留端点或内部凭证路径时启动失败（防通配误配越权/双认证冲突）</li>
     *   <li>业务开放路径（非 userinfo 豁免路径）必须声明 requiredScopes 与 audience
     *       （评审 P1：空值在运行时直接跳过两项授权门禁，一次配置遗漏即可放行任意
     *       启用客户端的有效令牌；userinfo 豁免口径见 {@link #DEFAULT_USERINFO_PATH}）</li>
     * </ul>
     */
    @Override
    public void afterPropertiesSet() {
        if (resourcePaths == null) {
            resourcePaths = new ArrayList<>();
        }
        for (ResourcePathRule rule : resourcePaths) {
            String pattern = rule.getPath();
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalStateException(
                    "access.oauth2.resource-paths 存在空路径模式，开放路径必须逐项显式配置");
            }
            for (String reserved : RESERVED_SESSION_PATHS) {
                if (pathMatcher.match(pattern, reserved)) {
                    throw new IllegalStateException(String.format(
                        "access.oauth2.resource-paths 模式 '%s' 覆盖平台会话端点 %s（JWT 分支不得覆盖），启动失败",
                        pattern, reserved));
                }
            }
            if (mayCoverInternalApiPath(pattern)) {
                throw new IllegalStateException(String.format(
                    "access.oauth2.resource-paths 模式 '%s' 覆盖内部凭证路径 %s/**（OAuth2 与内部凭证双认证冲突），启动失败",
                    pattern, INTERNAL_API_PREFIX));
            }
            // 业务开放路径强制双门禁（userinfo 默认路径豁免：旧令牌无 aud 兼容，且
            // userinfo 不做 scope 委托，仅验签 + 黑名单 + 客户端启用）
            if (!DEFAULT_USERINFO_PATH.equals(pattern)) {
                if (rule.getRequiredScopes() == null || rule.getRequiredScopes().isEmpty()) {
                    throw new IllegalStateException(String.format(
                        "access.oauth2.resource-paths 业务路径 '%s' 未声明 requiredScopes（scope 校验为开放门禁核心，不得为空），启动失败",
                        pattern));
                }
                if (rule.getAudience() == null || rule.getAudience().isBlank()) {
                    throw new IllegalStateException(String.format(
                        "access.oauth2.resource-paths 业务路径 '%s' 未声明 audience（业务路径强制受众校验），启动失败",
                        pattern));
                }
            }
        }
    }

    /**
     * 判定配置模式是否可能匹配内部凭证路径空间（{@code /api/perm/**}，无限路径集合）。
     * <p>
     * 评审 P2：原字面量样本匹配（pattern 对 "/api/perm"、"/api/perm/**" 两个字符串做
     * match）不完备——形如 /api/&#42;&#42;/sync 的模式不匹配样本却命中
     * {@code /api/perm/abstract-user/sync}。
     * 复评 P2：AntPathMatcher 段内正则还支持 URI 模板变量 {@code {name}} /
     * {@code {name:regex}}（如 {@code /api/{module}/abstract-user/sync} 匹配
     * {@code /api/perm/abstract-user/sync}），模板变量出现在段位置且 regex 不跨段，
     * 与 {@code *} 同为段级通配，按同级保守处理。
     * 统一改用静态前缀保守判定：P = pattern 截止第一个通配符（* / ? / {）前的
     * 静态前缀（无通配时为全串），模式能匹配任一 /api/perm 开头路径的必要条件是：
     * </p>
     * <ul>
     *   <li>P 为空（首字符即通配，如根级全通配或首段单星形态）→ 匹配任意路径</li>
     *   <li>P 是 "/api/perm" 的字符前缀（含相等，如 {@code /}、{@code /ap}、{@code /api}、
     *       {@code /api/per?...} 截止 ? 的前缀）→ 通配符落在 /api/perm 内部或之后</li>
     *   <li>P 以 "/api/perm/" 开头（如精确路径 {@code /api/perm/x}、{@code /api/perm/x/**}）→ 已深入内部空间</li>
     * </ul>
     * <p>
     * 三条件均不满足时 P 与 "/api/perm" 无前缀关系（如 {@code /api/example/**}、
     * {@code /example/**}、{@code /example/{id}}），模式匹配的路径必以 P 开头，
     * 与内部空间无交集。判定保守方向为拒绝（单字符通配 ? 与模板变量的形态可能被
     * 误拒，可接受）。平台会话端点为 3 个有限具体路径，保留逐端点样本匹配即完备，
     * 不适用本方法。
     * </p>
     */
    private boolean mayCoverInternalApiPath(String pattern) {
        // 静态前缀：截止第一个通配符（* / ? / URI 模板变量 {name}——AntPathMatcher
        // 段内正则将 {var} 按段级通配展开，复评 P2）
        String staticPrefix = pattern;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' || c == '?' || c == '{') {
                staticPrefix = pattern.substring(0, i);
                break;
            }
        }
        return staticPrefix.isEmpty()
            || INTERNAL_API_PREFIX.equals(staticPrefix)
            || INTERNAL_API_PREFIX.startsWith(staticPrefix)
            || staticPrefix.startsWith(INTERNAL_API_PREFIX + "/");
    }

    /**
     * 单条开放路径规则。
     */
    public static class ResourcePathRule {

        /** Ant 路径模式（精确路径或通配）。 */
        private String path;

        /** 令牌必须包含的 scope 集合（独立映射：空格分隔 scope 的子集校验；空=不要求）。 */
        private Set<String> requiredScopes = new LinkedHashSet<>();

        /** 令牌 aud 必须包含的受众（空=该路径不校验受众，如默认 userinfo 豁免）。 */
        private String audience;

        /** 允许的客户端限定（空=不限定，仅动态校验启用状态）。 */
        private Set<String> clientIds = new LinkedHashSet<>();

        public static ResourcePathRule exactPath(String path) {
            ResourcePathRule rule = new ResourcePathRule();
            rule.path = path;
            return rule;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Set<String> getRequiredScopes() {
            return requiredScopes;
        }

        public void setRequiredScopes(Set<String> requiredScopes) {
            this.requiredScopes = requiredScopes;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public Set<String> getClientIds() {
            return clientIds;
        }

        public void setClientIds(Set<String> clientIds) {
            this.clientIds = clientIds;
        }
    }
}
