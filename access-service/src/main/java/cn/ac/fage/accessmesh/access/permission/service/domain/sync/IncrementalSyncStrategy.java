package cn.ac.fage.accessmesh.access.permission.service.domain.sync;

import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.MappingSyncHandler;
import org.springframework.stereotype.Component;

/**
 * 增量同步策略
 * <p>
 * 仅添加/更新新条目，不删除已有条目。
 * 适用于：
 * - 多服务并发同步场景（避免误删其他服务的资源）
 * - 临时性的部分同步需求
 * 注意：增量模式不会清理过期映射和孤立资源。
 * </p>
 */
@Component
public class IncrementalSyncStrategy implements SyncModeStrategy {

    /**
     * 策略名称常量
     */
    public static final String NAME = "INCREMENTAL";

    /**
     * 返回策略名称
     *
     * @return 策略名称"INCREMENTAL"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 执行增量同步策略
     * <p>
     * 执行步骤：
     * 1. 同步资源（创建/更新，不清理）
     * 2. 同步映射（创建/更新，不清理）
     * 增量模式下删除计数为0。
     * </p>
     *
     * @param context        同步上下文
     * @param resourceHandler 资源同步处理器
     * @param mappingHandler  映射同步处理器
     * @return 同步操作结果
     */
    @Override
    public SyncResult execute(SyncContext context, ResourceSyncHandler resourceHandler,
                               MappingSyncHandler mappingHandler) {
        SyncResult result = new SyncResult();

        // 同步资源（增量模式，不清理）
        SyncResourcesResult resourcesResult = resourceHandler.syncResources(context);
        result.setCreatedResources(resourcesResult.getCreatedCount());
        result.setUpdatedResources(resourcesResult.getUpdatedCount());

        // 同步映射（增量模式，不清理）
        SyncMappingsResult mappingsResult = mappingHandler.syncMappings(context);
        result.setCreatedMappings(mappingsResult.getCreatedCount());
        result.setUpdatedMappings(mappingsResult.getUpdatedCount());

        // 增量模式下不清理
        result.setDeletedMappings(0);
        result.setDeletedResources(0);

        return result;
    }
}