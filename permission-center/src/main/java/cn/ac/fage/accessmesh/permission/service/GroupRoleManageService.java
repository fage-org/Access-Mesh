package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleSummaryResp;

import java.util.List;

/**
 * 分组角色管理服务接口
 * <p>
 * 提供分组角色的额外角色关联管理功能。
 * 分组角色可以关联基础角色，实现角色组的权限继承。
 * </p>
 */
public interface GroupRoleManageService {

    /**
     * 为分组角色添加额外角色关联
     * <p>
     * 将基础角色添加到分组角色的额外角色列表中。
     * 分组角色的成员将继承这些基础角色的权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        分组角色额外角色请求
     * @param operatorId 操作者ID
     */
    void addGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId);

    /**
     * 移除分组角色的额外角色关联
     * <p>
     * 将基础角色从分组角色的额外角色列表中移除。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        分组角色额外角色请求
     * @param operatorId 操作者ID
     */
    void removeGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId);

    /**
     * 查询分组角色的额外角色列表
     * <p>
     * 返回分组角色关联的所有基础角色列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      分组角色额外角色列表查询请求
     * @return 角色摘要列表
     */
    List<RoleSummaryResp> listGroupRoleExtraRoles(Long tenantId, GroupRoleExtraRolesListReq req);
}