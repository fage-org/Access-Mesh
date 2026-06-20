package cn.ac.fage.accessmesh.perm.common.enums;

import lombok.Getter;

/**
 * 数据范围模式（L2 数据权限契约）
 * <p>
 * 合并原 {@code allowed}（操作层放行）与数据范围层语义为单一四态枚举。
 * 业务方（Gateway / 前端 / 管理端）按 {@link #scopeMode} 决定是否发 SQL 及如何加范围过滤。
 * </p>
 * <p>
 * T-PERM-009（2026-06-20）定义；放 perm-common 供 admin / gateway / 前端共用。
 * </p>
 */
@Getter
public enum ScopeMode {
    /**
     * 无操作权限（原 allowed=false）。
     * <p>业务方应拒绝/返回 403，不发任何 SQL。</p>
     */
    DENIED("无操作权限"),

    /**
     * 有操作权限 + 具体实例授权。
     * <p>items[] 非空，业务方按 items[] 加 IN 范围过滤。</p>
     */
    INSTANCE("实例授权"),

    /**
     * 有操作权限 + 全量授权（scopeAll）。
     * <p>items[] 为空，业务方不加范围过滤。</p>
     */
    ALL("全量授权"),

    /**
     * 有操作权限但无数据范围（条件/互斥过滤后实例为空）。
     * <p>业务方应返回空结果，不发 SQL。</p>
     */
    EMPTY("有权限无数据");

    private final String description;

    ScopeMode(String description) {
        this.description = description;
    }
}
