package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;

/**
 * 同步模式策略接口
 * <p>
 * 定义不同同步模式的执行策略。
 * 支持全量同步（FULL）和增量同步（INCREMENTAL）两种模式。
 * 策略模式允许灵活切换同步算法。
 * </p>
 */
public interface SyncModeStrategy {

    /**
     * 执行同步策略
     *
     * @param context        同步上下文，包含所有必要数据
     * @param resourceHandler 资源同步处理器
     * @param mappingHandler  映射同步处理器
     * @return 同步操作结果
     */
    SyncResult execute(SyncContext context, ResourceSyncHandler resourceHandler, MappingSyncHandler mappingHandler);

    /**
     * 返回策略名称
     *
     * @return 策略名称（如"FULL"、"INCREMENTAL")
     */
    String getName();
}