package cn.ac.fage.accessmesh.common.security;

import cn.ac.fage.accessmesh.common.cache.CacheKeyUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StreamUtils;

/**
 * Gateway 与 access-service 共享的租户门禁协议。
 * 生命周期协调状态不属于业务缓存，不使用 TTL、L1 或缓存失效广播。
 * <p>
 * 脚本以 classpath 的 tenant-gate.lua 为权威来源，类加载时读入一次文本：
 * DefaultRedisScript 按资源定位时每次执行都会触发 lastModified 资源探测
 * （同步监视器内），门禁读取在每请求热点路径上，固定开销应避免。
 * </p>
 */
public final class TenantGateProtocol {
    private TenantGateProtocol() {}

    private static final String SCRIPT_TEXT = loadScriptText();
    public static final RedisScript<String> SCRIPT = script();
    public static final RedisScript<List<String>> READ_BATCH_SCRIPT = batchScript();

    public static String key(long tenantId) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        return CacheKeyUtil.build(tenantId, "access:tenant-gate", "state");
    }

    private static String loadScriptText() {
        try (InputStream in = new ClassPathResource("security/tenant-gate.lua").getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("tenant gate lua script missing from classpath", exception);
        }
    }

    private static RedisScript<String> script() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(SCRIPT_TEXT);
        script.setResultType(String.class);
        return script;
    }

    @SuppressWarnings("unchecked")
    private static RedisScript<List<String>> batchScript() {
        DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
        script.setScriptText(SCRIPT_TEXT);
        script.setResultType((Class<List<String>>) (Class<?>) List.class);
        return script;
    }
}
