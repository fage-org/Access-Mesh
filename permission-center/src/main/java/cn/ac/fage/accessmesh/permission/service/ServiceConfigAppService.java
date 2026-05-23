package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigResp;

import java.util.List;

/**
 * 服务配置应用服务接口
 * <p>
 * 提供服务配置的CRUD操作和服务接口同步。
 * </p>
 */
public interface ServiceConfigAppService {

    /**
     * 保存服务配置
     *
     * @param tenantId   租户ID
     * @param req        服务配置请求
     * @param operatorId 操作者ID
     * @return 保存后的服务配置详情
     */
    ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId);

    /**
     * 获取服务配置详情
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 服务配置详情
     */
    ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode);

    /**
     * 查询服务配置列表
     *
     * @param tenantId 租户ID
     * @return 服务配置列表
     */
    List<ServiceConfigResp> listServiceConfigs(Long tenantId);

    /**
     * 批量删除服务配置
     *
     * @param tenantId   租户ID
     * @param ids        服务配置ID列表
     * @param operatorId 操作者ID
     */
    void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId);

    /**
     * 查询服务的API列表
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return API映射列表
     */
    List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode);
}
