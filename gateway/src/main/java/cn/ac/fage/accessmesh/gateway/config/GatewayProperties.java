package cn.ac.fage.accessmesh.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关自定义配置属性类
 * <p>
 * 绑定到'gateway'前缀的配置属性，包含白名单、缓存、请求头、权限、签名等配置。
 * 通过application.yml中的gateway.*配置项进行配置。
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Whitelist whitelist = new Whitelist();
    private Cache cache = new Cache();
    private Header header = new Header();
    private Permission permission = new Permission();
    private Signature signature = new Signature();

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
     * 缓存配置
     * <p>
     * 配置网关本地缓存参数，用于减少远程服务调用。
     * </p>
     */
    @Getter
    @Setter
    public static class Cache {
        private L1 l1 = new L1();

        /**
         * L1本地缓存配置
         * <p>
         * 配置Caffeine本地缓存的参数：
         * - maxSize: 最大缓存条目数
         * - ttlSeconds: 缓存过期时间
         * </p>
         */
        @Getter
        @Setter
        public static class L1 {
            private long maxSize = 50000;
            // T-PERM-001：快照模式下 TTL 兜底 30-60s（快照失效主要靠 Redis pub/sub 主动广播 T-PERM-006，
            // TTL 仅作兜底）。原 check-interface 单值模式为 10s。
            private int ttlSeconds = 30;
            // T-PERM-008：stale store 续命窗口，T-GW-003 接入 stale-allow 时使用。
            private int staleGraceSeconds = 30;
        }
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
         * 请求头增强配置
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
        private String serviceUrl = "lb://permission-center";
        private String checkInterfacePath = "/api/perm/auth/check-interface";
        // T-PERM-001：快照模式接口，Gateway 拉取用户全量接口权限快照用于本地匹配
        private String interfaceSnapshotPath = "/api/perm/auth/interface-snapshot";
        private String unregisteredPolicy = "DENY";
        // T-GW-001：权限校验失联兜底模式。closed=拒绝（默认，生产安全）/ open=放行（仅demo）/ stale-allow=陈旧快照续命（T-GW-003）
        private FailMode failMode = FailMode.CLOSED;
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
