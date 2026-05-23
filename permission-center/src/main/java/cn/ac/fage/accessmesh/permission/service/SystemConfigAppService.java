package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.SystemConfigResp;

import java.util.List;

/**
 * 系统配置应用服务接口
 * <p>
 * 提供系统配置的CRUD操作。
 * </p>
 */
public interface SystemConfigAppService {

    /**
     * 保存或更新系统配置
     *
     * @param tenantId 租户ID
     * @param req      系统配置请求
     * @return 保存后的系统配置详情
     */
    SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req);

    /**
     * 获取系统配置详情
     *
     * @param tenantId  租户ID
     * @param configKey 配置键
     * @return 系统配置详情
     */
    SystemConfigResp getSystemConfig(Long tenantId, String configKey);

    /**
     * 查询系统配置列表
     *
     * @param tenantId 租户ID
     * @return 系统配置列表
     */
    List<SystemConfigResp> listSystemConfigs(Long tenantId);
}
