package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Dependency;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Requirement;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionManifestNormalizerTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final PermissionManifestNormalizer normalizer = new PermissionManifestNormalizer(new ObjectMapper(), FACTORY.getValidator());
    @AfterAll static void close() { FACTORY.close(); }

    @Test
    void shouldNormalizeOrderingDuplicatesAndWildcard_withoutHashingGeneration() {
        var first = normalizer.normalize(new PermissionManifestReq(1, "41", "r", List.of(
                dependency("b", null, "b", List.of("UPDATE", "VIEW", "VIEW"), null),
                dependency("a", List.of(), "a", List.of("VIEW"), null))));
        var second = normalizer.normalize(new PermissionManifestReq(1, "42", "r", List.of(
                dependency("a", null, "a", List.of("VIEW"), null),
                dependency("b", List.of(), "b", List.of("VIEW", "UPDATE"), null))));
        assertThat(first.payloadHash()).isEqualTo(second.payloadHash());
        assertThat(first.semanticHash()).isEqualTo(second.semanticHash());
        assertThat(first.declarations()).hasSize(2);
        assertThat(second.generation()).isEqualTo(42);
    }

    @Test
    void shouldKeepDescriptionAndRevisionInPayloadIdentity_butNotInGraphIdentity() {
        var first = normalizer.normalize(new PermissionManifestReq(1, "41", "r1",
                List.of(dependency("a", List.of("VIEW"), "a", List.of("VIEW"), "first"))));
        var second = normalizer.normalize(new PermissionManifestReq(1, "42", "r2",
                List.of(dependency("a", List.of("VIEW"), "a", List.of("VIEW"), "renamed"))));
        assertThat(first.payloadHash()).isNotEqualTo(second.payloadHash());
        assertThat(first.semanticHash()).isEqualTo(second.semanticHash());
    }

    @Test
    void shouldRejectDuplicateKeysAndMultipleTriggers_beforeCompilation() {
        var duplicate = dependency("a", null, "a", List.of("VIEW"), null);
        assertThatThrownBy(() -> normalizer.normalize(new PermissionManifestReq(1, "1", "r", List.of(duplicate, duplicate))))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> normalizer.normalize(new PermissionManifestReq(1, "1", "r",
                List.of(dependency("a", List.of("VIEW", "UPDATE"), "a", List.of("VIEW"), null)))))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> normalizer.normalize(new PermissionManifestReq(1, "9223372036854775808", "r", List.of())))
                .isInstanceOf(BizException.class).hasMessageContaining("PUBLICATION_GENERATION_INVALID");
    }

    @Test
    void shouldAcceptExplicitEmptySnapshot_butRejectNullAndInvalidSchema() {
        assertThat(normalizer.normalize(new PermissionManifestReq(1, "1", "r", List.of())).declarations()).isEmpty();
        assertThatThrownBy(() -> normalizer.normalize(new PermissionManifestReq(1, "1", "r", null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> normalizer.normalize(new PermissionManifestReq(2, "1", "r", List.of()))).isInstanceOf(BizException.class);
    }

    @Test
    void shouldHashCanonicalUtf8Declarations_insteadOfEncodedBusinessKeyOrder() {
        var result = normalizer.normalize(new PermissionManifestReq(1, "1", "r", List.of(
                dependency("é", null, "a", List.of("VIEW"), null),
                dependency("A", null, "a", List.of("VIEW"), null))));
        assertThat(result.semanticHash()).isEqualTo("2cfe10ce029e5c33038cc25cd9196ad216e0ab38bee097ead452d6199117193c");
    }

    @Test
    void shouldUseOneCanonicalDeclarationIdentity_acrossJsonNullPolicies() {
        var alternate = new PermissionManifestNormalizer(new ObjectMapper().setSerializationInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL), FACTORY.getValidator());
        var req = new PermissionManifestReq(1, "1", "r", List.of(dependency("a", null, "a", List.of("VIEW"), null)));
        var first = normalizer.normalize(req);
        var second = alternate.normalize(req);
        assertThat(first.payloadHash()).isEqualTo(second.payloadHash());
        assertThat(normalizer.declarationJson(first.declarations().getFirst()))
                .isEqualTo(alternate.declarationJson(second.declarations().getFirst()));
        assertThat(normalizer.declarationSemanticHash(first.declarations().getFirst()))
                .isEqualTo(alternate.declarationSemanticHash(second.declarations().getFirst()));
    }

    private Dependency dependency(String key, List<String> trigger, String target, List<String> operations, String description) {
        return new Dependency(key, new ResourceKey("REPORT", "root", null), trigger,
                List.of(new Requirement(new ResourceKey("REPORT", target, null), operations)), description);
    }
}
