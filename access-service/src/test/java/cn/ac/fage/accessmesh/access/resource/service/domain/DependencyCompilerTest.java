package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyCompilerTest {
    private final DependencyCompiler compiler = new DependencyCompiler();
    private final ResourceKey a = new ResourceKey("REPORT", "a", null);
    private final ResourceKey b = new ResourceKey("REPORT", "b", null);
    private final ResourceKey c = new ResourceKey("REPORT", "c", null);

    @Test
    void shouldMergeRequiredOperationsAcrossDeclarations_withExactEdgeKey() {
        var result = compiler.compile("reports", List.of(
                declaration("first", a, b, "VIEW", List.of("VIEW")),
                declaration("second", a, b, "VIEW", List.of("UPDATE")),
                declaration("wildcard", a, b, null, List.of("VIEW"))),
                resources(), types(), operations(), List.of(), Set.of());
        assertThat(result.declarations()).allMatch(d -> d.reason() == null);
        assertThat(result.edges()).hasSize(2);
        assertThat(result.edges()).anyMatch(e -> Long.valueOf(2).equals(e.sourceOperationBits()) && e.requiredOperationBits() == 6L);
        assertThat(result.edges()).anyMatch(e -> e.sourceOperationBits() == null && e.requiredOperationBits() == 2L);
    }

    @Test
    void shouldKeepEstablishedContribution_whenChangedDeclarationWouldCloseCycle() {
        var stable = declaration("z-stable", a, b, null, List.of("VIEW"));
        var changed = declaration("a-new", b, a, null, List.of("VIEW"));
        var result = compiler.compile("reports", List.of(changed, stable), resources(), types(), operations(),
                List.of(), Set.of(stable.businessKey()));
        assertThat(result.declarations()).anyMatch(d -> d.declaration().equals(changed) && "CYCLE".equals(d.reason()));
        assertThat(result.declarations()).anyMatch(d -> d.declaration().equals(stable) && d.reason() == null);
        assertThat(result.edges()).singleElement().satisfies(e -> {
            assertThat(e.sourceId()).isEqualTo(1L);
            assertThat(e.targetId()).isEqualTo(2L);
        });
    }

    @Test
    void shouldRejectMissingOwnershipOperationAndSelfReferences_withoutAddingEdges() {
        var foreign = new ResourceKey("FOREIGN", "f", null);
        var unknown = new ResourceKey("UNKNOWN", "u", null);
        var result = compiler.compile("reports", List.of(
                declaration("owner", a, foreign, null, List.of("VIEW")),
                declaration("type", a, unknown, null, List.of("VIEW")),
                declaration("resource", a, c, null, List.of("VIEW")),
                declaration("operation", a, b, "MISSING", List.of("VIEW")),
                declaration("self", a, a, null, List.of("VIEW"))),
                resources(), Map.of("REPORT", new DependencyCompiler.TypeInfo(100, "reports"),
                        "FOREIGN", new DependencyCompiler.TypeInfo(101, "other")), operations(), List.of(), Set.of());
        assertThat(result.edges()).isEmpty();
        assertThat(result.declarations()).extracting(DependencyCompiler.Resolution::reason)
                .containsExactlyInAnyOrder("CROSS_OWNER", "TYPE_MISSING", "RESOURCE_MISSING", "OPERATION_INVALID", "SELF_DEPENDENCY");
    }

    @Test
    void shouldRejectCycleThroughRetainedGraph_andPreserveRetainedGraph() {
        var existing = new DependencyCompiler.Edge(2L, 1L, null, 2L);
        var result = compiler.compile("reports", List.of(declaration("cycle", a, b, null, List.of("VIEW"))),
                resources(), types(), operations(), List.of(existing), Set.of());
        assertThat(result.declarations()).singleElement().extracting(DependencyCompiler.Resolution::reason).isEqualTo("CYCLE");
        assertThat(result.edges()).isEmpty();
    }

    private DependencyCompiler.Declaration declaration(String key, ResourceKey source, ResourceKey target,
                                                        String trigger, List<String> required) {
        return new DependencyCompiler.Declaration(key, source, trigger, target, required, null);
    }

    private Map<ResourceKey, Long> resources() { return Map.of(a, 1L, b, 2L); }
    private Map<String, DependencyCompiler.TypeInfo> types() {
        return Map.of("REPORT", new DependencyCompiler.TypeInfo(100, "reports"));
    }
    private List<OperationPermission> operations() {
        return List.of(operation("VIEW", 2), operation("UPDATE", 4));
    }
    private OperationPermission operation(String code, long bit) {
        var operation = new OperationPermission();
        operation.setResourceType(100);
        operation.setCode(code);
        operation.setBinaryBit(bit);
        return operation;
    }
}
