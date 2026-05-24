package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
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
        RolePermEntry entry = new RolePermEntry(1L, 2L, 3L, "resource:a", 4, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, false);
        List<RolePermEntry> entries = new ArrayList<>();
        entries.add(entry);

        ResourceEntity resource = new ResourceEntity();
        resource.setId(3L);

        Map<Long, ResourceEntity> resourceMap = new LinkedHashMap<>();
        resourceMap.put(resource.getId(), resource);

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