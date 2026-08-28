package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SystemConfigResp;

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
     * 按条件统计有效系统配置数量
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（configKey/description LIKE，大小写敏感）
     * @return 有效行数
     */
    long countSystemConfigs(Long tenantId, String keyword);

    /**
     * 按条件分页查询系统配置
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（configKey/description LIKE，大小写敏感）
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 系统配置列表（ORDER BY config_key, id）
     */
    List<SystemConfigResp> listSystemConfigs(Long tenantId, String keyword, int offset, int limit);
}
