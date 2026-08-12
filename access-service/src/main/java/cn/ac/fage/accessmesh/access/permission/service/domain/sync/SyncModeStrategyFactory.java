package cn.ac.fage.accessmesh.access.permission.service.domain.sync;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步模式策略工厂
 * <p>
 * 用于获取不同同步模式的策略实例。
 * 支持按名称查找策略，已注册策略包括：
 * - FULL：全量同步策略
 * - INCREMENTAL：增量同步策略
 * </p>
 */
@Component
public class SyncModeStrategyFactory {

    /**
     * 已注册策略映射（名称→策略实例）
     */
    private final Map<String, SyncModeStrategy> strategies = new ConcurrentHashMap<>();

    /**
     * 构造函数，自动注册已知的策略
     *
     * @param fullSyncStrategy      全量同步策略
     * @param incrementalSyncStrategy 增量同步策略
     */
    public SyncModeStrategyFactory(FullSyncStrategy fullSyncStrategy,
                                    IncrementalSyncStrategy incrementalSyncStrategy) {
        registerStrategy(fullSyncStrategy);
        registerStrategy(incrementalSyncStrategy);
    }

    /**
     * 注册策略
     *
     * @param strategy 策略实例
     */
    public void registerStrategy(SyncModeStrategy strategy) {
        strategies.put(strategy.getName().toUpperCase(), strategy);
    }

    /**
     * 按名称获取策略
     *
     * @param syncMode 同步模式名称（如"FULL"、"INCREMENTAL")
     * @return 对应的策略实例
     * @throws IllegalArgumentException syncMode为空或策略不存在时抛出异常
     */
    public SyncModeStrategy getStrategy(String syncMode) {
        if (syncMode == null || syncMode.isBlank()) {
            throw new IllegalArgumentException("syncMode cannot be null or blank");
        }
        SyncModeStrategy strategy = strategies.get(syncMode.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown syncMode: " + syncMode);
        }
        return strategy;
    }

    }