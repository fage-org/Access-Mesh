package cn.ac.fage.accessmesh.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 同步租户门禁适配。调用方须按设计持数据库租户行锁：先 reserve，再读取最新事实，
 * 数据库变更提交后 publish。Redis 故障向上传播，入口按基础设施不可用拒绝。
 */
public final class RedisTenantGateStore {
    private final StringRedisTemplate redis;

    public RedisTenantGateStore(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis);
    }

    public record Publication(long tenantId, String processId, String token) {}

    public TenantGateState read(long tenantId) {
        return TenantGateState.fromWire(redis.execute(TenantGateProtocol.SCRIPT,
            List.of(TenantGateProtocol.key(tenantId)), "READ"));
    }

    public TenantGateSnapshot readForSession(long tenantId) {
        return TenantGateSnapshot.fromWire(redis.execute(TenantGateProtocol.SCRIPT,
            List.of(TenantGateProtocol.key(tenantId)), "READ_SESSION"));
    }

    public Map<Long, TenantGateState> readBatch(List<Long> tenantIds) {
        if (tenantIds.isEmpty()) return Map.of();
        List<Long> ids = tenantIds.stream().distinct().toList();
        List<String> values = redis.execute(TenantGateProtocol.READ_BATCH_SCRIPT,
            ids.stream().map(TenantGateProtocol::key).toList(), "READ_BATCH");
        if (values == null || values.size() != ids.size()) {
            throw new IllegalStateException("invalid tenant gate batch response");
        }
        Map<Long, TenantGateState> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            result.put(ids.get(i), TenantGateState.fromWire(values.get(i)));
        }
        return Collections.unmodifiableMap(result);
    }

    public Publication reserve(long tenantId) {
        String token = UUID.randomUUID().toString();
        String processId = redis.execute(TenantGateProtocol.SCRIPT,
            List.of(TenantGateProtocol.key(tenantId)), "BLOCK", token);
        if (processId == null || !processId.matches("[a-f0-9]{40}")) {
            throw new IllegalStateException("Redis did not acknowledge tenant gate reservation");
        }
        return new Publication(tenantId, processId, token);
    }

    /** false 表示令牌已失效；不得把未发布的启用状态报告为恢复成功。 */
    public boolean publish(Publication publication, TenantGateState state) {
        Objects.requireNonNull(publication);
        Objects.requireNonNull(state);
        if (state.status() == TenantGateState.Status.UNAVAILABLE) {
            throw new IllegalArgumentException("cannot publish unavailable state");
        }
        String result = redis.execute(TenantGateProtocol.SCRIPT,
            List.of(TenantGateProtocol.key(publication.tenantId())), "PUBLISH",
            publication.processId(), publication.token(), state.status().name(),
            Long.toString(state.sessionEpoch()));
        return "PUBLISHED".equals(result);
    }

    /** 新租户开通回滚后清理自己的阻断记录，不能清除后续操作或已经发布的状态。 */
    public void discard(Publication publication) {
        redis.execute(TenantGateProtocol.SCRIPT, List.of(TenantGateProtocol.key(publication.tenantId())),
            "DISCARD", publication.processId(), publication.token());
    }
}
