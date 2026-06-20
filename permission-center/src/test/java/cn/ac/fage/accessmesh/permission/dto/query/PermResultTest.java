package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.vo.RolePermEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermResultTest {

    @Test
    void shouldDefensivelyCopyCollections_whenBuildingPermResult() {
        RolePermEntry entry = new RolePermEntry(1L, 2L, 3L, "resource:a", 4, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, false);
        List<RolePermEntry> instanceEntries = new ArrayList<>();
        instanceEntries.add(entry);

        AbstractRole role = new AbstractRole();
        role.setId(2L);

        Map<Long, AbstractRole> roleMap = new LinkedHashMap<>();
        roleMap.put(role.getId(), role);

        PermResult result = PermResult.builder(true, null)
            .instanceEntries(instanceEntries)
            .roleMap(roleMap)
            .build();

        instanceEntries.clear();
        roleMap.clear();

        assertEquals(1, result.instanceEntries().size());
        assertEquals(1, result.roleMap().size());
        assertThrows(UnsupportedOperationException.class, () -> result.instanceEntries().add(entry));
        assertThrows(UnsupportedOperationException.class, () -> result.roleMap().clear());
    }

    @Test
    void shouldNormalizeNullListsToEmpty_whenBuildingPermResult() {
        PermResult result = PermResult.builder(true, null)
            .scopeAllEntries(null)
            .instanceEntries(null)
            .build();

        assertTrue(result.scopeAllEntries().isEmpty());
        assertTrue(result.instanceEntries().isEmpty());
        assertTrue(result.allEntries().isEmpty());
    }
}