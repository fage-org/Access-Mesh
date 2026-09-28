package cn.ac.fage.accessmesh.access.resource.service;

import cn.ac.fage.accessmesh.access.resource.dto.resp.ServiceConfigSyncResp;

/**
 * 服务同步应用服务接口
 * <p>
 * 提供服务接口与资源API映射的同步功能。
 * </p>
 */
public interface ServiceSyncAppService {

    cn.ac.fage.accessmesh.access.resource.dto.resp.ServiceConfigSyncResp syncInterfacesV2(
        Long tenantId, cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigSyncV2Req req);

}
