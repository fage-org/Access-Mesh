package cn.ac.fage.accessmesh.admin.dto.resp;

import java.util.List;

/**
 * 批量操作结果响应记录类
 * <p>
 * 用于批量操作的统一响应结果。
 * 包含总数、成功数、失败数、成功ID列表、失败消息列表。
 * 提供静态工厂方法快速构建结果。
 * </p>
 *
 * @param total          操作总数
 * @param success        成功数量
 * @param failed         失败数量
 * @param successIds     成功的ID列表
 * @param failedMessages 失败消息列表
 */
public record BatchResultResp(
    /**
     * 操作总数
     */
    int total,

    /**
     * 成功数量
     */
    int success,

    /**
     * 失败数量
     */
    int failed,

    /**
     * 成功的ID列表
     */
    List<Long> successIds,

    /**
     * 失败消息列表
     */
    List<String> failedMessages
) {
    /**
     * 构建完全成功的响应
     *
     * @param ids 成功的ID列表
     * @return 批量操作结果响应
     */
    public static BatchResultResp success(List<Long> ids) {
        return new BatchResultResp(ids.size(), ids.size(), 0, ids, List.of());
    }

    /**
     * 构建部分成功的响应
     *
     * @param total          操作总数
     * @param success        成功数量
     * @param successIds     成功的ID列表
     * @param failedMessages 失败消息列表
     * @return 批量操作结果响应
     */
    public static BatchResultResp partial(int total, int success, List<Long> successIds, List<String> failedMessages) {
        return new BatchResultResp(total, success, total - success, successIds, failedMessages);
    }
}