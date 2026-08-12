package cn.ac.fage.accessmesh.access.permission.service.domain.sync;

import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.MappingSyncHandler;
import org.springframework.stereotype.Component;

/**
 * 全量同步策略
 * <p>
 * 执行完整的资源与映射同步操作，包括清理过期条目。
 * 全量同步会：
 * 1. 同步所有资源（创建/更新）
 * 2. 同步所有映射（创建/更新）
 * 3. 清理不在传入列表中的过期映射
 * 4. 清理无关联的孤立资源
 * </p>
 */
@Component
public class FullSyncStrategy implements SyncModeStrategy {

    /**
     * 策略名称常量
     */
    public static final String NAME = "FULL";

    /**
     * 返回策略名称
     *
     * @return 策略名称"FULL"
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 执行全量同步策略
     * <p>
     * 执行步骤：
     * 1. 同步资源（创建/更新）
     * 2. 同步映射（创建/更新）
     * 3. 清理过期映射
     * 4. 清理孤立资源
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

        // 同步资源
        SyncResourcesResult resourcesResult = resourceHandler.syncResources(context);
        result.setCreatedResources(resourcesResult.getCreatedCount());
        result.setUpdatedResources(resourcesResult.getUpdatedCount());

        // 同步映射
        SyncMappingsResult mappingsResult = mappingHandler.syncMappings(context);
        result.setCreatedMappings(mappingsResult.getCreatedCount());
        result.setUpdatedMappings(mappingsResult.getUpdatedCount());

        // 清理过期映射
        int deletedMappings = mappingHandler.cleanupObsoleteMappings(
            context.tenantId(),
            context.req().serviceCode(),
            mappingsResult.getIncomingKeys()
        );
        result.setDeletedMappings(deletedMappings);

        // 清理孤立资源
        int deletedResources = resourceHandler.cleanupOrphanedResources(
            context.tenantId(),
            context.req().serviceCode(),
            context.apiType(),
            resourcesResult.getActiveResourceIds()
        );
        result.setDeletedResources(deletedResources);

        return result;
    }
}