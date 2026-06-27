package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gateway 本地接口快照缓存失效器（T-PERM-006）
 * <p>
 * 根据 permission-center 广播的 {@link PermInvalidateEvent} 清理本地 Caffeine
 * {@code INTERFACE_SNAPSHOT}。Redis pub/sub 只保证尽力送达，丢失时由 TTL 兜底。
 * </p>
 */
@Component
public class InterfaceSnapshotCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(InterfaceSnapshotCacheInvalidator.class);

    private final Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache;

    public InterfaceSnapshotCacheInvalidator(Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache) {
        this.interfaceSnapshotCache = interfaceSnapshotCache;
    }

    /**
     * 清理本地快照缓存。
     * <ul>
     *   <li>serviceCodes 非空：清理该租户下对应服务全部用户快照（API mapping / 资源 / 条件规则影响）</li>
     *   <li>userIds 非空：清理该租户下对应用户全部服务快照（用户角色关系变化）</li>
     *   <li>仅 roleIds 非空：Gateway 无法本地反查角色影响用户，按租户级安全清理</li>
     * </ul>
     *
     * @return 实际失效的本地缓存条目数
     */
    public long evict(PermInvalidateEvent event) {
        if (event == null || event.tenantId() == null) {
            return 0L;
        }
        Set<String> serviceCodes = normalizeStrings(event.serviceCodes());
        Set<Long> userIds = normalizeLongs(event.userIds());
        Set<Long> roleIds = normalizeLongs(event.roleIds());
        if (serviceCodes.isEmpty() && userIds.isEmpty() && roleIds.isEmpty()) {
            return 0L;
        }

        long evicted = 0L;
        for (String key : new ArrayList<>(interfaceSnapshotCache.asMap().keySet())) {
            InterfaceSnapshotCacheKeys.ParsedKey parsed = InterfaceSnapshotCacheKeys.parse(key);
            if (parsed == null || !event.tenantId().equals(parsed.tenantId())) {
                continue;
            }
            if (shouldEvict(parsed, serviceCodes, userIds)) {
                interfaceSnapshotCache.invalidate(key);
                evicted++;
            }
        }
        log.debug("Evicted gateway interface snapshots (tenantId={}, roleIds={}, userIds={}, serviceCodes={}, evicted={})",
            event.tenantId(), roleIds, userIds, serviceCodes, evicted);
        return evicted;
    }

    private boolean shouldEvict(InterfaceSnapshotCacheKeys.ParsedKey parsed, Set<String> serviceCodes, Set<Long> userIds) {
        boolean serviceMatched = !serviceCodes.isEmpty() && serviceCodes.contains(parsed.serviceCode());
        boolean userMatched = !userIds.isEmpty() && userIds.contains(parsed.userId());
        if (!serviceCodes.isEmpty() || !userIds.isEmpty()) {
            return serviceMatched || userMatched;
        }
        return true;
    }

    private Set<Long> normalizeLongs(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(v -> v != null)
            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> normalizeStrings(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
