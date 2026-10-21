package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.type.service.domain.ResourceAssociationDeclaration.Mode;
import java.util.Map;
import java.util.Set;

/** 目录适用性事实，不包含角色、操作或权限求值。 */
public final class ResourceAssociationRuntime {
    private ResourceAssociationRuntime() {}
    public record TypeRule(Long id, Integer value, String code, Mode mode, Set<String> parentTypes, boolean active) {
        public TypeRule { parentTypes = Set.copyOf(parentTypes); }
    }
    public record Context(Long primaryId, Map<Integer, TypeRule> types, Set<Integer> applicableTypes) {
        public Context { types = Map.copyOf(types); applicableTypes = Set.copyOf(applicableTypes); }
        public boolean related(Integer type) { return types.containsKey(type); }
        public boolean applicable(Integer type) { return applicableTypes.contains(type); }
    }
}
