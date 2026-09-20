package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import jakarta.validation.Validator;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 仅本地形状/重复键/自环与清单内环；平台仍独占事实、所有权与联合图校验。 */
final class ManifestValidation {
    private final Validator validator;
    ManifestValidation(Validator validator) { this.validator = validator; }
    void validate(PermissionManifestReq manifest) {
        if (manifest == null || !validator.validate(manifest).isEmpty()) throw invalid("INVALID_MANIFEST");
        try { if (Long.parseLong(manifest.publicationGeneration()) <= 0) throw invalid("INVALID_GENERATION"); }
        catch (NumberFormatException e) { throw invalid("INVALID_GENERATION"); }
        Set<String> declarations = new HashSet<>();
        Map<ResourceKey, Set<ResourceKey>> graph = new HashMap<>();
        for (var declaration : manifest.dependencies()) {
            if (!declarations.add(declaration.declarationKey())) throw invalid("DUPLICATE_DECLARATION_KEY");
            Set<ResourceKey> targets = new HashSet<>();
            for (var requirement : declaration.requires()) {
                if (!targets.add(requirement.target())) throw invalid("DUPLICATE_DECLARATION_TARGET");
                if (declaration.source().equals(requirement.target())) throw invalid("SELF_DEPENDENCY");
                if (reachable(graph, requirement.target(), declaration.source())) throw invalid("CYCLE");
                graph.computeIfAbsent(declaration.source(), ignored -> new HashSet<>()).add(requirement.target());
            }
        }
    }
    private boolean reachable(Map<ResourceKey, Set<ResourceKey>> graph, ResourceKey source, ResourceKey target) {
        Set<ResourceKey> seen = new HashSet<>();
        ArrayDeque<ResourceKey> pending = new ArrayDeque<>(); pending.add(source);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (current.equals(target)) return true;
            if (seen.add(current)) pending.addAll(graph.getOrDefault(current, Set.of()));
        }
        return false;
    }
    private IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
}
