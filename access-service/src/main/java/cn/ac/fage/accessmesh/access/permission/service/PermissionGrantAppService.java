package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp;
import java.util.List;

/**
 * 权限授予与撤销管理服务接口
 * <p>
 * 提供角色权限的查询和聚合授予功能。
 * 写入唯一入口为 applyGrantPlan（记录级 plan 单事务原子执行）；
 * 旧写入口 batchGrant/batchRevoke/listChildren/addChildren/removeChild 已随
 * T-PERM-034 删除（2026-08-27 端点退役收口，无存量调用方）。
 * </p>
 */
public interface PermissionGrantAppService {

    /**
     * 原子应用记录级授权计划，并返回目标角色的完整持久化权限集合。
     */
    List<RolePermissionItemResp> applyGrantPlan(Long tenantId, ApplyGrantPlanReq req);

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
}
