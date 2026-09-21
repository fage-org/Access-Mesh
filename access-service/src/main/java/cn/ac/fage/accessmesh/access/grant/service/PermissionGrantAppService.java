package cn.ac.fage.accessmesh.access.grant.service;

import cn.ac.fage.accessmesh.access.grant.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.dto.req.PreviewGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.access.grant.dto.req.SubPermAllowedTypesReq;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp;
import cn.ac.fage.accessmesh.access.grant.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.access.grant.dto.resp.SubPermAllowedTypesResp;
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
     * 授撤影响预览（T-PERM-073，契约 §11.4.1）——只读，仅供参考。
     * <p>门禁/角色有效性与保存同源（ROLE:MANAGE + 启用角色 + 委托/形状校验经纯准备复用）；
     * REPEATABLE_READ 只读一致事务，不写任何数据（不创建 INLINE、不产生权限变更标记）。</p>
     */
    GrantPlanPreviewResp previewGrantPlan(Long tenantId, PreviewGrantPlanReq req);

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
     * 子权限允许类型只读查询（总册 §11.5）
     * <p>
     * 门禁：目标角色 ROLE:VIEW 实例级（resolveRoleId 失败 20001，无 VIEW 抛
     * SecurityException——不采用空结果掩盖鉴权失败）；策略结果由
     * {@code resolveSubPermissionPolicy} 直接映射（读写同源）。
     * </p>
     */
    SubPermAllowedTypesResp subPermAllowedTypes(Long tenantId, SubPermAllowedTypesReq req);
}
