package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import java.util.List;

/** 资源前置未成功时 manifestResult 为 null，保留逐步诊断；两个请求不构成原子事务。 */
public record RegistrationResult(List<SyncResultResp> preparationResults, SyncResultResp manifestResult) {
    public RegistrationResult { preparationResults = List.copyOf(preparationResults); }
    public boolean successful() { return manifestResult != null && complete(manifestResult); }
    static boolean complete(SyncResultResp response) {
        if (response == null || !response.accepted() || "PUBLICATION_GENERATION_STALE".equals(response.reason())) return false;
        if (response.detail() == null) return response.applied() || response.stale();
        var detail = response.detail();
        return detail.failedCount() == 0 && detail.itemResults() != null
                && detail.itemResults().stream().allMatch(item -> item != null && (item.applied() || item.stale()));
    }
}
