package cn.ac.fage.accessmesh.perm.common.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.util.List;

/**
 * 资源查询响应体（T-API-002 自 access-service 迁入 SDK 公共包）
 * <p>
 * 返回用户可访问的资源列表，包括缓存信息。
 * 用于资源查询接口的响应。
 * </p>
 * <p>
 * T-PERM-013：scopeAll(boolean) → scopeMode(ScopeMode)，对外协议统一使用枚举。
 * 资源条目仅使用 INSTANCE / ALL 两态。
 * </p>
 * <p>
 * T-API-002（2026-09-06 定案）：内部数据库 id 字段族（matchedRoleIds /
 * matchedPermissionIds，role / role_resource_permission 内部行 id）全数裁剪，
 * 与 core-flows §15「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。
 * </p>
 *
 * @param items           资源条目列表
 * @param cacheTtlSeconds 缓存有效时间（秒）
 */
public record QueryResourcesResp(
    List<ResourceEntry> items,
    int cacheTtlSeconds
) {
    /**
     * 资源条目
     * <p>
     * 表示用户可访问的单个资源信息，包括操作权限和来源信息。
     * </p>
     *
     * @param resourceTypeCode   资源类型编码
     * @param resourceCode       资源编码，scopeMode=ALL 时为 null
     * @param codeType           编码类型，scopeMode=ALL 时为 null
     * @param resourceName       资源名称，scopeMode=ALL 时为 null
     * @param canGrant           是否可授予他人
     * @param scopeMode          范围模式（T-PERM-013）：INSTANCE=具体实例，ALL=全量范围
     * @param operations         操作权限列表
     * @param grantSources       授权来源列表
     */
    public record ResourceEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        boolean canGrant,
        ScopeMode scopeMode,
        List<String> operations,
        List<String> grantSources
    ) {}
}
