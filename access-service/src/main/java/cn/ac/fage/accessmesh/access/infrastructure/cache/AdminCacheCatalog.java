package cn.ac.fage.accessmesh.access.infrastructure.cache;

import cn.ac.fage.accessmesh.access.platform.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;

import java.time.Duration;
import java.util.List;

/**
 * Admin 服务缓存目录
 * <p>
 * 定义 admin 域（access-service）的所有缓存条目。
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
            .l1Ttl(Duration.ofMinutes(10))
            .l1MaxSize(500)
            .l2Ttl(Duration.ofMinutes(60))
            .valueType(new TypeRef<List<DictTypeResp>>() {})
            .build();

}