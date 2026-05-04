package cn.ac.fage.accessmesh.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gateway custom configuration properties bound to 'gateway' prefix.
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Whitelist whitelist = new Whitelist();
    private Cache cache = new Cache();
    private Header header = new Header();
    private Permission permission = new Permission();
    private Signature signature = new Signature();

    public Whitelist getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(Whitelist whitelist) {
        this.whitelist = whitelist;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    public static class Whitelist {
        private List<String> paths = List.of(
            "/auth/**",
            "/actuator/health",
            "/public/**",
            "/captcha/**"
        );

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }
    }

    public static class Cache {
        private L1 l1 = new L1();

        public L1 getL1() {
            return l1;
        }

        public void setL1(L1 l1) {
            this.l1 = l1;
        }

        public static class L1 {
            private long maxSize = 50000;
            private int ttlSeconds = 10;  // 缩短TTL以降低权限撤销后的风险窗口（原30秒）

            public long getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(long maxSize) {
                this.maxSize = maxSize;
            }

            public int getTtlSeconds() {
                return ttlSeconds;
            }

            public void setTtlSeconds(int ttlSeconds) {
                this.ttlSeconds = ttlSeconds;
            }
        }
    }

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

        public List<String> getClean() {
            return clean;
        }

        public void setClean(List<String> clean) {
            this.clean = clean;
        }

        public Enrich getEnrich() {
            return enrich;
        }

        public void setEnrich(Enrich enrich) {
            this.enrich = enrich;
        }

        public static class Enrich {
            private String requestId = "X-Request-Id";
            private String tenantId = "X-Tenant-Id";
            private String userId = "X-User-Id";
            private String userName = "X-User-Name";
            private String userType = "X-User-Type";

            public String getRequestId() {
                return requestId;
            }

            public void setRequestId(String requestId) {
                this.requestId = requestId;
            }

            public String getTenantId() {
                return tenantId;
            }

            public void setTenantId(String tenantId) {
                this.tenantId = tenantId;
            }

            public String getUserId() {
                return userId;
            }

            public void setUserId(String userId) {
                this.userId = userId;
            }

            public String getUserName() {
                return userName;
            }

            public void setUserName(String userName) {
                this.userName = userName;
            }

            public String getUserType() {
                return userType;
            }

            public void setUserType(String userType) {
                this.userType = userType;
            }
        }
    }

    public static class Permission {
        private String serviceUrl = "lb://permission-center";
        private String checkInterfacePath = "/api/perm/auth/check-interface";
        private String unregisteredPolicy = "DENY";

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public String getCheckInterfacePath() {
            return checkInterfacePath;
        }

        public void setCheckInterfacePath(String checkInterfacePath) {
            this.checkInterfacePath = checkInterfacePath;
        }

        public String getUnregisteredPolicy() {
            return unregisteredPolicy;
        }

        public void setUnregisteredPolicy(String unregisteredPolicy) {
            this.unregisteredPolicy = unregisteredPolicy;
        }
    }

    /**
     * Signature configuration for header signing.
     * Used to prevent header tampering between gateway and downstream services.
     * FIX #7: Signature is always enabled - removed enabled field for security
     */
    public static class Signature {
        /**
         * Secret key for HMAC-SHA256 signing.
         * Should be configured via environment variable for security.
         * REQUIRED: Application will fail to start if not configured.
         */
        private String secret;

        /**
         * Header name for the signature value.
         */
        private String headerName = "X-User-Signature";

        /**
         * Header name for the signature timestamp.
         */
        private String timestampHeaderName = "X-Signature-Timestamp";

        // FIX #7: isEnabled() removed - signature is always enabled
        // Setter for enabled also removed

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getTimestampHeaderName() {
            return timestampHeaderName;
        }

        public void setTimestampHeaderName(String timestampHeaderName) {
            this.timestampHeaderName = timestampHeaderName;
        }
    }
}
