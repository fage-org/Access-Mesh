package cn.ac.fage.accessmesh.gateway.cache;

import org.junit.jupiter.api.Test;

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
}
