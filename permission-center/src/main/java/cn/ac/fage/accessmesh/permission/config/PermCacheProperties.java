package cn.ac.fage.accessmesh.permission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Permission cache configuration properties.
 */
@Component
@ConfigurationProperties(prefix = "perm.cache")
public class PermCacheProperties {

    private L1Config l1 = new L1Config();
    private L2Config l2 = new L2Config();

    public L1Config getL1() { return l1; }
    public void setL1(L1Config l1) { this.l1 = l1; }

    public L2Config getL2() { return l2; }
    public void setL2(L2Config l2) { this.l2 = l2; }

    public static class L1Config {
        private int maximumSize = 1000;
        private int expireMinutes = 10;

        public int getMaximumSize() { return maximumSize; }
        public void setMaximumSize(int maximumSize) { this.maximumSize = maximumSize; }

        public int getExpireMinutes() { return expireMinutes; }
        public void setExpireMinutes(int expireMinutes) { this.expireMinutes = expireMinutes; }
    }

    public static class L2Config {
        private int ttlMinutes = 30;

        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }
}
