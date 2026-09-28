package cn.ac.fage.accessmesh.perm.common.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.util.List;

/**
 * 接口权限快照响应体（T-PERM-001 迁入 perm-common 供 Gateway 共享）
 * <p>
 * 供Gateway消费的接口权限快照响应。
 * 包含租户的服务接口权限配置，Gateway 本地内存匹配鉴权。
 * </p>
 * <p>
 * T-PERM-018：移除 permissionVersion/notModified（缓存下沉）。access-service 每次实时构建全量快照，
 * Gateway 本地 Caffeine 缓存 + Redis 广播（perm:invalidate）+ TTL 兜底保证一致性。
 * </p>
 * <p>
 * T-PERM-013：scopeAll(boolean) → scopeMode(ScopeMode)，对外协议统一使用枚举。
 * 快照条目恒为 INSTANCE：API 类型级 scopeAll 授权由服务端展开为该 serviceCode 全部
 * enabled 注册映射的逐条 INSTANCE 条目（T-PERM-017 起，不输出 ALL 通配——类型级 API
 * 授权语义=「全部已注册 API」，未注册接口维持默认拒绝）。
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
     * @param httpMethod      HTTP方法
     * @param pathPattern     路径模式
     * @param hasCondition    是否有条件权限
     * @param conditionId     条件ID，无条件时为null
     * @param conditionRules  T-PERM-017 内联的条件规则 JSON 原文。仅当条件 {@code gateway_evaluable=true}
     *                        且规则类型全在白名单内时由 SnapshotAssembler 内联，Gateway 本地重评；其他情况为 null
     *                        （Gateway 命中该条目时回退 check-interface）
     * @param scopeMode       范围模式（T-PERM-013）：本响应恒 INSTANCE（类型级授权已展开为逐注册路由条目，
     *                        ALL 形态不产出；枚举 ALL 态由 Query* 响应族使用）
     */
    public record ApiPermissionEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId,
        String conditionRules,
        ScopeMode scopeMode
    ) {}
}
