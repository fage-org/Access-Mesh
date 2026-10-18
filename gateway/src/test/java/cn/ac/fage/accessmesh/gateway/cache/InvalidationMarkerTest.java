package cn.ac.fage.accessmesh.gateway.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidationMarkerTest {

    private final InvalidationMarker marker = new InvalidationMarker();

    @Test
    void shouldInvalidateLoadTokenWhenKeyIsMarked() {
        InvalidationMarker.LoadToken token = marker.beginLoad("key-1");

        marker.mark("key-1");

        assertThat(marker.isCurrent(token)).isFalse();
        assertThat(marker.contains("key-1")).isTrue();
    }

    @Test
    void shouldInvalidateLoadTokenWhenGlobalEpochBumps() {
        InvalidationMarker.LoadToken token = marker.beginLoad("key-1");

        marker.clearAndBumpGlobalEpoch();

        assertThat(marker.isCurrent(token)).isFalse();
    }

    @Test
    void shouldCommitAndUnmarkOnlyWhenTokenIsCurrent() {
        InvalidationMarker.LoadToken token = marker.beginLoad("key-1");
        AtomicBoolean committed = new AtomicBoolean(false);

        boolean success = marker.commitIfCurrent(token, () -> committed.set(true));

        assertThat(success).isTrue();
        assertThat(committed).isTrue();
        assertThat(marker.contains("key-1")).isFalse();
    }

    @Test
    void shouldSkipCommitWhenTokenIsStale() {
        InvalidationMarker.LoadToken token = marker.beginLoad("key-1");
        marker.mark("key-1");
        AtomicBoolean committed = new AtomicBoolean(false);

        boolean success = marker.commitIfCurrent(token, () -> committed.set(true));

        assertThat(success).isFalse();
        assertThat(committed).isFalse();
        assertThat(marker.contains("key-1")).isTrue();
    }

    @Test
    void shouldCleanupOrphanedKeysWhenOversized() {
        marker.mark("live");
        marker.mark("orphan-1");
        marker.mark("orphan-2");

        marker.cleanupIfOversized(Set.of("live"));

        assertThat(marker.contains("live")).isTrue();
        assertThat(marker.contains("orphan-1")).isFalse();
        assertThat(marker.contains("orphan-2")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cleanupWithAnOldLiveSetMustNotReviveAnInvalidatedLoad(boolean oversized) {
        Set<String> earlierLiveKeys = Set.of();
        var token = marker.beginLoad("new-load");
        marker.mark("new-load");
        if (oversized) {
            marker.mark("orphan");
            marker.cleanupIfOversized(earlierLiveKeys);
        } else {
            marker.retainLiveKeys(earlierLiveKeys);
        }

        AtomicBoolean committed = new AtomicBoolean();
        assertThat(marker.commitIfCurrent(token, () -> committed.set(true))).isFalse();
        assertThat(committed).isFalse();
        assertThat(marker.commitIfCurrent(marker.beginLoad("new-load"), () -> {})).isTrue();
    }
}
