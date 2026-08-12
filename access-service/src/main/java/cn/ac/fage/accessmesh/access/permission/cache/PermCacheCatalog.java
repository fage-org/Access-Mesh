package cn.ac.fage.accessmesh.access.permission.cache;

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
     * Value: List&lt;RolePermEntry&gt; 角色的原始权限记录（条件评估前、互斥过滤前）。
     * 空权限角色缓存空列表（List.of()，非 null）防穿透。
     * T-PERM-018：engine forUserView 读路径激活（getBatch 批量查 roleIds，miss 集合 1 SQL，putBatch 回填）。
     * </p>
     */
    public static final CacheCatalogEntry<java.util.List<cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry>> ROLE_PERM_SNAPSHOT =
        CacheCatalogEntry.<java.util.List<cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry>>builder()
            .code("perm:role-perm-snapshot")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(5)
            .l1MaxSize(2000)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<java.util.List<cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry>>() {})
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
     * 操作权限按资源类型缓存（按ID索引）
     * <p>
     * Key: resourceType（缓存 Key 格式："op_perm:" + resourceType）
     * Value: Map<Long, OperationPermission> (id → op)
     * 缓存时间：60分钟（操作定义通常不变）
     * </p>
     */
    public static final CacheCatalogEntry<Map<Long, cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>> OPERATION_PERMISSIONS_BY_TYPE =
        CacheCatalogEntry.<Map<Long, cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>>builder()
            .code("perm:operation-permissions-by-type")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(60)
            .l1MaxSize(100)
            .l2TtlMinutes(120)
            .valueType(new TypeRef<Map<Long, cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>>() {})
            .build();
}