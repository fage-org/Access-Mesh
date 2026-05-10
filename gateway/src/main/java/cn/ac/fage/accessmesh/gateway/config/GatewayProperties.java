package cn.ac.fage.accessmesh.gateway.config;

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
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Whitelist whitelist = new Whitelist();
    private Cache cache = new Cache();
    private Header header = new Header();
    private Permission permission = new Permission();
    private Signature signature = new Signature();

    /**
     * 获取白名单配置
     *
     * @return 白名单配置对象
     */
    public Whitelist getWhitelist() {
        return whitelist;
    }

    /**
     * 设置白名单配置
     *
     * @param whitelist 白名单配置对象
     */
    public void setWhitelist(Whitelist whitelist) {
        this.whitelist = whitelist;
    }

    /**
     * 获取缓存配置
     *
     * @return 缓存配置对象
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * 设置缓存配置
     *
     * @param cache 缓存配置对象
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * 获取请求头配置
     *
     * @return 请求头配置对象
     */
    public Header getHeader() {
        return header;
    }

    /**
     * 设置请求头配置
     *
     * @param header 请求头配置对象
     */
    public void setHeader(Header header) {
        this.header = header;
    }

    /**
     * 获取权限配置
     *
     * @return 权限配置对象
     */
    public Permission getPermission() {
        return permission;
    }

    /**
     * 设置权限配置
     *
     * @param permission 权限配置对象
     */
    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    /**
     * 获取签名配置
     *
     * @return 签名配置对象
     */
    public Signature getSignature() {
        return signature;
    }

    /**
     * 设置签名配置
     *
     * @param signature 签名配置对象
     */
    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    /**
     * 白名单配置
     * <p>
     * 配置不需要认证和权限校验的路径列表。
     * 这些路径直接放行，如认证接口、健康检查、公开资源等。
     * </p>
     */
    public static class Whitelist {
        private List<String> paths = List.of(
            "/auth/**",
            "/actuator/health",
            "/public/**",
            "/captcha/**"
        );

        /**
         * 获取白名单路径列表
         *
         * @return 白名单路径列表
         */
        public List<String> getPaths() {
            return paths;
        }

        /**
         * 设置白名单路径列表
         *
         * @param paths 白名单路径列表
         */
        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }

    /**
     * 缓存配置
     * <p>
     * 配置网关本地缓存参数，用于减少远程服务调用。
     * </p>
     */
    public static class Cache {
        private L1 l1 = new L1();

        /**
         * 获取L1缓存配置
         *
         * @return L1缓存配置对象
         */
        public L1 getL1() {
            return l1;
        }

        /**
         * 设置L1缓存配置
         *
         * @param l1 L1缓存配置对象
         */
        public void setL1(L1 l1) {
            this.l1 = l1;
        }

        /**
         * L1本地缓存配置
         * <p>
         * 配置Caffeine本地缓存的参数：
         * - maxSize: 最大缓存条目数
         * - ttlSeconds: 缓存过期时间
         * </p>
         */
        public static class L1 {
            private long maxSize = 50000;
            private int ttlSeconds = 10;  // 缩短TTL以降低权限撤销后的风险窗口（原30秒）

            /**
             * 获取最大缓存条目数
             *
             * @return 最大缓存条目数
             */
            public long getMaxSize() {
                return maxSize;
            }

            /**
             * 设置最大缓存条目数
             *
             * @param maxSize 最大缓存条目数
             */
            public void setMaxSize(long maxSize) {
                this.maxSize = maxSize;
            }

            /**
             * 获取缓存过期时间（秒）
             *
             * @return 缓存过期时间（秒）
             */
            public int getTtlSeconds() {
                return ttlSeconds;
            }

            /**
             * 设置缓存过期时间（秒）
             *
             * @param ttlSeconds 缓存过期时间（秒）
             */
            public void setTtlSeconds(int ttlSeconds) {
                this.ttlSeconds = ttlSeconds;
            }
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
         * 获取需要清理的请求头列表
         *
         * @return 需要清理的请求头列表
         */
        public List<String> getClean() {
            return clean;
        }

        /**
         * 设置需要清理的请求头列表
         *
         * @param clean 需要清理的请求头列表
         */
        public void setClean(List<String> clean) {
            this.clean = clean;
        }

        /**
         * 获取请求头增强配置
         *
         * @return 请求头增强配置对象
         */
        public Enrich getEnrich() {
            return enrich;
        }

        /**
         * 设置请求头增强配置
         *
         * @param enrich 请求头增强配置对象
         */
        public void setEnrich(Enrich enrich) {
            this.enrich = enrich;
        }

        /**
         * 请求头增强配置
         * <p>
         * 配置从token中提取并添加到下游请求的请求头名称。
         * </p>
         */
        public static class Enrich {
            private String requestId = "X-Request-Id";
            private String tenantId = "X-Tenant-Id";
            private String userId = "X-User-Id";
            private String userName = "X-User-Name";
            private String userType = "X-User-Type";

            /**
             * 获取请求ID请求头名称
             *
             * @return 请求ID请求头名称
             */
            public String getRequestId() {
                return requestId;
            }

            /**
             * 设置请求ID请求头名称
             *
             * @param requestId 请求ID请求头名称
             */
            public void setRequestId(String requestId) {
                this.requestId = requestId;
            }

            /**
             * 获取租户ID请求头名称
             *
             * @return 租户ID请求头名称
             */
            public String getTenantId() {
                return tenantId;
            }

            /**
             * 设置租户ID请求头名称
             *
             * @param tenantId 租户ID请求头名称
             */
            public void setTenantId(String tenantId) {
                this.tenantId = tenantId;
            }

            /**
             * 获取用户ID请求头名称
             *
             * @return 用户ID请求头名称
             */
            public String getUserId() {
                return userId;
            }

            /**
             * 设置用户ID请求头名称
             *
             * @param userId 用户ID请求头名称
             */
            public void setUserId(String userId) {
                this.userId = userId;
            }

            /**
             * 获取用户名请求头名称
             *
             * @return 用户名请求头名称
             */
            public String getUserName() {
                return userName;
            }

            /**
             * 设置用户名请求头名称
             *
             * @param userName 用户名请求头名称
             */
            public void setUserName(String userName) {
                this.userName = userName;
            }

            /**
             * 获取用户类型请求头名称
             *
             * @return 用户类型请求头名称
             */
            public String getUserType() {
                return userType;
            }

            /**
             * 设置用户类型请求头名称
             *
             * @param userType 用户类型请求头名称
             */
            public void setUserType(String userType) {
                this.userType = userType;
            }
        }
    }

    /**
     * 权限配置
     * <p>
     * 配置权限校验服务的地址和策略。
     * </p>
     */
    public static class Permission {
        private String serviceUrl = "lb://permission-center";
        private String checkInterfacePath = "/api/perm/auth/check-interface";
        private String unregisteredPolicy = "DENY";

        /**
         * 获取权限服务URL
         *
         * @return 权限服务URL
         */
        public String getServiceUrl() {
            return serviceUrl;
        }

        /**
         * 设置权限服务URL
         *
         * @param serviceUrl 权限服务URL
         */
        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        /**
         * 获取接口权限校验路径
         *
         * @return 接口权限校验路径
         */
        public String getCheckInterfacePath() {
            return checkInterfacePath;
        }

        /**
         * 设置接口权限校验路径
         *
         * @param checkInterfacePath 接口权限校验路径
         */
        public void setCheckInterfacePath(String checkInterfacePath) {
            this.checkInterfacePath = checkInterfacePath;
        }

        /**
         * 获取未注册接口策略
         * <p>
         * 当接口未在权限中心注册时的处理策略：
         * - DENY: 拒绝访问
         * - ALLOW: 允许访问
         * </p>
         *
         * @return 未注册接口策略
         */
        public String getUnregisteredPolicy() {
            return unregisteredPolicy;
        }

        /**
         * 设置未注册接口策略
         *
         * @param unregisteredPolicy 未注册接口策略
         */
        public void setUnregisteredPolicy(String unregisteredPolicy) {
            this.unregisteredPolicy = unregisteredPolicy;
        }
    }

    /**
     * 签名配置
     * <p>
     * 用于防止网关和下游服务之间的请求头篡改。
     * 使用HMAC-SHA256签名验证请求头的完整性。
     * 签名功能始终启用（安全原因，移除enabled字段）。
     * </p>
     */
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

        /**
         * 获取签名密钥
         *
         * @return 签名密钥
         */
        public String getSecret() {
            return secret;
        }

        /**
         * 设置签名密钥
         *
         * @param secret 签名密钥
         */
        public void setSecret(String secret) {
            this.secret = secret;
        }

        /**
         * 获取签名请求头名称
         *
         * @return 签名请求头名称
         */
        public String getHeaderName() {
            return headerName;
        }

        /**
         * 设置签名请求头名称
         *
         * @param headerName 签名请求头名称
         */
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        /**
         * 获取签名时间戳请求头名称
         *
         * @return 签名时间戳请求头名称
         */
        public String getTimestampHeaderName() {
            return timestampHeaderName;
        }

        /**
         * 设置签名时间戳请求头名称
         *
         * @param timestampHeaderName 签名时间戳请求头名称
         */
        public void setTimestampHeaderName(String timestampHeaderName) {
            this.timestampHeaderName = timestampHeaderName;
        }
    }
}