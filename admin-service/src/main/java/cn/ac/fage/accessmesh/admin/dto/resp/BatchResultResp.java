package cn.ac.fage.accessmesh.admin.dto.resp;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量操作结果响应
 */
public record BatchResultResp(
    int total,
    int success,
    int failed,
    List<Long> successIds,
    List<String> failedMessages
) {
    public static BatchResultResp success(List<Long> ids) {
        return new BatchResultResp(ids.size(), ids.size(), 0, ids, List.of());
    }

    public static BatchResultResp partial(int total, int success, List<Long> successIds, List<String> failedMessages) {
        return new BatchResultResp(total, success, total - success, successIds, failedMessages);
    }
}