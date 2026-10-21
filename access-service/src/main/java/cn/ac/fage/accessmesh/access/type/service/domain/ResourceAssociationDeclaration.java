package cn.ac.fage.accessmesh.access.type.service.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.Set;

/** 类型关系声明的统一解析；未知模式不能退化成无限制目录。 */
public record ResourceAssociationDeclaration(Mode mode, Set<String> parentTypeCodes) {
    public enum Mode { NONE, EXCLUSIVE, SHARED }
    public static final ResourceAssociationDeclaration NONE = new ResourceAssociationDeclaration(Mode.NONE, Set.of());

    public ResourceAssociationDeclaration {
        parentTypeCodes = Set.copyOf(parentTypeCodes);
    }

    public static ResourceAssociationDeclaration parse(ObjectMapper mapper, String extra) {
        if (extra == null || extra.isBlank()) return NONE;
        try {
            return from(mapper.readTree(extra));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("资源关系声明不是合法 JSON", exception);
        }
    }

    public static ResourceAssociationDeclaration from(JsonNode root) {
        JsonNode modeNode = root == null ? null : root.get("associationMode");
        JsonNode parentsNode = root == null ? null : root.get("associationParentTypeCodes");
        if (modeNode == null && parentsNode == null) return NONE;
        if (modeNode == null || !modeNode.isTextual()) {
            throw new IllegalArgumentException("associationMode 必须是 NONE/EXCLUSIVE/SHARED");
        }
        Mode mode;
        try { mode = Mode.valueOf(modeNode.asText()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("未知 associationMode", exception);
        }
        if (mode == Mode.NONE) {
            if (parentsNode != null) throw new IllegalArgumentException("NONE 不允许声明 associationParentTypeCodes");
            return NONE;
        }
        if (parentsNode == null || !parentsNode.isArray() || parentsNode.isEmpty() || parentsNode.size() > 200) {
            throw new IllegalArgumentException("associationParentTypeCodes 必须为非空数组，最多 200 项");
        }
        Set<String> parents = new LinkedHashSet<>();
        for (JsonNode parent : parentsNode) {
            if (!parent.isTextual() || !parent.asText().matches("[A-Z][A-Z0-9_]{0,63}")
                || !parents.add(parent.asText())) {
                throw new IllegalArgumentException("associationParentTypeCodes 含非法或重复类型编码");
            }
        }
        return new ResourceAssociationDeclaration(mode, parents);
    }
}
