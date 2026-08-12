package cn.ac.fage.accessmesh.access.admin.cache;

import cn.ac.fage.accessmesh.access.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin 服务缓存目录
 * <p>
 * 定义 admin-service 模块的所有缓存条目。
 * </p>
 */
public final class AdminCacheCatalog {

    private AdminCacheCatalog() {
    }

    /**
     * 字典类型缓存
     * <p>
     * Key: "all"（全局键）
     * Value: List<DictTypeResp> 字典数据列表
     * </p>
     */
    public static final CacheCatalogEntry<List<DictTypeResp>> DICT_TYPES =
        CacheCatalogEntry.<List<DictTypeResp>>builder()
            .code("admin:dict-types")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(10)
            .l1MaxSize(500)
            .l2TtlMinutes(60)
            .valueType(new TypeRef<List<DictTypeResp>>() {})
            .build();

    /**
     * 操作码缓存
     * <p>
     * Key: tenantId
     * Value: Map&lt;String, Long&gt; 操作码 → 操作权限ID映射
     * </p>
     */
    public static final CacheCatalogEntry<Map<String, Long>> OPERATION_CODE =
        CacheCatalogEntry.<Map<String, Long>>builder()
            .code("admin:operation-code")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(30)
            .l1MaxSize(100)
            .l2TtlMinutes(120)
            .valueType(new TypeRef<Map<String, Long>>() {})
            .build();

    /**
     * 操作者可见组织范围缓存
     * <p>
     * Key: operatorId（操作者用户 ID）
     * Value: Set<Long> 操作者通过 ADMIN_ORG:VIEW 可见的默认树组织 ID 集合
     * <p>
     * 60 秒 TTL，吸收高频查询（memberCandidates / pageUsers / validateUsersInDefaultTreeScope）
     */
    public static final CacheCatalogEntry<Set<Long>> ORG_VISIBILITY =
        CacheCatalogEntry.<Set<Long>>builder()
            .code("admin:org-visibility")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(1)
            .l1MaxSize(500)
            .l2TtlMinutes(5)
            .valueType(new TypeRef<Set<Long>>() {})
            .build();
}