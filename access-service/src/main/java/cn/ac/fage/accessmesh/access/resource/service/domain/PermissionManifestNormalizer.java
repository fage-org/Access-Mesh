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
            ResourceKey source = normalizeKey(dependency.source());
            Set<ResourceKey> targets = new HashSet<>();
            for (var required : dependency.requires()) {
                // T-PERM-100 外评收口：目标去重、声明构造与双指纹均在归一后资源键上进行
                // （perm-common DTO 不动，装载侧归一）——带空白与干净形态为同一声明身份
                ResourceKey target = normalizeKey(required.target());
                if (!targets.add(target)) throw invalid("DUPLICATE_DECLARATION_TARGET");
                List<String> operations = required.operationCodes().stream().distinct().sorted(CanonicalJson::compareText).toList();
                declarations.add(new DependencyCompiler.Declaration(dependency.declarationKey(), source,
                        dependency.sourceOperationCodes().isEmpty() ? null : dependency.sourceOperationCodes().getFirst(),
                        target, operations, dependency.description()));
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

    /** T-PERM-100 外评收口：manifest 资源键 codeType 归一（null/空白→default、去首尾空白，§12.1 同源） */
    private static ResourceKey normalizeKey(ResourceKey key) {
        String codeType = key.codeType() == null || key.codeType().isBlank()
                ? cn.ac.fage.accessmesh.access.projection.PermConstants.CodeType.DEFAULT : key.codeType().trim();
        return codeType.equals(key.codeType()) ? key
                : new ResourceKey(key.resourceTypeCode(), key.resourceCode(), codeType);
    }
}
