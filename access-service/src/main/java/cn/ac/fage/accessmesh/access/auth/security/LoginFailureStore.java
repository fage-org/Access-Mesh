package cn.ac.fage.accessmesh.access.auth.security;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 登录失败计数是认证协调状态；平台与租户使用独立命名空间。
 * <p>
 * 键构造与计数脚本的唯一来源：写入方（登录失败计数）、判定方（锁定检查）与清除方
 * （登录成功、平台重置解锁）都必须经本类，不得在调用方自拼键格式——双份字面量
 * 单侧漂移会使清除方静默删错键、临时锁定无法按设计解除（2026-10-08 评审收敛）。
 * </p>
 */
@Component
public class LoginFailureStore {
    private static final Logger log = LoggerFactory.getLogger(LoginFailureStore.class);
    /** 计数达到该值即临时锁定；键自首次失败起 30 分钟过期，过期自动解锁 */
    public static final int LOCK_THRESHOLD = 5;
    public static final long LOCK_WINDOW_MINUTES = 30;
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
    private final StringRedisTemplate redis;

    public LoginFailureStore(StringRedisTemplate redis) { this.redis = redis; }

    public static String platformKey(String username) { return "platform:login:fail:" + username; }
    public static String tenantKey(long tenantId, String username) { return "login:fail:" + tenantId + ":" + username; }

    public void clearPlatform(String username) { redis.delete(platformKey(username)); }
    public void clearTenant(long tenantId, String username) { redis.delete(tenantKey(tenantId, username)); }

    public void recordPlatformFailure(String username) {
        redis.execute(INCREMENT, List.of(platformKey(username)), String.valueOf(LOCK_WINDOW_MINUTES * 60));
    }

    public void recordTenantFailure(long tenantId, String username) {
        redis.execute(INCREMENT, List.of(tenantKey(tenantId, username)), String.valueOf(LOCK_WINDOW_MINUTES * 60));
    }

    /**
     * 平台侧脏计数 fail-fast（拒绝判定而非宽容放行）：计数键仅由本服务脚本写入，
     * 负值或非数字说明状态被外部篡改，按基础设施异常拒绝平台入口。
     */
    public boolean isPlatformLocked(String username) {
        String count = redis.opsForValue().get(platformKey(username));
        if (count == null) return false;
        try {
            long value = Long.parseLong(count);
            if (value < 0) throw new NumberFormatException("negative login failure count");
            return value >= LOCK_THRESHOLD;
        } catch (NumberFormatException exception) {
            throw new SystemException(99999, "平台登录状态异常", exception);
        }
    }

    /**
     * 租户侧沿既有宽容语义（T-ADMIN-022）：脏值告警并按未锁定处理，
     * 不因计数键异常阻断登录主链。
     */
    public boolean isTenantLocked(long tenantId, String username) {
        String key = tenantKey(tenantId, username);
        String count = redis.opsForValue().get(key);
        if (count == null) return false;
        try {
            return Long.parseLong(count) >= LOCK_THRESHOLD;
        } catch (NumberFormatException exception) {
            log.warn("登录失败计数键存在非数字值，按未锁定处理: key={}, value={}", key, count);
            return false;
        }
    }
}
