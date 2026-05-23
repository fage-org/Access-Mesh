package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;

import java.util.List;

/**
 * 角色管理服务接口
 * <p>
 * 提供角色的CRUD操作、树结构管理、启用/禁用等功能。
 * 角色是权限分配的主体，可以是基础角色或分组角色。
 * </p>
 */
public interface RoleManageService {

    /**
     * 创建角色
     * <p>
     * 创建新的角色实体，设置角色类型、编码、名称等属性。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        角色创建请求
     * @param operatorId 操作者ID
     * @return 创建的角色详情
     */
    RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId);

    /**
     * 获取角色详情
     * <p>
     * 根据角色ID查询角色实体详情。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色详情
     */
    RoleResp getRole(Long tenantId, Long roleId);

    /**
     * 更新角色基本信息
     * <p>
     * 更新角色的名称、状态、排序序号和扩展信息。
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param name       角色名称
     * @param status     状态（0禁用 1启用）
     * @param sortOrder  排序序号
     * @param extra      扩展信息（JSON格式）
     * @param operatorId 操作者ID
     * @return 更新后的角色详情
     */
    RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId);

    /**
     * 移动角色
     * <p>
     * 将角色移动到新的父节点下，调整角色的层级位置。
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param parentId   新父节点ID
     * @param operatorId 操作者ID
     */
    void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId);

    /**
     * 批量删除角色
     * <p>
     * 批量软删除多个角色实体。
     * </p>
     *
     * @param tenantId  租户ID
     * @param roleIds   角色ID列表
     * @param operatorId 操作者ID
     */
    void deleteRoles(Long tenantId, List<Long> roleIds, Long operatorId);

    /**
     * 获取角色树
     * <p>
     * 获取租户的角色树结构。
      * domainCode为空或空白时返回全部角色；否则按域分类规则判断当前域是否覆盖角色管理资源类型。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码，可选
     * @return 角色树响应列表
     */
    List<RoleTreeResp> getRoleTree(Long tenantId, String domainCode);

    /**
     * 查询角色列表
     * <p>
     * 获取租户的角色扁平列表，可按业务域、角色类型和关键词筛选，支持分页。
     * </p>
     *
     * @param tenantId     租户ID
     * @param domainCode   业务域编码，可选
     * @param roleTypeCode 角色类型编码，可选
     * @param keyword      关键词，可选
     * @param offset       分页偏移量
     * @param limit        每页条数
     * @return 角色列表
     */
    List<RoleResp> listRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword, int offset, int limit);

    /**
     * 统计角色数量
     * <p>
     * 统计满足条件的角色总数，用于分页计算。
     * </p>
     *
     * @param tenantId     租户ID
     * @param domainCode   业务域编码，可选
     * @param roleTypeCode 角色类型编码，可选
     * @param keyword      关键词，可选
     * @return 角色数量
     */
    long countRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword);
}