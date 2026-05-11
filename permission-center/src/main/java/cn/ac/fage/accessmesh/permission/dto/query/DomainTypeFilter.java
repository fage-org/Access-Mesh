package cn.ac.fage.accessmesh.permission.dto.query;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 业务域类型过滤条件
 * <p>
 * 封装按业务域过滤资源类型的查询条件。
 * 支持三种过滤策略：不过滤、仅指定域、全局+指定域。
 * 调用者通过 apply 方法将过滤条件应用到 QueryWrapper，无需关心实现细节。
 * </p>
 */
public class DomainTypeFilter {

    /**
     * 是否不需要过滤
     */
    private final boolean noFilter;

    /**
     * 是否匹配不到任何结果
     */
    private final boolean matchNone;

    /**
     * IN 条件的资源类型值集合
     */
    private final Set<Integer> includeValues;

    /**
     * NOT IN 条件的资源类型值集合（用于全局域补集）
     */
    private final Set<Integer> excludeValues;

    private DomainTypeFilter(boolean noFilter, boolean matchNone, Set<Integer> includeValues, Set<Integer> excludeValues) {
        this.noFilter = noFilter;
        this.matchNone = matchNone;
        this.includeValues = includeValues != null ? Collections.unmodifiableSet(includeValues) : Set.of();
        this.excludeValues = excludeValues != null ? Collections.unmodifiableSet(excludeValues) : Set.of();
    }

    /**
     * 创建不过滤条件
     *
     * @return 不过滤的过滤条件
     */
    public static DomainTypeFilter noFilter() {
        return new DomainTypeFilter(true, false, Set.of(), Set.of());
    }

    /**
     * 创建空结果过滤条件
     *
     * @return 不匹配任何结果的过滤条件
     */
    public static DomainTypeFilter none() {
        return new DomainTypeFilter(false, true, Set.of(), Set.of());
    }

    /**
     * 创建仅指定域的过滤条件
     *
     * @param domainTypeValues 指定域声明的资源类型值集合
     * @return 仅指定域的过滤条件
     */
    public static DomainTypeFilter only(Set<Integer> domainTypeValues) {
        if (domainTypeValues == null || domainTypeValues.isEmpty()) {
            return none();
        }
        return new DomainTypeFilter(false, false, domainTypeValues, Set.of());
    }

    /**
     * 创建全局+指定域的过滤条件
     * <p>
     * 全局域范围 = 指定域类型 + 未被任何域认领的类型
     * 实现方式：IN (domainValues) OR NOT IN (allClaimedValues)
     * </p>
     *
     * @param domainTypeValues 指定域声明的资源类型值集合
     * @param allClaimedValues 所有业务域声明的资源类型值集合
     * @return 全局+指定域的过滤条件
     */
    public static DomainTypeFilter globalPlus(Set<Integer> domainTypeValues, Set<Integer> allClaimedValues) {
        Set<Integer> include = domainTypeValues != null ? domainTypeValues : Set.of();
        Set<Integer> exclude = allClaimedValues != null ? allClaimedValues : Set.of();
        if (include.isEmpty() && exclude.isEmpty()) {
            return noFilter();
        }
        return new DomainTypeFilter(false, false, include, exclude);
    }

    /**
     * 是否不需要过滤
     *
     * @return 不需要过滤返回true
     */
    public boolean isNoFilter() { return noFilter; }

    /**
     * 是否匹配不到任何结果
     *
     * @return 匹配不到任何结果返回true
     */
    public boolean isMatchNone() { return matchNone; }

    /**
     * 获取 IN 条件的资源类型值集合
     *
     * @return 资源类型值集合
     */
    public Set<Integer> getIncludeValues() { return includeValues; }

    /**
     * 获取 NOT IN 条件的资源类型值集合
     *
     * @return 资源类型值集合
     */
    public Set<Integer> getExcludeValues() { return excludeValues; }

    /**
     * 判断给定类型值是否命中过滤条件
     *
     * @param typeValue 类型值
     * @return 命中过滤条件返回true
     */
    public boolean matches(Integer typeValue) {
        if (noFilter) {
            return true;
        }
        if (matchNone || typeValue == null) {
            return false;
        }
        if (includeValues.contains(typeValue)) {
            return true;
        }
        return !excludeValues.isEmpty() && !excludeValues.contains(typeValue);
    }

    /**
     * 将两个过滤条件合并（取并集）
     * <p>
     * 用于同一查询中需要合并多个域的场景
     * </p>
     *
     * @param other 另一个过滤条件
     * @return 合并后的过滤条件
     */
    public DomainTypeFilter merge(DomainTypeFilter other) {
        if (other == null) return this;
        if (this.noFilter || other.noFilter) return noFilter();
        if (this.matchNone) return other;
        if (other.matchNone) return this;

        Set<Integer> mergedInclude = new HashSet<>(this.includeValues);
        mergedInclude.addAll(other.includeValues);
        Set<Integer> mergedExclude = new HashSet<>(this.excludeValues);
        mergedExclude.addAll(other.excludeValues);
        // 合并后 include 和 exclude 可能有交集，需要从 exclude 中移除 include 的部分
        mergedExclude.removeAll(mergedInclude);
        return new DomainTypeFilter(false, false, mergedInclude, mergedExclude);
    }
}
