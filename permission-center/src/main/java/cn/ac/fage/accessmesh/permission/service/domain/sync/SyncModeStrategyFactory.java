package cn.ac.fage.accessmesh.permission.service.domain.sync;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for obtaining sync mode strategies.
 */
@Component
public class SyncModeStrategyFactory {

    private final Map<String, SyncModeStrategy> strategies = new ConcurrentHashMap<>();

    public SyncModeStrategyFactory(FullSyncStrategy fullSyncStrategy,
                                    IncrementalSyncStrategy incrementalSyncStrategy) {
        registerStrategy(fullSyncStrategy);
        registerStrategy(incrementalSyncStrategy);
    }

    /**
     * Register a strategy.
     */
    public void registerStrategy(SyncModeStrategy strategy) {
        strategies.put(strategy.getName().toUpperCase(), strategy);
    }

    /**
     * Get a strategy by name.
     *
     * @param syncMode the sync mode name (FULL, INCREMENTAL, etc.)
     * @return the corresponding strategy
     * @throws IllegalArgumentException if the strategy is not found
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

    /**
     * Check if a strategy exists for the given mode.
     */
    public boolean hasStrategy(String syncMode) {
        return syncMode != null && strategies.containsKey(syncMode.toUpperCase());
    }
}