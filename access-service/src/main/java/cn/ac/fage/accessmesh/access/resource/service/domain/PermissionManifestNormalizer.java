package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.sync.CanonicalJson;
import cn.ac.fage.accessmesh.access.sync.PublicationGeneration;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 形状预检与完整/语义双指纹；任何数据库变更前执行。 */
@Component
public class PermissionManifestNormalizer {
    private final ObjectMapper mapper;
    private final Validator validator;
    public PermissionManifestNormalizer(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper.copy().setSerializationInclusion(JsonInclude.Include.ALWAYS);
        this.validator = validator;
    }
    public record Normalized(long generation, String revision, String payloadHash, String semanticHash,
                             List<DependencyCompiler.Declaration> declarations) {}

    public Normalized normalize(PermissionManifestReq request) {
        if (request == null || !validator.validate(request).isEmpty()) throw invalid("INVALID_MANIFEST");
        long generation;
        try { generation = PublicationGeneration.parse(request.publicationGeneration()); }
        catch (IllegalArgumentException e) { throw invalid("PUBLICATION_GENERATION_INVALID"); }
        Set<String> keys = new HashSet<>();
        List<DependencyCompiler.Declaration> declarations = new ArrayList<>();
        for (var dependency : request.dependencies()) {
            if (!keys.add(dependency.declarationKey())) throw invalid("DUPLICATE_DECLARATION_KEY");
            Set<ResourceKey> targets = new HashSet<>();
            for (var required : dependency.requires()) {
                if (!targets.add(required.target())) throw invalid("DUPLICATE_DECLARATION_TARGET");
                List<String> operations = required.operationCodes().stream().distinct().sorted(CanonicalJson::compareText).toList();
                declarations.add(new DependencyCompiler.Declaration(dependency.declarationKey(), dependency.source(),
                        dependency.sourceOperationCodes().isEmpty() ? null : dependency.sourceOperationCodes().getFirst(),
                        required.target(), operations, dependency.description()));
            }
        }
        declarations.sort(Comparator.comparing(d -> CanonicalJson.text(mapper.valueToTree(d)), CanonicalJson::compareText));
        // 双指纹分工：payload hash 含 description（锁完整请求身份，供同代次重放检测）；
        // graph hash 置空 description（锁图身份，展示字段变更不触发重编译、供 unchanged 短路判定）。二者不可互换。
        var payload = mapper.createObjectNode();
        payload.put("schemaVersion", request.schemaVersion());
        payload.put("revision", request.revision());
        payload.set("declarations", mapper.valueToTree(declarations));
        List<DependencyCompiler.Declaration> graph = declarations.stream().map(d -> new DependencyCompiler.Declaration(
                d.declarationKey(), d.source(), d.sourceOperationCode(), d.target(), d.requiredOperationCodes(), null))
                .sorted(Comparator.comparing(d -> CanonicalJson.text(mapper.valueToTree(d)), CanonicalJson::compareText)).toList();
        return new Normalized(generation, request.revision(), CanonicalJson.hash(payload),
                CanonicalJson.hash(mapper.valueToTree(graph)), List.copyOf(declarations));
    }
    /** 数据库存储与稳定贡献判定复用同一规范化，避免全局 JSON null 策略改变声明身份。 */
    public String declarationJson(DependencyCompiler.Declaration declaration) {
        return CanonicalJson.text(mapper.valueToTree(declaration));
    }
    public DependencyCompiler.Declaration readDeclaration(String json) {
        try { return mapper.readValue(json, DependencyCompiler.Declaration.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new cn.ac.fage.accessmesh.common.exception.SystemException(
                    AccessErrorCode.SYSTEM_INIT_FAILED.getCode(), "invalid stored dependency declaration", e);
        }
    }
    public String declarationSemanticHash(DependencyCompiler.Declaration declaration) {
        return CanonicalJson.hash(mapper.valueToTree(new DependencyCompiler.Declaration(
                declaration.declarationKey(), declaration.source(), declaration.sourceOperationCode(),
                declaration.target(), declaration.requiredOperationCodes(), null)));
    }

    private BizException invalid(String reason) {
        return new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(), reason);
    }
}
