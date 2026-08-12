package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.access.permission.service.domain.sync.SyncMappingsResult;

import java.util.Set;

/**
 * API映射同步处理器接口
 * <p>
 * 提供服务接口同步过程中的API映射同步操作。
 * 根据同步上下文同步资源与API接口的映射关系，
 * 创建新映射、更新已有映射。
 * 同时清理废弃映射（不在传入集合中的映射）。
 * </p>
 */
public interface MappingSyncHandler {

    /**
     * 同步API映射
     * <p>
     * 根据同步上下文同步资源实体与API接口的映射关系。
     * 创建新映射、更新已有映射，返回同步结果统计和传入键集合。
     * </p>
     *
     * @param context 同步上下文，包含租户ID、服务配置、接口列表等
     * @return 同步结果，包含创建/更新的映射数量和传入键集合
     */
    SyncMappingsResult syncMappings(SyncContext context);

    /**
     * 清理废弃映射
     * <p>
     * 删除不在传入集合中的API映射记录。
     * 根据传入键集合（httpMethod|path|resourceCode格式），删除不在集合中的映射。
     * 用于全量同步模式下清理废弃映射。
     * </p>
     *
     * @param tenantId     租户ID
     * @param serviceCode  服务编码
     * @param incomingKeys 应保留的映射键集合（格式：httpMethod|path|resourceCode）
     * @return 删除的映射数量
     */
    int cleanupObsoleteMappings(Long tenantId, String serviceCode, Set<String> incomingKeys);
}