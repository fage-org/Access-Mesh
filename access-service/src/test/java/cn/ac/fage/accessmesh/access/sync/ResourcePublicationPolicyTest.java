package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationPolicy;
import cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationState;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadata;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationPolicy.Decision.*;

class ResourcePublicationPolicyTest {
    @Test void shouldProtectNewerIncrement_withoutDroppingOutOfOrderOtherKey() {
        var state = state(43, null, null);
        assertThat(ResourcePublicationPolicy.full(state, 42, "full")).isEqualTo(STALE);
        assertThat(ResourcePublicationPolicy.single(state, null, 42L, "a")).isEqualTo(APPLY);
    }
    @Test void shouldFenceAllOldIncrements_afterFullIncludingMissingKeys() {
        var state = state(42, 42L, "full");
        assertThat(ResourcePublicationPolicy.single(state, null, 41L, "old")).isEqualTo(STALE);
        assertThat(ResourcePublicationPolicy.single(state, null, 42L, "equal")).isEqualTo(STALE);
        assertThat(ResourcePublicationPolicy.single(state, null, 43L, "new")).isEqualTo(APPLY);
        assertThat(ResourcePublicationPolicy.single(state, null, null, "legacy")).isEqualTo(GENERATION_REQUIRED);
    }
    @Test void shouldRetrySameFullOnlyUntilAnotherPublicationSucceeds() {
        var state = state(42, 42L, "full");
        assertThat(ResourcePublicationPolicy.full(state, 42, "full")).isEqualTo(APPLY);
        assertThat(ResourcePublicationPolicy.full(state, 42, "changed")).isEqualTo(CONFLICT);
        state.setMaxGeneration(43L);
        assertThat(ResourcePublicationPolicy.full(state, 42, "full")).isEqualTo(STALE);
    }
    @Test void shouldCompareSameKeyContentAndKeepPureLegacyMode() {
        var metadata = new SyncMetadata();
        metadata.setLastPublicationGeneration(42L);
        metadata.setLastPublicationHash("same");
        assertThat(ResourcePublicationPolicy.single(null, null, null, "legacy")).isEqualTo(APPLY);
        assertThat(ResourcePublicationPolicy.single(state(42, null, null), metadata, 42L, "same")).isEqualTo(UNCHANGED);
        assertThat(ResourcePublicationPolicy.single(state(42, null, null), metadata, 42L, "other")).isEqualTo(CONFLICT);
    }
    private ResourcePublicationState state(long maximum, Long full, String hash) {
        var state = new ResourcePublicationState();
        state.setMaxGeneration(maximum);
        state.setLastFullGeneration(full);
        state.setLastFullPayloadHash(hash);
        return state;
    }
}
