package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

/**
 * 同步重试服务接口
 * <p>
 * 提供跨服务数据同步失败后的重试机制。
 * 当数据同步失败时，记录失败信息并支持后续重试。
 * 用于保证分布式系统中数据的一致性。
 * </p>
 */
public interface SyncRetryService {

    /**
     * 记录同步失败事件
     * <p>
     * 当跨服务数据同步失败时，记录失败详情以便后续重试。
     * </p>
     *
     * @param messageKey    消息键，用于标识同步消息类型
     * @param targetService 目标服务名称
     * @param entityType    实体类型（如User、Org、Menu）
     * @param externalId    外部实体ID
     * @param operationType 操作类型（如CREATE、UPDATE、DELETE）
     * @param payload       同步数据内容（JSON格式）
     * @param error         错误信息
     */
    void recordSyncFailure(String messageKey, String targetService, String entityType,
                           String externalId, String operationType, String payload, String error);

    /**
     * 标记重试成功
     * <p>
     * 当重试操作成功后，标记记录为已处理。
     * </p>
     *
     * @param id 重试记录ID
     */
    void markSuccess(Long id);

    /**
     * 标记重试失败
     * <p>
     * 当重试操作再次失败后，更新错误信息并增加重试次数。
     * </p>
     *
     * @param id    重试记录ID
     * @param error 错误信息
     */
    void markFailed(Long id, String error);

    /**
     * 获取待重试记录列表
     * <p>
     * 查询所有未处理且需要重试的同步记录。
     * </p>
     *
     * @return 待重试记录列表
     */
    List<SysSyncRetry> getPendingRetries();

    /**
     * 删除已处理的记录
     * <p>
     * 删除已成功处理或已放弃的重试记录。
     * </p>
     *
     * @param id 重试记录ID
     */
    void deleteProcessed(Long id);

    /**
     * 分页查询同步重试记录
     * <p>
     * 查询系统中的同步重试记录，支持分页。
     * </p>
     *
     * @param pageReq 分页请求参数
     * @return 分页同步重试记录结果
     */
    PaginatedResult<SysSyncRetry> page(PageReq pageReq);
}
