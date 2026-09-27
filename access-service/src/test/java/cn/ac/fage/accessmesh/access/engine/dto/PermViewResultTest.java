package cn.ac.fage.accessmesh.access.engine.dto;

import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.ResourceDescription;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermViewResultTest {

    @Test
    void shouldDefensivelyCopyCollections_whenBuildingPermViewResult() {
        GrantFact entry = new GrantFact(1L, 2L, 4, 3L, 1L, false, false, null, false, null, "MANUAL");
        List<GrantFact> entries = new ArrayList<>();
        entries.add(entry);

        ResourceDescription resource = new ResourceDescription(3L, 4, "resource:a", "default",
            null, null, null, null, null, null, null);

        Map<Long, ResourceDescription> resourceMap = new LinkedHashMap<>();
        resourceMap.put(resource.id(), resource);

        PermViewResult result = PermViewResult.builder()
            .entries(entries)
            .resourceMap(resourceMap)
            .build();

        entries.clear();
        resourceMap.clear();

        assertEquals(1, result.getEntries().size());
        assertEquals(1, result.getResourceMap().size());
        assertThrows(UnsupportedOperationException.class, () -> result.getEntries().add(entry));
        assertThrows(UnsupportedOperationException.class, () -> result.getResourceMap().clear());
    }

    @Test
    void shouldNormalizeNullCollectionsToEmpty_whenBuildingPermViewResult() {
        PermViewResult result = PermViewResult.builder()
            .entries(null)
            .resourceMap(null)
            .sourceRoleMap(null)
            .build();

        assertTrue(result.getEntries().isEmpty());
        assertTrue(result.getResourceMap().isEmpty());
        assertTrue(result.getSourceRoleMap().isEmpty());
    }
}
