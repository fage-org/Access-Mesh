package cn.ac.fage.accessmesh.permission.util;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用树结构构建工具类
 * <p>
 * 提供从扁平列表构建层级树结构的通用方法。
 * 支持自定义的ID提取、父ID提取和节点构建逻辑。
 * 用于角色树、资源树等层级结构的构建。
 * </p>
 *
 * @param <E> 实体类型
 * @param <N> 节点类型
 */
public class TreeBuilder<E, N> {

    private final Function<E, Long> idExtractor;
    private final Function<E, Long> parentIdExtractor;
    private final BiFunction<E, List<N>, N> nodeBuilder;

    /**
     * 创建树结构构建器
     * <p>
     * 需要提供三个函数：ID提取、父ID提取和节点构建。
     * </p>
     *
     * @param idExtractor       实体ID提取函数
     * @param parentIdExtractor 父ID提取函数（根节点可返回null）
     * @param nodeBuilder       从实体和子节点列表构建节点的函数
     */
    public TreeBuilder(Function<E, Long> idExtractor,
                        Function<E, Long> parentIdExtractor,
                        BiFunction<E, List<N>, N> nodeBuilder) {
        this.idExtractor = idExtractor;
        this.parentIdExtractor = parentIdExtractor;
        this.nodeBuilder = nodeBuilder;
    }

    /**
     * 从指定根节点构建树结构
     * <p>
     * 以指定的实体为根，从所有实体列表中递归构建完整的树结构。
     * 使用预分组优化查询效率，实现O(1)的子节点查找。
     * </p>
     *
     * @param root        根实体
     * @param allEntities 所有实体列表（包含根节点和所有子孙节点）
     * @return 包含所有子孙节点的树节点
     */
    public N buildTree(E root, List<E> allEntities) {
        // 按parentId预分组，实现O(1)查找
        Map<Long, List<E>> byParentId = allEntities.stream()
            .collect(Collectors.groupingBy(
                e -> parentIdExtractor.apply(e) != null ? parentIdExtractor.apply(e) : -1L
            ));

        Set<Long> visited = new HashSet<>();
        return buildNode(root, byParentId, visited);
    }

    /**
     * 从多个根节点构建树结构列表
     * <p>
     * 以多个实体为根，从所有实体列表中递归构建多个树结构。
     * 用于构建森林结构的场景。
     * </p>
     *
     * @param roots       根实体列表
     * @param allEntities 所有实体列表（包含根节点和所有子孙节点）
     * @return 树节点列表
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

    /**
     * 递归构建单个节点及其子孙节点
     * <p>
     * 从指定实体开始，递归构建完整的节点结构。
     * 使用visited集合防止循环引用导致的无限递归。
     * </p>
     *
     * @param entity     当前实体
     * @param byParentId 按父ID分组的实体映射
     * @param visited    已访问的实体ID集合
     * @return 构建的节点及其所有子孙节点
     */
    private N buildNode(E entity, Map<Long, List<E>> byParentId, Set<Long> visited) {
        Long entityId = idExtractor.apply(entity);

        // 防止循环引用：检查是否已访问
        if (visited.contains(entityId)) {
            return nodeBuilder.apply(entity, List.of());
        }
        visited.add(entityId);

        List<E> childEntities = byParentId.getOrDefault(entityId, List.of());

        // 递归构建子节点
        List<N> children = childEntities.stream()
            .map(child -> buildNode(child, byParentId, visited))
            .collect(Collectors.toList());

        return nodeBuilder.apply(entity, children);
    }
}