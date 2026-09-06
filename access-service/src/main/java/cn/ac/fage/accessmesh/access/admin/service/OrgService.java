package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgUserItemResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

import java.util.List;

/**
 * 组织服务接口
 * <p>
 * 提供组织管理相关的服务方法，包括组织的创建、更新、删除、查询等。
 * 支持组织树结构查询。
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
    PageResp<OrgResp> pageOrgs(OrgPageReq req);

    /**
     * 获取组织树
     * <p>
     * 根据条件获取组织树结构。
     * 用于组织管理界面的树形展示。
     * </p>
     * <p>
     * T-ADMIN-021：支持 includePositions 组织+岗位一体树（岗位作为所属组织子节点，
     * 岗位节点按调用者 ORG:VIEW_POSITION 后端裁剪）；operationCode（VIEW/CREATE，
     * CREATE 限默认树）；treeConfigId 树配置子树裁剪（不传=默认树子树，用户决策契约字面）；
     * 响应统一包装 ItemsResp（控制器层）。契约：admin-service-api-contract §4.2.1。
     * </p>
     *
     * @param query 组织查询条件
     * @return 组织树列表（顶层为配置根节点单根；parentOrgId 给定时为该节点子树）
     */
    List<OrgResp> treeOrgs(OrgQuery query);

    /**
     * 查询组织/岗位下的用户列表
     * <p>
     * 查询指定组织或岗位下通过 user-org 关联的用户。
     * 用于岗位卡片展开后展示已分配用户。
     * </p>
     *
     * @param orgId 组织或岗位ID
     * @return 用户简要信息列表
     */
    List<OrgUserItemResp> listOrgUsers(Long orgId);
}