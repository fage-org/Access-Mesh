package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限中心缓存目录
 * <p>
 * 定义 permission 域的所有缓存条目。TTL 为 {@link Duration} 秒级精度。
 * </p>
 *
 * <h3>目录清单（T-ACCESS-008）：</h3>
 * <table border="1">
 *   <tr><th>常量名</th><th>code</th><th>模式</th><th>TTL</th><th>说明</th></tr>
 *   <tr><td>EFFECTIVE_ROLES</td><td>perm:effective-roles</td><td>L2_ONLY</td><td>10s</td><td>快照链路</td></tr>
 *   <tr><td>ROLE_PERM_SNAPSHOT</td><td>perm:role-perm-snapshot</td><td>L2_ONLY</td><td>10s</td><td>快照链路</td></tr>
 *   <tr><td>TYPE_VALUE</td><td>perm:type-value</td><td>L2_ONLY</td><td>10s</td><td>快照链路</td></tr>
 *   <tr><td>TYPE_CODE</td><td>perm:type-code</td><td>L2_ONLY</td><td>10s</td><td>快照链路</td></tr>
 *   <tr><td>CONDITION_RULES</td><td>perm:condition-rules</td><td>L2_ONLY</td><td>10s</td><td>快照链路（内联进快照）</td></tr>
 *   <tr><td>ROLE_MUTEX_RULE</td><td>perm:role-mutex-rule</td><td>L2_ONLY</td><td>10s</td><td>快照链路</td></tr>
 *   <tr><td>OPERATION_PERMISSIONS_BY_TYPE</td><td>perm:operation-permissions-by-type</td><td>L1_L2</td><td>60m/120m</td><td>不进快照内容（位掩码计算），普通缓存 + 跨实例 L1 失效广播</td></tr>
 *   <tr><td>ORG_VISIBILITY</td><td>admin:org-visibility</td><td>L2_ONLY</td><td>60s</td><td>不影响接口快照</td></tr>
 * </table>
 *
 * <h3>快照链路安全边界（T-ACCESS-008 用户决策 2026-08-21）：</h3>
 * <p>
 * 可能影响接口权限快照的 6 个目录统一 L2_ONLY、TTL≤10s、不创建授权 L1，
 * 与 Gateway 快照回源截止 5s、Gateway L1 15s 构成「10+5+15≤30s」安全边界；
 * 超限由 {@code PermCacheBoundaryValidator} 启动校验强制。
 * 读路径 miss 回填必须使用 {@code CacheService.beginRead} 令牌写入剩余 TTL。
 * </p>
 */
public final class PermCacheCatalog {

    private PermCacheCatalog() {
    }

    /**
     * 用户有效角色缓存（快照链路）
     * <p>
     * Key: userId
     * Value: Set&lt;Long&gt; 角色ID集合
     * </p>
     */
    public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
        CacheCatalogEntry.<Set<Long>>builder()
            .code("perm:effective-roles")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<Set<Long>>() {})
            .build();

    /**
     * 角色权限快照缓存（快照链路）
     * <p>
     * Key: roleId
     * Value: List&lt;RolePermEntry&gt; 角色的原始权限记录（条件评估前、互斥过滤前）。
     * 空权限角色缓存空列表（List.of()，非 null）防穿透。
     * T-PERM-018：engine forUserView 读路径激活（getBatch 批量查 roleIds，miss 集合 1 SQL，putBatch 回填）。
     * </p>
     */
    public static final CacheCatalogEntry<List<cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry>> ROLE_PERM_SNAPSHOT =
        CacheCatalogEntry.<List<cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry>>builder()
            .code("perm:role-perm-snapshot")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<List<cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry>>() {})
            .build();

    /**
     * 类型值映射缓存（快照链路）
     * <p>
     * Key: typeKey + ":" + typeCode（经 BusinessKeys.typeValueCacheKey 构造，格式 golden 锁定）
     * Value: Map&lt;String, Integer&gt; 类型码 -&gt; 类型值
     * </p>
     */
    public static final CacheCatalogEntry<Map<String, Integer>> TYPE_VALUE =
        CacheCatalogEntry.<Map<String, Integer>>builder()
            .code("perm:type-value")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<Map<String, Integer>>() {})
            .build();

    /**
     * 类型码缓存（快照链路）
     * <p>
     * Key: typeKey + ":" + typeValue（经 BusinessKeys.typeCodeCacheKey 构造，格式 golden 锁定）
     * Value: String 类型码
     * </p>
     */
    public static final CacheCatalogEntry<String> TYPE_CODE =
        CacheCatalogEntry.<String>builder()
            .code("perm:type-code")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<String>() {})
            .build();

    /**
     * 条件规则缓存（快照链路——可下发条件内联进接口快照）
     * <p>
     * Key: conditionId
     * Value: JsonNode 规则 JSON
     * </p>
     */
    public static final CacheCatalogEntry<JsonNode> CONDITION_RULES =
        CacheCatalogEntry.<JsonNode>builder()
            .code("perm:condition-rules")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<JsonNode>() {})
            .build();

    /**
     * 角色互斥规则缓存（快照链路——快照构建前 filterRoleMutex 使用）
     * <p>
     * Key: "all"（单一键）
     * Value: String JSON数组格式的互斥规则
     * </p>
     */
    public static final CacheCatalogEntry<String> ROLE_MUTEX_RULE =
        CacheCatalogEntry.<String>builder()
            .code("perm:role-mutex-rule")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<String>() {})
            .build();

    /**
     * 操作权限按资源类型缓存（按ID索引）
     * <p>
     * Key: resourceType（缓存 Key 格式："op_perm:" + resourceType）
     * Value: Map&lt;Long, OperationPermission&gt; (id → op)
     * </p>
     * <p>
     * 不进接口快照内容（validate/hasPermission 位掩码计算），T-ACCESS-008 用户决策：
     * 保持 L1_L2 普通缓存（60m/120m），跨实例一致性由普通 L1 失效广播保证
     * （广播丢失时最长陈旧 = L1 TTL）。
     * </p>
     */
    public static final CacheCatalogEntry<Map<Long, cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>> OPERATION_PERMISSIONS_BY_TYPE =
        CacheCatalogEntry.<Map<Long, cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>>builder()
            .code("perm:operation-permissions-by-type")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMinutes(60))
            .l1MaxSize(100)
            .l2Ttl(Duration.ofMinutes(120))
            .valueType(new TypeRef<Map<Long, cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>>() {})
            .build();

    /**
     * OPERATION_PERMISSIONS_BY_TYPE 的缓存键构造（T-PERM-047）。
     * 读路径（PermQueryEngine.resolveBitMasks）与写路径失效（create/update/deleteOperation、
     * resource_type 预置）共用同一键格式，禁止散落手拼 "op_perm:" 前缀。
     *
     * @param resourceType 资源类型内部值（type_definition.type_value）
     * @return 缓存 identifier
     */
    public static String operationPermissionsByTypeKey(Integer resourceType) {
        return "op_perm:" + resourceType;
    }

    /**
     * 操作者可见组织范围缓存
     * <p>
     * Key: operatorId（操作者用户 ID）
     * Value: Set&lt;Long&gt; 操作者通过 ORG:VIEW 可见的默认树组织 ID 集合
     * <p>
     * L2_ONLY（用户决策）：安全敏感目录，L1 本地缓存会造成跨实例旧可见范围；
     * 纯 Redis 共享存储 + 权限变更后租户级 evictAll 保证全实例一致。
     * 不影响接口权限快照——TTL 不受 10 秒边界约束，60 秒吸收高频查询。
     */
    public static final CacheCatalogEntry<Set<Long>> ORG_VISIBILITY =
        CacheCatalogEntry.<Set<Long>>builder()
            .code("admin:org-visibility")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(60))
            .valueType(new TypeRef<Set<Long>>() {})
            .build();
}
