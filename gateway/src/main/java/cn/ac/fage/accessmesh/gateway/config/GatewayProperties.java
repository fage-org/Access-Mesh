package cn.ac.fage.accessmesh.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 网关自定义配置属性类
 * <p>
 * 绑定到'gateway'前缀的配置属性，包含白名单、请求头、权限、签名等配置。
 * 通过application.yml中的gateway.*配置项进行配置。
 * </p>
 *
 * <p>T-ACCESS-003 评审 P1 修复（2026-08-14）：指定 Bean 名 {@code accessGatewayProperties}。
 * 默认 Bean 名 {@code gatewayProperties} 与 Spring Cloud Gateway 自带的
 * {@code org.springframework.cloud.gateway.config.GatewayProperties}（GatewayAutoConfiguration
 * 创建）同名冲突，导致上下文无法启动（归并前既有缺陷，由新 Gateway 上下文测试暴露）。
 * 注入点按类型（cn.ac.fage.accessmesh.gateway.config.GatewayProperties）查找不受影响。</p>
 *
 * <p>T-ACCESS-008（2026-08-21）：删除 {@code gateway.cache.l1.*}（快照 TTL/容量统一由
 * {@code GatewayCacheCatalog} 声明 + {@code accessmesh.cache.catalogs."gw:interface-snapshot".*}
 * 运维覆盖）与 {@code gateway.permission.fail-mode}（权限回源失败固定 fail-closed，
 * 不可切换）；新增 {@code snapshot-load-deadline} 快照加载全链路墙钟硬截止时间。</p>
 */
@Getter
@Setter
@Component("accessGatewayProperties")
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Whitelist whitelist = new Whitelist();
    private Header header = new Header();
    private Permission permission = new Permission();
    private Signature signature = new Signature();
    private OAuth2 oauth2 = new OAuth2();

    /**
     * 白名单配置
     * <p>
     * 配置不需要认证和权限校验的路径列表。
     * 这些路径直接放行，如认证接口、健康检查、公开资源等。
     * </p>
     */
    @Getter
    @Setter
    public static class Whitelist {
        private List<String> paths = List.of(
            "/auth/**",
            "/actuator/health",
            "/public/**",
            "/captcha/**"
        );
    }

    /**
     * OAuth2 委托令牌透传配置（T-ACCESS-013）
     * <p>
     * 命中路径的请求跳过会话校验/权限校验/身份头注入/身份头签名，
     * Authorization 头（OAuth2 JWT）原样透传给下游，由下游 access-service 的
     * OAuth2 JWT 认证分支验签 + 开放路径门禁（scope/audience/client_id 启用校验）判定。
     * 路径为 Gateway 外部口径（如 /admin/api/**，StripPrefix 后由下游按自身口径
     * 匹配 access.oauth2.resource-paths）；/auth/** 已在白名单中透传，无需重复配置。
     * 默认为空（无业务路径默认开放）。
     * </p>
     */
    @Getter
    @Setter
    public static class OAuth2 {
        private List<String> passthroughPaths = List.of();
    }

    /**
     * 请求头配置
     * <p>
     * 配置需要清理和增强的请求头列表。
     * - clean: 下游服务之前需要清理的请求头
     * - enrich: 需要从token中提取并添加的请求头
     * </p>
     */
    @Getter
    @Setter
    public static class Header {
        private List<String> clean = List.of(
            "X-User-Id",
            "X-Tenant-Id",
            "X-User-Name",
            "X-User-Roles",
            "X-User-Type",
            "X-Internal-Secret",
            "X-User-Signature",
            "X-Signature-Timestamp"
        );
        private Enrich enrich = new Enrich();

        /**
         * 请求头增强
         * <p>
         * 配置从token中提取并添加到下游请求的请求头名称。
         * </p>
         */
        @Getter
        @Setter
        public static class Enrich {
            private String requestId = "X-Request-Id";
            private String tenantId = "X-Tenant-Id";
            private String userId = "X-User-Id";
            private String userName = "X-User-Name";
            private String userType = "X-User-Type";
        }
    }

    /**
     * 权限配置
     * <p>
     * 配置权限校验服务的地址和策略。
     * </p>
     */
    @Getter
    @Setter
    public static class Permission {
        // T-ACCESS-010：目标由 permission-center 统一切换为 access-service
        private String serviceUrl = "lb://access-service";
        private String checkInterfacePath = "/api/perm/auth/check-interface";
        // T-PERM-001：快照模式接口，Gateway 拉取用户全量接口权限快照用于本地匹配
        private String interfaceSnapshotPath = "/api/perm/auth/interface-snapshot";
        private String unregisteredPolicy = "DENY";
        /**
         * 权限快照加载全链路墙钟硬截止时间（T-ACCESS-008，默认/上限 5 秒）。
         * <p>
         * 计时覆盖服务发现与负载均衡、连接、请求发送、access-service 处理、
         * 响应读取与解码，以及失效竞争触发的重试；同一授权请求内的所有尝试
         * 共享同一截止时间，不得因重试重新计时。超过截止时间不得写入 Gateway
         * 缓存并固定 fail-closed 返回 503。连接/响应分段超时不能替代该总截止。
         * 配置超过 5 秒时启动失败（GatewayCacheBoundaryValidator）。
         * </p>
         */
        private Duration snapshotLoadDeadline = Duration.ofSeconds(5);
    }

    /**
     * 签名配置
     * <p>
     * 用于防止网关和下游服务之间的请求头篡改。
     * 使用HMAC-SHA256签名验证请求头的完整性。
     * 签名功能始终启用（安全原因，移除enabled字段）。
     * </p>
     */
    @Getter
    @Setter
    public static class Signature {
        /**
         * HMAC-SHA256签名密钥
         * <p>
         * 应通过环境变量配置以确保安全。
         * 必须：如未配置，应用启动将失败。
         * </p>
         */
        private String secret;

        /**
         * 签名值请求头名称
         */
        private String headerName = "X-User-Signature";

        /**
         * 签名时间戳请求头名称
         */
        private String timestampHeaderName = "X-Signature-Timestamp";
    }
}
