package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

/**
 * 组织服务接口
 * <p>
 * 提供组织管理相关的服务方法，包括组织的创建、更新、删除、查询等。
 * 支持单个创建和批量创建，以及组织树结构查询。
 * 组织用于构建企业的组织架构，支持层级结构。
 * </p>
 */
public interface OrgService {

    /**
     * 创建组织
     * <p>
     * 创建单个组织节点。
     * 设置组织名称、编码、父组织等属性。
     * </p>
     *
     * @param req 组织创建请求
     * @return 创建的组织ID
     */
    Long createOrg(OrgCreateReq req);

    /**
     * 批量创建组织
     * <p>
     * 批量创建多个组织节点。
     * 用于组织数据导入或初始化场景。
     * </p>
     *
     * @param req 批量创建请求
     * @return 批量操作结果，包含成功和失败的记录
     */
    BatchResultResp batchCreateOrgs(OrgBatchCreateReq req);

    /**
     * 更新组织
     * <p>
     * 更新指定组织的基本信息。
     * 包括组织名称、编码、负责人等属性。
     * </p>
     *
     * @param req 组织更新请求
     */
    void updateOrg(OrgUpdateReq req);

    /**
     * 删除组织
     * <p>
     * 删除指定组织及其所有子组织。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param id 组织ID
     */
    void deleteOrg(Long id);

    /**
     * 批量删除组织
     * <p>
     * 批量删除多个组织及其所有子组织。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的组织ID列表请求
     */
    void batchDeleteOrgs(IdsReq req);

    /**
     * 获取组织详情
     * <p>
     * 根据ID查询组织详细信息。
     * </p>
     *
     * @param id 组织ID
     * @return 组织详情响应
     */
    OrgResp getOrg(Long id);

    /**
     * 分页查询组织列表
     * <p>
     * 根据条件分页查询组织列表。
     * 支持按组织名称、编码等条件筛选。
     * </p>
     *
     * @param req 分页查询请求
     * @return 分页组织列表结果
     */
    PaginatedResult<OrgResp> pageOrgs(OrgPageReq req);

    /**
     * 获取组织树
     * <p>
     * 根据条件获取组织树结构。
     * 用于组织管理界面的树形展示。
     * </p>
     *
     * @param query 组织查询条件
     * @return 组织树列表
     */
    List<OrgResp> treeOrgs(OrgQuery query);

    /**
     * 获取组织的所有子孙组织ID
     * <p>
     * 使用递归查询获取指定组织的所有子孙组织ID。
     * 用于级联删除和权限计算场景。
     * </p>
     *
     * @param orgId 组织ID
     * @return 子孙组织ID列表
     */
    List<Long> getDescendantOrgIds(Long orgId);
}