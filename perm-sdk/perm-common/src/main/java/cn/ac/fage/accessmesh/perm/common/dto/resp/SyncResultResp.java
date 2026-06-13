package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 同步操作响应壳
 * <p>
 * 详见 {@code docs/design/permission-center/api-contract.md §6.2.2.2}：
 * <ul>
 *   <li>成功：accepted=true, applied=true, stale=false, retryClass=null, reason=null</li>
 *   <li>STALE：accepted=true, applied=false, stale=true, retryClass=STALE_VERSION, reason=SYNC_VERSION_STALE</li>
 *   <li>失败：accepted=false, applied=false, stale=false,
 *       retryClass∈{RETRYABLE,DEPENDENCY_MISSING,NON_RETRYABLE,SECURITY_DENIED}</li>
 * </ul>
 * </p>
 * <p>
 * sync 接口 detail 始终为 {@code null}；full-sync 接口 detail 非 null，包含批量明细。
 * 详见 §6.2.2.6 FullSyncDetail 结构。
 * </p>
 *
 * @param accepted   服务端是否完成业务幂等校验
 * @param applied    是否真正落库
 * @param stale      是否因 syncVersion 较旧而被钝化
 * @param retryClass 失败/钝化分类（RETRYABLE/DEPENDENCY_MISSING/NON_RETRYABLE/SECURITY_DENIED/STALE_VERSION）
 * @param reason     可读的拒绝/钝化原因
 * @param detail     全量同步明细（sync 接口为 null；full-sync 接口非 null）
 */
public record SyncResultResp(
        boolean accepted,
        boolean applied,
        boolean stale,
        String retryClass,
        String reason,
        FullSyncDetail detail
) {
    /**
     * 兼容现有 sync 路径的 5 字段构造（detail 默认 null）。
     */
    public SyncResultResp(boolean accepted, boolean applied, boolean stale,
                          String retryClass, String reason) {
        this(accepted, applied, stale, retryClass, reason, null);
    }

    /**
     * 全量同步明细，仅 full-sync 接口返回。
     *
     * @param appliedCount     成功 apply 的 item 数量
     * @param staleCount       因 syncVersion 较旧而被钝化的 item 数量
     * @param failedCount      因依赖/参数/权限失败的 item 数量
     * @param deactivatedCount scope 内未出现而被自动 DELETE/UNBIND 的业务键数量
     * @param itemResults      每个 item 的明细结果
     */
    public record FullSyncDetail(
            int appliedCount,
            int staleCount,
            int failedCount,
            int deactivatedCount,
            List<ItemResult> itemResults
    ) {}

    /**
     * 单个 item 的处理结果。
     *
     * @param businessKey 业务键
     * @param applied     是否成功落库
     * @param stale       是否因版本较旧被钝化
     * @param retryClass  失败/钝化分类
     * @param reason      可读原因
     */
    public record ItemResult(
            String businessKey,
            boolean applied,
            boolean stale,
            String retryClass,
            String reason
    ) {}
}
