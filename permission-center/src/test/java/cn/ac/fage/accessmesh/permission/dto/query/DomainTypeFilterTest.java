package cn.ac.fage.accessmesh.permission.dto.query;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTypeFilterTest {

    @Test
    void shouldMatchSpecificAndGlobalTypesInGlobalPlusMode() {
        DomainTypeFilter filter = DomainTypeFilter.globalPlus(Set.of(1), Set.of(1, 2));

        assertTrue(filter.matches(1));
        assertFalse(filter.matches(2));
        assertTrue(filter.matches(3));
    }

    @Test
    void shouldMatchNothingWhenFilterIsNone() {
        DomainTypeFilter filter = DomainTypeFilter.none();

        assertTrue(filter.isMatchNone());
        assertFalse(filter.matches(1));
    }

    @Test
    void shouldDegradeToNoFilterWhenGlobalPlusHasNoConstraints() {
        DomainTypeFilter filter = DomainTypeFilter.globalPlus(Set.of(), Set.of());

        assertTrue(filter.isNoFilter());
        assertTrue(filter.matches(99));
    }
}