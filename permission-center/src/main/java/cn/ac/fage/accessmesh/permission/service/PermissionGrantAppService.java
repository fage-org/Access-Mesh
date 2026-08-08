package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionChildrenReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionRemoveChildReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemResp;
import java.util.List;

/**
 * 权限授予与撤销管理服务接口
 * <p>
 * 提供角色权限的批量授予、撤销、查询和管理功能。
 * 支持权限项的层级结构管理（添加/移除子权限）。
 * </p>
 */
public interface PermissionGrantAppService {

    /**
     * 原子应用记录级授权计划，并返回目标角色的完整持久化权限集合。
     */
    List<RolePermissionItemResp> applyGrantPlan(Long tenantId, ApplyGrantPlanReq req);

    /**
     * 批量授予角色权限
     * <p>
     * 对角色的权限进行批量操作（添加、更新、删除）。
     * 操作完成后返回角色权限项的完整列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限授予请求，包含要授予的权限列表
     * @return 操作完成后的权限项列表
     */
    List<RolePermissionItemResp> batchGrant(Long tenantId, RoleGrantReq req);

    /**
     * 批量撤销角色权限
     * <p>
     * 从角色中批量移除指定的权限项。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量撤销请求，包含要撤销的权限列表
     */
    void batchRevoke(Long tenantId, BatchRevokeReq req);

    /**
     * 查询角色的权限列表
     * <p>
     * 获取角色拥有的所有权限项，支持按条件筛选。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限列表查询请求
     * @return 权限项列表
     */
    List<RolePermissionItemResp> listPermissions(Long tenantId, RolePermissionListReq req);

    /**
     * 查询权限项的子权限列表
     * <p>
     * 获取指定权限项下的所有子权限项。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      子权限查询请求，指定父权限项
     * @return 子权限项列表
     */
    List<RolePermissionItemResp> listChildren(Long tenantId, RolePermissionChildrenReq req);

    /**
     * 添加子权限项
     * <p>
     * 为指定权限项添加子权限，构建权限层级结构。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      添加子权限请求，包含父权限ID和子权限列表
     * @return 添加后的子权限项列表
     */
    List<RolePermissionItemResp> addChildren(Long tenantId, RolePermissionAddChildReq req);

    /**
     * 移除子权限项
     * <p>
     * 从权限项中移除指定的子权限关系。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      移除子权限请求，指定父权限和要移除的子权限
     */
    void removeChild(Long tenantId, RolePermissionRemoveChildReq req);
}
