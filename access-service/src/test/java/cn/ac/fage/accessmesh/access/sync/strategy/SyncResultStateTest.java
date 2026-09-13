package cn.ac.fage.accessmesh.access.sync.strategy;

import cn.ac.fage.accessmesh.access.sync.strategy.SyncMappingsResult;
import cn.ac.fage.accessmesh.access.sync.strategy.SyncResourcesResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncResultStateTest {

    @Test
    void shouldInitializeIncomingKeys_whenUsingNoArgsConstructor() {
        SyncMappingsResult result = new SyncMappingsResult();

        assertNotNull(result.getIncomingKeys());
        assertTrue(result.getIncomingKeys().isEmpty());
    }

    @Test
    void shouldInitializeActiveResourceIds_whenUsingNoArgsConstructor() {
        SyncResourcesResult result = new SyncResourcesResult();

        assertNotNull(result.getActiveResourceIds());
        assertTrue(result.getActiveResourceIds().isEmpty());
    }
}