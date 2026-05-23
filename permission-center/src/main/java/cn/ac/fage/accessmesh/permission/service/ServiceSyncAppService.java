package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;

/**
 * 服务同步应用服务接口
 * <p>
 * 提供服务接口与资源API映射的同步功能。
 * </p>
 */
public interface ServiceSyncAppService {

    /**
     * 同步服务接口
     *
     * @param tenantId 租户ID
     * @param req      同步请求
     * @return 同步响应
     */
    ServiceConfigSyncResp syncInterfaces(Long tenantId, ServiceConfigSyncReq req);
}
