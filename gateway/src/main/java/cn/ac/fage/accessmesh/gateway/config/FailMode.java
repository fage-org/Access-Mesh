package cn.ac.fage.accessmesh.gateway.config;

/**
 * Gateway 权限校验失联兜底模式（T-GW-001 / T-GW-002）。
 * <p>
 * 当 permission-center 不可达（网络错误、超时、5xx）时的兜底策略：
 * <ul>
 *   <li>{@link #CLOSED} — fail-closed：拒绝请求（默认，生产安全）</li>
 *   <li>{@link #OPEN} — fail-open：放行请求（仅限演示环境）</li>
 *   <li>{@link #STALE_ALLOW} — stale-allow：使用陈旧快照续命（T-GW-003 实现）</li>
 * </ul>
 * <p>
 * 核心原则：权限主动撤销 > 服务不可达兜底。
 * {@code StaleLoadDiscardedException}（回源并发失效）代表显式撤销，不受 fail-mode 影响，始终 503。
 */
public enum FailMode {

    /** fail-closed：permission-center 不可达时拒绝请求（默认，生产安全） */
    CLOSED,

    /** fail-open：permission-center 不可达时放行请求（仅限演示环境） */
    OPEN,

    /** stale-allow：permission-center 不可达时使用陈旧快照续命（T-GW-003 实现） */
    STALE_ALLOW
}
