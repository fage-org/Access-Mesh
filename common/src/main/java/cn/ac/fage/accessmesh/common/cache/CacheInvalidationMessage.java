package cn.ac.fage.accessmesh.common.cache;

import java.util.Set;

/**
 * 普通 L1 失效广播消息载荷（T-ACCESS-008）
 * <p>
 * 经 Redis RTopic 序列化为 JSON 传输；{@code all=true} 表示目录租户级全量失效
 * （{@code keys} 为空）。
 * </p>
 *
 * @param catalogCode 缓存目录编码
 * @param tenantId 租户ID
 * @param keys 完整缓存键集合（all=false 时非空）
 * @param all 是否目录租户级全量失效
 */
public record CacheInvalidationMessage(String catalogCode, Long tenantId, Set<String> keys, boolean all) {
}
