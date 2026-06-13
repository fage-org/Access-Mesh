package cn.ac.fage.accessmesh.permission.service.sync;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;

import java.util.List;

/**
 * SyncResultResp 构造工具
 * <p>
 * 统一同步操作响应壳的构造，避免散落 boolean 组合错误。
 * 字段语义详见 api-contract §6.2.2.2 / §6.2.2.6。
 * </p>
 */
public final class SyncResultBuilder {

    /**
     * STALE 错误分类常量。
     */
    public static final String RETRY_STALE_VERSION = "STALE_VERSION";
    public static final String RETRY_RETRYABLE = "RETRYABLE";
    public static final String RETRY_DEPENDENCY_MISSING = "DEPENDENCY_MISSING";
    public static final String RETRY_NON_RETRYABLE = "NON_RETRYABLE";
    public static final String RETRY_SECURITY_DENIED = "SECURITY_DENIED";

    public static final String REASON_STALE = "SYNC_VERSION_STALE";
    public static final String REASON_FULL_SYNC_PARTIAL_FAILURE = "FULL_SYNC_PARTIAL_FAILURE";

    private SyncResultBuilder() {
    }

    /**
     * APPLIED 成功。
     */
    public static SyncResultResp applied() {
        return new SyncResultResp(true, true, false, null, null);
    }

    /**
     * STALE：accepted=true，applied=false，retryClass=STALE_VERSION。
     */
    public static SyncResultResp stale() {
        return new SyncResultResp(true, false, true, RETRY_STALE_VERSION, REASON_STALE);
    }

    /**
     * 通用失败。
     */
    public static SyncResultResp failed(String retryClass, String reason) {
        return new SyncResultResp(false, false, false, retryClass, reason);
    }

    public static SyncResultResp dependencyMissing(String reason) {
        return failed(RETRY_DEPENDENCY_MISSING, reason);
    }

    public static SyncResultResp nonRetryable(String reason) {
        return failed(RETRY_NON_RETRYABLE, reason);
    }

    public static SyncResultResp securityDenied(String reason) {
        return failed(RETRY_SECURITY_DENIED, reason);
    }

    public static SyncResultResp retryable(String reason) {
        return failed(RETRY_RETRYABLE, reason);
    }

    /**
     * full-sync 响应封装：将批量统计与 itemResults 包装到 detail。
     * <ul>
     *   <li>accepted = true</li>
     *   <li>applied = (failedCount == 0)</li>
     *   <li>stale = false</li>
     *   <li>retryClass = (failedCount &gt; 0) ? "RETRYABLE" : null</li>
     *   <li>reason = (failedCount &gt; 0) ? "FULL_SYNC_PARTIAL_FAILURE" : null</li>
     * </ul>
     */
    public static SyncResultResp fullSync(int appliedCount, int staleCount, int failedCount,
                                          int deactivatedCount,
                                          List<SyncResultResp.ItemResult> items) {
        boolean allOk = failedCount == 0;
        return new SyncResultResp(
                true,
                allOk,
                false,
                allOk ? null : RETRY_RETRYABLE,
                allOk ? null : REASON_FULL_SYNC_PARTIAL_FAILURE,
                new SyncResultResp.FullSyncDetail(
                        appliedCount, staleCount, failedCount, deactivatedCount,
                        items == null ? List.of() : items
                )
        );
    }

    /**
     * full-sync 全失败响应（如身份/scope 校验未通过）。
     */
    public static SyncResultResp fullSyncRejected(String retryClass, String reason,
                                                  int rejectedCount,
                                                  List<SyncResultResp.ItemResult> items) {
        return new SyncResultResp(
                false,
                false,
                false,
                retryClass,
                reason,
                new SyncResultResp.FullSyncDetail(
                        0, 0, rejectedCount, 0,
                        items == null ? List.of() : items
                )
        );
    }
}
