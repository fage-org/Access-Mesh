package cn.ac.fage.accessmesh.permission.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限中心缓存目录
 * <p>
 * 定义 permission-center 模块的所有缓存条目。
 * 每种缓存只需一个静态常量，携带完整的配置信息。
 * </p>
 *
 * <h3>首批缓存条目：</h3>
 * <table border="1">
 *   <tr><th>常量名</th><th>code</th><th>模式</th><th>L1 TTL</th><th>L2 TTL</th></tr>
 *   <tr><td>EFFECTIVE_ROLES</td><td>perm:effective-roles</td><td>L1_L2</td><td>5min</td><td>30min</td></tr>
 *   <tr><td>ROLE_PERM_SNAPSHOT</td><td>perm:role-perm-snapshot</td><td>L1_L2</td><td>5min</td><td>30min</td></tr>
 *   <tr><td>PERMISSION_VERSION</td><td>perm:permission-version</td><td>L2_ONLY</td><td>-</td><td>60min</td></tr>
 *   <tr><td>INTERFACE_SNAPSHOT</td><td>perm:interface-snapshot</td><td>L1_L2</td><td>10min</td><td>60min</td></tr>
 *   <tr><td>TYPE_VALUE</td><td>perm:type-value</td><td>L1_L2</td><td>30min</td><td>120min</td></tr>
 *   <tr><td>TYPE_CODE</td><td>perm:type-code</td><td>L1_L2</td><td>30min</td><td>120min</td></tr>
 *   <tr><td>CONDITION_RULES</td><td>perm:condition-rules</td><td>L1_L2</td><td>10min</td><td>30min</td></tr>
 *   <tr><td>ROLE_MUTEX_RULE</td><td>perm:role-mutex-rule</td><td>L1_L2</td><td>10min</td><td>30min</td></tr>
 * </table>
 */
public final class PermCacheCatalog {

    private PermCacheCatalog() {
    }

    /**
     * 用户有效角色缓存
     * <p>
     * Key: userId
     * Value: Set<Long> 角色ID集合
     * </p>
     */
    public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
        CacheCatalogEntry.<Set<Long>>builder()
            .code("perm:effective-roles")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(5)
            .l1MaxSize(2000)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<Set<Long>>() {})
            .build();

    /**
     * 角色权限快照缓存
     * <p>
     * Key: roleId
     * Value: RolePermSnapshot
     * </p>
     */
    public static final CacheCatalogEntry<cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot> ROLE_PERM_SNAPSHOT =
        CacheCatalogEntry.<cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot>builder()
            .code("perm:role-perm-snapshot")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(5)
            .l1MaxSize(2000)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot>() {})
            .build();

    /**
     * 权限版本号缓存
     * <p>
     * Key: roleId
     * Value: Long 版本号
     * </p>
     * <p>
     * 使用 L2_ONLY 模式，版本号需要跨节点一致性，不应缓存到 L1。
     * </p>
     */
    public static final CacheCatalogEntry<Long> PERMISSION_VERSION =
        CacheCatalogEntry.<Long>builder()
            .code("perm:permission-version")
            .mode(CacheMode.L2_ONLY)
            .l2TtlMinutes(60)
            .valueType(new TypeRef<Long>() {})
            .build();

    /**
     * 接口权限快照缓存
     * <p>
     * Key: serviceCode
     * Value: InterfaceSnapshot
     * </p>
     */
    public static final CacheCatalogEntry<cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot> INTERFACE_SNAPSHOT =
        CacheCatalogEntry.<cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot>builder()
            .code("perm:interface-snapshot")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(10)
            .l1MaxSize(500)
            .l2TtlMinutes(60)
            .valueType(new TypeRef<cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot>() {})
            .build();

    /**
     * 类型值映射缓存
     * <p>
     * Key: typeDefId + typeCode（复合键，使用字符串表示）
     * Value: Map<String, Integer> 类型码 -> 类型值
     * </p>
     */
    public static final CacheCatalogEntry<Map<String, Integer>> TYPE_VALUE =
        CacheCatalogEntry.<Map<String, Integer>>builder()
            .code("perm:type-value")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(30)
            .l1MaxSize(500)
            .l2TtlMinutes(120)
            .valueType(new TypeRef<Map<String, Integer>>() {})
            .build();

    /**
     * 类型码缓存
     * <p>
     * Key: typeDefId + typeValue
     * Value: String 类型码
     * </p>
     */
    public static final CacheCatalogEntry<String> TYPE_CODE =
        CacheCatalogEntry.<String>builder()
            .code("perm:type-code")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(30)
            .l1MaxSize(500)
            .l2TtlMinutes(120)
            .valueType(new TypeRef<String>() {})
            .build();

    /**
     * 条件规则缓存
     * <p>
     * Key: conditionId
     * Value: JsonNode 规则 JSON
     * </p>
     */
    public static final CacheCatalogEntry<JsonNode> CONDITION_RULES =
        CacheCatalogEntry.<JsonNode>builder()
            .code("perm:condition-rules")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(10)
            .l1MaxSize(1000)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<JsonNode>() {})
            .build();

    /**
     * 角色互斥规则缓存
     * <p>
     * Key: "all"（单一键）
     * Value: String JSON数组格式的互斥规则
     * </p>
     */
    public static final CacheCatalogEntry<String> ROLE_MUTEX_RULE =
        CacheCatalogEntry.<String>builder()
            .code("perm:role-mutex-rule")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(10)
            .l1MaxSize(100)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<String>() {})
            .build();

    /**
     * 操作码缓存
     * <p>
     * Key: operationId
     * Value: String 操作码
     * </p>
     */
    public static final CacheCatalogEntry<String> OPERATION_CODE =
        CacheCatalogEntry.<String>builder()
            .code("perm:operation-code")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(30)
            .l1MaxSize(500)
            .l2TtlMinutes(120)
            .valueType(new TypeRef<String>() {})
            .build();
}