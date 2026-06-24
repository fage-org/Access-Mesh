package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 接口权限快照响应体（T-PERM-001 迁入 perm-common 供 Gateway 共享）
 * <p>
 * 供Gateway消费的接口权限快照响应。
 * 包含租户的服务接口权限配置，Gateway 本地内存匹配鉴权。
 * </p>
 * <p>
 * T-PERM-018：移除 permissionVersion/notModified（缓存下沉）。permission-center 每次实时构建全量快照，
 * Gateway 本地 Caffeine 缓存 + Redis 广播（perm:invalidate）+ TTL 兜底保证一致性。
 * </p>
 *
 * @param allowedApis 允许访问的API权限条目列表
 */
public record InterfaceSnapshotResp(
    List<ApiPermissionEntry> allowedApis
) {
    /**
     * API权限条目
     * <p>
     * 表示单个API接口的权限配置信息。
     * </p>
     *
     * @param serviceCode     服务编码
     * @param httpMethod      HTTP方法，scopeAll为true时为null
     * @param pathPattern     路径模式，scopeAll为true时为null
     * @param hasCondition    是否有条件权限
     * @param conditionId     条件ID，无条件时为null
     * @param conditionRules  T-PERM-017 内联的条件规则 JSON 原文。仅当条件 {@code gateway_evaluable=true}
     *                        且规则类型全在白名单内时由 SnapshotAssembler 内联，Gateway 本地重评；其他情况为 null
     *                        （Gateway 命中该条目时回退 check-interface）
     * @param scopeAll        是否为全量范围权限（不限定具体接口），为true时httpMethod和pathPattern为null
     */
    public record ApiPermissionEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId,
        String conditionRules,
        boolean scopeAll
    ) {}
}
