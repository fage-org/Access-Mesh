package cn.ac.fage.accessmesh.access.admin.sync.model;

import java.time.Duration;
import java.util.Map;

/**
 * Timeout policy used by the atomic sync-task claim SQL.
 */
public record SyncTaskClaimTimeouts(
    Duration defaultTimeout,
    Duration fullSyncTimeout,
    Map<String, Duration> actionTimeouts
) {

    private static final Duration FALLBACK = Duration.ofSeconds(60);

    public SyncTaskClaimTimeouts {
        defaultTimeout = safe(defaultTimeout, FALLBACK);
        fullSyncTimeout = safe(fullSyncTimeout, defaultTimeout);
        actionTimeouts = actionTimeouts == null ? Map.of() : Map.copyOf(actionTimeouts);
    }

    public Duration timeoutForAction(String syncAction) {
        if (syncAction == null) {
            return defaultTimeout;
        }
        return safe(actionTimeouts.get(syncAction), defaultTimeout);
    }

    private static Duration safe(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
