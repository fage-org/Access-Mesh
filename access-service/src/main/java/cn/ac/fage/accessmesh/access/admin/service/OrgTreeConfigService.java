package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgTreeConfigCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgTreeConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgTreeConfigResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

/**
 * 组织树配置服务接口
 * <p>
 * 提供组织树配置的管理功能。
 * 组织树配置定义组织层级结构和展示规则，
 * 用于多租户场景下的组织架构定制。
 * </p>
 */
public interface OrgTreeConfigService {

    /**
     * 创建组织树配置
     * <p>
     * 创建新的组织树配置记录。
     * </p>
     *
     * @param config 组织树配置实体
     * @return 创建的配置ID
     */
    Long createOrgTreeConfig(OrgTreeConfigCreateReq req);

    /**
     * 更新组织树配置
     * <p>
     * 更新指定组织树配置的信息。
     * </p>
     *
     * @param config 组织树配置实体
     */
    void updateOrgTreeConfig(OrgTreeConfigUpdateReq req);

    /**
     * 批量删除组织树配置
     * <p>
     * 批量删除多个组织树配置记录。
     * </p>
     *
     * @param req 待删除的配置ID列表请求
     */
    void deleteOrgTreeConfigs(IdsReq req);

    /**
     * 设置默认组织树配置
     * <p>
     * 将指定配置设置为默认配置。
     * 默认配置用于未指定配置的租户。
     * </p>
     *
     * @param id 配置ID
     */
    void setDefault(Long id);

    /**
     * 获取组织树配置详情
     * <p>
     * 根据ID查询组织树配置详细信息。
     * </p>
     *
     * @param id 配置ID
     * @return 组织树配置响应
     */
    OrgTreeConfigResp getOrgTreeConfig(Long id);

    /**
     * 分页查询组织树配置
     * <p>
     * 查询系统中的组织树配置列表，支持分页。
     * </p>
     *
     * @param pageReq 分页请求参数
     * @return 分页组织树配置结果
     */
    PageResp<OrgTreeConfigResp> pageOrgTreeConfigs(PageReq pageReq);
}
