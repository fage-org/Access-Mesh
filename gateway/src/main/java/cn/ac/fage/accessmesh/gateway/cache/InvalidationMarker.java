package cn.ac.fage.accessmesh.gateway.cache;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 显式失效标记与回源代际校验。
 * <p>
 * {@code invalidatedKeys} 用于 stale-allow 门禁；{@code keyGeneration} 和
 * {@code globalEpoch} 用于阻止失效事件之后返回的旧回源结果重新写回缓存。
 * </p>
 */
@Component
public class InvalidationMarker {

    private final Set<String> invalidatedKeys = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, AtomicLong> keyGeneration = new ConcurrentHashMap<>();
    private final AtomicLong globalEpoch = new AtomicLong();

    public synchronized LoadToken beginLoad(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return new LoadToken(key, generationOf(key), globalEpoch.get());
    }

    public synchronized void mark(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        invalidatedKeys.add(key);
        keyGeneration.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public synchronized void markAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            invalidatedKeys.add(key);
            keyGeneration.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    public synchronized boolean contains(String key) {
        return key != null && invalidatedKeys.contains(key);
    }

    public synchronized boolean isCurrent(LoadToken token) {
        if (token == null) {
            return false;
        }
        return globalEpoch.get() == token.globalEpoch()
            && generationOf(token.key()) == token.keyGeneration();
    }

    public synchronized boolean unmarkIfCurrent(LoadToken token) {
        if (!isCurrentInternal(token)) {
            return false;
        }
        invalidatedKeys.remove(token.key());
        return true;
    }

    public synchronized boolean commitIfCurrent(LoadToken token, Runnable commitAction) {
        if (!isCurrentInternal(token)) {
            return false;
        }
        commitAction.run();
        invalidatedKeys.remove(token.key());
        return true;
    }

    public synchronized void clearAndBumpGlobalEpoch() {
        invalidatedKeys.clear();
        keyGeneration.clear();
        globalEpoch.incrementAndGet();
    }

    /**
     * 保留当前仍可能参与 stale 或 in-flight 校验的 key，清掉孤立标记。
     */
    public synchronized void retainLiveKeys(Set<String> liveKeys) {
        Set<String> safeLiveKeys = liveKeys == null ? Set.of() : liveKeys;
        invalidatedKeys.retainAll(safeLiveKeys);
        keyGeneration.keySet().removeIf(key -> !safeLiveKeys.contains(key));
    }

    /**
     * 标记集合超过 live key 数量 2 倍时触发上限保护清理。
     */
    public synchronized void cleanupIfOversized(Set<String> liveKeys) {
        Set<String> safeLiveKeys = liveKeys == null ? Set.of() : liveKeys;
        int limit = Math.max(1, safeLiveKeys.size() * 2);
        if (invalidatedKeys.size() > limit || keyGeneration.size() > limit) {
            retainLiveKeys(safeLiveKeys);
        }
    }

    synchronized int invalidatedSize() {
        return invalidatedKeys.size();
    }

    synchronized int generationSize() {
        return keyGeneration.size();
    }

    private boolean isCurrentInternal(LoadToken token) {
        if (token == null) {
            return false;
        }
        return globalEpoch.get() == token.globalEpoch()
            && generationOf(token.key()) == token.keyGeneration();
    }

    private long generationOf(String key) {
        AtomicLong generation = keyGeneration.get(key);
        return generation == null ? 0L : generation.get();
    }

    public record LoadToken(String key, long keyGeneration, long globalEpoch) {
    }
}
