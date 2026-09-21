package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 已装载事实的纯编译；不读取缓存/数据库，不按资源启停过滤，不物化角色权限。 */
@Component
public class DependencyCompiler {

    /** 编译拒绝原因值域（产出口径单源；消费方按 reason 分类 retryClass 时禁止对字面量比较）。 */
    public static final String REASON_TYPE_MISSING = "TYPE_MISSING";
    public static final String REASON_CROSS_OWNER = "CROSS_OWNER";
    public static final String REASON_RESOURCE_MISSING = "RESOURCE_MISSING";
    public static final String REASON_SELF_DEPENDENCY = "SELF_DEPENDENCY";
    public static final String REASON_OPERATION_INVALID = "OPERATION_INVALID";
    public static final String REASON_CYCLE = "CYCLE";

    public record Declaration(String declarationKey, ResourceKey source, String sourceOperationCode,
                              ResourceKey target, List<String> requiredOperationCodes, String description) {
        public Declaration { requiredOperationCodes = List.copyOf(requiredOperationCodes); }
        public String businessKey() {
            return SyncKeyCodecUtil.dependencyDeclarationBusinessKey(declarationKey,
                    target.resourceTypeCode(), target.resourceCode(), target.codeType());
        }
    }
    /** owner 为空表示非外部 SYNC 类型，不能用于依赖声明。 */
    public record TypeInfo(Integer value, String owner) {}
    public record Edge(Long sourceId, Long targetId, Long sourceOperationBits, long requiredOperationBits) {}
    public record Resolution(Declaration declaration, String reason, Edge edge) {}
    public record Result(List<Resolution> declarations, List<Edge> edges) {}
    private record OperationKey(Integer type, String code) {}
    private record EdgeKey(Long source, Long target, Long trigger) {}

    public Result compile(String sourceService, List<Declaration> declarations, Map<ResourceKey, Long> resources,
                          Map<String, TypeInfo> types, List<OperationPermission> operations,
                          List<Edge> retainedGraph, Set<String> unchangedResolvedKeys) {
        Map<OperationKey, Long> bits = new HashMap<>();
        for (OperationPermission operation : operations) {
            bits.put(new OperationKey(operation.getResourceType(), operation.getCode()), operation.getBinaryBit());
        }
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (Edge edge : retainedGraph) addEdge(graph, edge);
        Map<EdgeKey, Long> merged = new LinkedHashMap<>();
        List<Resolution> resolutions = new ArrayList<>();
        List<Declaration> ordered = new ArrayList<>(declarations);
        ordered.sort(Comparator.comparing((Declaration d) -> !unchangedResolvedKeys.contains(d.businessKey()))
                .thenComparing(Declaration::businessKey));
        for (Declaration declaration : ordered) {
            TypeInfo sourceType = types.get(declaration.source().resourceTypeCode());
            TypeInfo targetType = types.get(declaration.target().resourceTypeCode());
            String reason = null;
            Long sourceId = resources.get(declaration.source());
            Long targetId = resources.get(declaration.target());
            Long trigger = null;
            long required = 0;
            if (sourceType == null || targetType == null) {
                reason = REASON_TYPE_MISSING;
            } else if (!ownedBy(sourceService, sourceType) || !ownedBy(sourceService, targetType)
                    || ResourceTypeCode.API.equals(declaration.source().resourceTypeCode())
                    || ResourceTypeCode.API.equals(declaration.target().resourceTypeCode())) {
                reason = REASON_CROSS_OWNER;
            } else if (sourceId == null || targetId == null) {
                reason = REASON_RESOURCE_MISSING;
            } else if (Objects.equals(sourceId, targetId)) {
                reason = REASON_SELF_DEPENDENCY;
            } else {
                if (declaration.sourceOperationCode() != null) {
                    trigger = bits.get(new OperationKey(sourceType.value(), declaration.sourceOperationCode()));
                    if (trigger == null || trigger <= 0) reason = REASON_OPERATION_INVALID;
                }
                for (String code : declaration.requiredOperationCodes()) {
                    Long bit = bits.get(new OperationKey(targetType.value(), code));
                    if (bit == null || bit <= 0) reason = REASON_OPERATION_INVALID;
                    else required |= bit;
                }
                if (required == 0) reason = REASON_OPERATION_INVALID;
                if (reason == null && reachable(graph, targetId, sourceId)) reason = REASON_CYCLE;
            }
            if (reason != null) {
                resolutions.add(new Resolution(declaration, reason, null));
                continue;
            }
            Edge edge = new Edge(sourceId, targetId, trigger, required);
            resolutions.add(new Resolution(declaration, null, edge));
            addEdge(graph, edge);
            merged.merge(new EdgeKey(sourceId, targetId, trigger), required, (left, right) -> left | right);
        }
        List<Edge> resultEdges = merged.entrySet().stream()
                .map(e -> new Edge(e.getKey().source(), e.getKey().target(), e.getKey().trigger(), e.getValue()))
                .toList();
        return new Result(List.copyOf(resolutions), resultEdges);
    }

    private boolean ownedBy(String service, TypeInfo type) {
        return type.owner() != null && !type.owner().isBlank()
                && !LocalProjectionOwner.isLocalOwner(type.owner()) && type.owner().equals(service);
    }
    private void addEdge(Map<Long, Set<Long>> graph, Edge edge) {
        graph.computeIfAbsent(edge.sourceId(), key -> new HashSet<>()).add(edge.targetId());
    }
    private boolean reachable(Map<Long, Set<Long>> graph, Long from, Long target) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(from);
        while (!pending.isEmpty()) {
            Long current = pending.removeFirst();
            if (current.equals(target)) return true;
            if (visited.add(current)) pending.addAll(graph.getOrDefault(current, Set.of()));
        }
        return false;
    }
}
