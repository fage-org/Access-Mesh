package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;

/**
 * 服务接口同步上下文记录类
 * <p>
 * 在服务接口同步过程中携带所有必要数据的上下文对象。
 * 包含租户ID、服务配置、同步请求、操作者ID、基础路径、API类型等。
 * </p>
 *
 * @param tenantId     租户ID
 * @param serviceConfig 服务配置实体
 * @param req          同步请求参数
 * @param operatorId   操作者ID
 * @param basePath     基础路径
 * @param apiType      API类型（REST、RPC等）
 */
public record SyncContext(
    /**
     * 租户ID
     */
    Long tenantId,

    /**
     * 服务配置实体
     */
    ServiceConfig serviceConfig,

    /**
     * 同步请求参数
     */
    ServiceConfigSyncReq req,

    /**
     * 操作者ID
     */
    Long operatorId,

    /**
     * 基础路径（用于API路径拼接）
     */
    String basePath,

    /**
     * API类型（REST、RPC等）
     */
    Integer apiType
) {
    /**
     * 创建同步上下文
     *
     * @param tenantId     租户ID
     * @param config       服务配置实体
     * @param req          同步请求参数
     * @param operatorId   操作者ID
     * @param basePath     基础路径
     * @param apiType      API类型
     * @return 同步上下文对象
     */
    public static SyncContext of(Long tenantId, ServiceConfig config, ServiceConfigSyncReq req,
                                  Long operatorId, String basePath, Integer apiType) {
        return new SyncContext(tenantId, config, req, operatorId, basePath, apiType);
    }
}