package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DomainConfigResp;

import java.util.List;

/**
 * 域配置应用服务接口
 * <p>
 * 提供域配置的CRUD操作。
 * </p>
 */
public interface DomainConfigAppService {

    /**
     * 保存或更新域配置
     *
     * @param tenantId 租户ID
     * @param req      域配置请求
     * @return 保存后的域配置详情
     */
    DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req);

    /**
     * 获取域配置详情
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @param configType 配置类型编码
     * @return 域配置详情
     */
    DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType);

    /**
     * 查询域配置列表
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @return 域配置列表
     */
    List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode);

    /**
     * 批量删除域配置
     *
     * @param tenantId   租户ID
     * @param ids        配置ID列表
     * @param operatorId 操作者ID
     */
    void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId);
}
