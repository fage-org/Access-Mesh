package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * 系统配置服务接口
 * <p>
 * 提供系统配置管理相关的服务方法，包括配置的查询、更新、删除等。
 * 系统配置用于存储全局性的配置项，如系统参数、功能开关等。
 * </p>
 */
public interface ConfigService {

    /**
     * 分页查询配置列表
     * <p>
     * 根据条件分页查询系统配置列表。
     * 支持按配置名称、键名等条件筛选。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页配置列表结果
     */
    PaginatedResult<ConfigResp> pageConfigs(PageReq pageReq);

    /**
     * 获取配置详情
     * <p>
     * 根据ID查询配置详细信息。
     * </p>
     *
     * @param id 配置ID
     * @return 配置详情响应
     */
    ConfigResp getConfig(Long id);

    /**
     * 更新配置
     * <p>
     * 更新指定配置的基本信息。
     * 包括配置名称、键名、配置值等属性。
     * </p>
     *
     * @param req 配置更新请求
     */
    void updateConfig(ConfigUpdateReq req);

    /**
     * 删除配置
     * <p>
     * 批量删除系统配置。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的配置ID列表请求
     */
    void deleteConfig(IdsReq req);
}