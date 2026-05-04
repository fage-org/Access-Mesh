package cn.ac.fage.accessmesh.permission.util;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic tree builder utility for constructing hierarchical tree structures from flat lists.
 *
 * @param <E> Entity type
 * @param <N> Node type
 */
public class TreeBuilder<E, N> {

    private final Function<E, Long> idExtractor;
    private final Function<E, Long> parentIdExtractor;
    private final BiFunction<E, List<N>, N> nodeBuilder;

    /**
     * Creates a TreeBuilder with the required extractors.
     *
     * @param idExtractor Function to extract entity ID
     * @param parentIdExtractor Function to extract parent ID (may return null for root)
     * @param nodeBuilder Function to build a node from entity and its children
     */
    public TreeBuilder(Function<E, Long> idExtractor,
                        Function<E, Long> parentIdExtractor,
                        BiFunction<E, List<N>, N> nodeBuilder) {
        this.idExtractor = idExtractor;
        this.parentIdExtractor = parentIdExtractor;
        this.nodeBuilder = nodeBuilder;
    }

    /**
     * Builds a tree from a flat list of entities, starting from the specified root.
     *
     * @param root The root entity
     * @param allEntities All entities including root and descendants
     * @return The tree node representing the root with all descendants
     */
    public N buildTree(E root, List<E> allEntities) {
        // Pre-group entities by parentId for O(1) lookup
        Map<Long, List<E>> byParentId = allEntities.stream()
            .collect(Collectors.groupingBy(
                e -> parentIdExtractor.apply(e) != null ? parentIdExtractor.apply(e) : -1L
            ));

        Set<Long> visited = new HashSet<>();
        return buildNode(root, byParentId, visited);
    }

    /**
     * Builds a list of trees from multiple root entities.
     *
     * @param roots List of root entities
     * @param allEntities All entities including roots and descendants
     * @return List of tree nodes
     */
    public List<N> buildTrees(List<E> roots, List<E> allEntities) {
        Map<Long, List<E>> byParentId = allEntities.stream()
            .collect(Collectors.groupingBy(
                e -> parentIdExtractor.apply(e) != null ? parentIdExtractor.apply(e) : -1L
            ));

        Set<Long> visited = new HashSet<>();
        return roots.stream()
            .map(root -> buildNode(root, byParentId, visited))
            .collect(Collectors.toList());
    }

    private N buildNode(E entity, Map<Long, List<E>> byParentId, Set<Long> visited) {
        Long entityId = idExtractor.apply(entity);

        // 防止循环引用：检查是否已访问
        if (visited.contains(entityId)) {
            return nodeBuilder.apply(entity, List.of());
        }
        visited.add(entityId);

        List<E> childEntities = byParentId.getOrDefault(entityId, List.of());

        // Recursively build children first
        List<N> children = childEntities.stream()
            .map(child -> buildNode(child, byParentId, visited))
            .collect(Collectors.toList());

        return nodeBuilder.apply(entity, children);
    }
}