package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncResourcesResult;

import java.util.Set;

/**
 * 资源同步处理器接口
 * <p>
 * 提供服务接口同步过程中的资源同步操作。
 * 根据同步上下文同步API资源实体，创建新资源、更新已有资源。
 * 同时清理孤立资源（无关联映射的资源）。
 * </p>
 */
public interface ResourceSyncHandler {

    /**
     * 同步资源
     * <p>
     * 根据同步上下文同步API资源实体。
     * 创建新资源、更新已有资源，返回同步结果统计和活跃资源ID集合。
     * </p>
     *
     * @param context 同步上下文，包含租户ID、服务配置、接口列表等
     * @return 同步结果，包含创建/更新的资源数量和活跃资源ID集合
     */
    SyncResourcesResult syncResources(SyncContext context);

    /**
     * 清理孤立资源
     * <p>
     * 删除无关联映射的资源实体。
     * 根据活跃资源ID集合，删除不在集合中的资源（孤立资源）。
     * 用于全量同步模式下清理废弃资源。
     * </p>
     *
     * @param tenantId        租户ID
     * @param serviceCode     服务编码
     * @param apiType         API资源类型值
     * @param activeResourceIds 应保留活跃的资源ID集合
     * @return 删除的资源数量
     */
    int cleanupOrphanedResources(Long tenantId, String serviceCode, Integer apiType, Set<Long> activeResourceIds);
}