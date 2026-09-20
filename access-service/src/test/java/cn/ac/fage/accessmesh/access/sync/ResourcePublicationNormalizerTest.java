package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourcePublicationNormalizerTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 21, 0, 0);
    private final ResourcePublicationNormalizer normalizer = new ResourcePublicationNormalizer(new ObjectMapper());

    @Test void shouldIgnoreGenerationAndObjectKeyOrder_butPreserveArrayOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", List.of(1, 2)); first.put("a", true);
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("a", true); reordered.put("z", List.of(1, 2));
        assertThat(normalizer.hash(request(first, "1", AT))).isEqualTo(normalizer.hash(request(reordered, "2", AT)));
        reordered.put("z", List.of(2, 1));
        assertThat(normalizer.hash(request(first, "1", AT))).isNotEqualTo(normalizer.hash(request(reordered, "2", AT)));
    }

    @Test void shouldDetachNestedExtraFromCallerBeforeHashAndWrite() {
        Map<String, Object> child = new LinkedHashMap<>(); child.put("value", 1);
        List<Object> array = new ArrayList<>(); array.add(child);
        Map<String, Object> extra = new LinkedHashMap<>(); extra.put("nested", array);
        var original = request(extra, "1", AT);
        String before = normalizer.hash(original);
        var snapshot = normalizer.snapshot(original);
        child.put("value", 2); array.add("changed"); extra.put("another", true);
        assertThat(normalizer.hash(snapshot)).isEqualTo(before);
        assertThat(normalizer.hash(original)).isNotEqualTo(before);
    }

    @Test void shouldSortFullBusinessKeysAndRejectNormalizedDuplicateKeys() {
        var a = item("a", null);
        var b = item("b", null);
        var scope = new ResourceEntitySyncScope("reports", "REPORT");
        assertThat(normalizer.fullHash(new ResourceEntityFullSyncReq(scope, List.of(a, b), "1")))
                .isEqualTo(normalizer.fullHash(new ResourceEntityFullSyncReq(scope, List.of(b, a), "2")));
        assertThatThrownBy(() -> normalizer.fullHash(new ResourceEntityFullSyncReq(scope, List.of(a, item("a", "default")), "1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("DUPLICATE_BUSINESS_KEY");
    }

    @Test void shouldUsePostgresMicrosecondIdentity_inFingerprint() {
        assertThat(normalizer.hash(request(null, "1", AT.plusNanos(499))))
                .isEqualTo(normalizer.hash(request(null, "1", AT)));
        assertThat(normalizer.hash(request(null, "1", AT.plusNanos(501))))
                .isEqualTo(normalizer.hash(request(null, "1", AT.plusNanos(1000))))
                .isNotEqualTo(normalizer.hash(request(null, "1", AT)));
    }

    @Test void shouldConfirmOmittedValuesAndOrderedExtra_withoutChangingParentMeaning() {
        var current = new ResourceEntity();
        current.setName("stored"); current.setPath("/stored"); current.setStatus(0);
        current.setExtra("{\"a\":true,\"z\":[1,2]}");
        assertThat(normalizer.matchesExisting(request(Map.of("z", List.of(1, 2), "a", true), "1", AT), current, null)).isTrue();
        assertThat(normalizer.matchesExisting(request(Map.of("z", List.of(2, 1), "a", true), "1", AT), current, null)).isFalse();
        current.setParentId(99L);
        assertThat(normalizer.matchesExisting(request(null, "1", AT), current, null)).isFalse();
    }

    private ResourceEntitySyncReq request(Map<String, Object> extra, String generation, LocalDateTime at) {
        return new ResourceEntitySyncReq("UPSERT", "REPORT", "a", null, null, null, null, null,
                null, null, extra, "reports", null, null, new SyncVersionRef(at, 1L), generation);
    }
    private ResourceEntitySyncItem item(String code, String codeType) {
        return new ResourceEntitySyncItem(code, codeType, null, null, null, null, null, null,
                null, null, null, new SyncVersionRef(AT, 1L));
    }
}
