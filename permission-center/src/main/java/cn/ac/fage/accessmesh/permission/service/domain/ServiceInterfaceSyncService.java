package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;

/**
 * 服务接口同步服务接口
 * <p>
 * 提供服务接口与资源API映射的同步功能。
 * 使用策略模式处理不同的同步模式（FULL、INCREMENTAL等）。
 * 同步流程包括：权限校验、服务配置获取、资源同步、映射同步、清理废弃数据。
 * </p>
 */
public interface ServiceInterfaceSyncService {

    /**
     * 同步服务接口
     * <p>
     * 根据服务配置和同步请求同步资源实体和API映射。
     * 支持全量同步（FULL）和增量同步（INCREMENTAL）模式。
     * 全量同步会清理不在同步列表中的资源和映射。
     * 需要SYSTEM_CONFIG_MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        同步请求，包含服务编码、同步模式、接口列表等
     * @param operatorId 操作者ID，可选
     * @return 同步响应，包含创建/更新的资源和映射数量统计
     */
    ServiceConfigSyncResp syncInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId);
}