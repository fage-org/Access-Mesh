package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionTreeReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;

/**
 * 核心权限检查服务接口
 * <p>
 * 提供权限验证、查询和接口快照功能。
 * 是权限系统的核心入口，用于授权检查和可访问资源查询。
 * </p>
 */
public interface PermissionService {

    /**
     * 单次权限检查
     * <p>
     * 使用稳定的业务键进行权限验证。
     * tenantId 从 TenantContextHolder 中获取。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限检查请求，包含资源类型、资源编码、操作类型等
     * @return 权限检查响应，包含是否授权和相关信息
     */
    AuthCheckResp check(Long tenantId, AuthCheckReq req);

    /**
     * 批量权限检查
     * <p>
     * 对多个资源+操作组合进行批量权限验证。
     * 提高批量场景下的检查效率。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量权限检查请求
     * @return 批量权限检查响应，包含每个组合的检查结果
     */
    BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req);

    /**
     * 接口权限检查（Gateway回调）
     * <p>
     * Gateway通过服务编码、HTTP方法和路径进行权限检查。
     * 用于API网关的动态权限拦截。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口检查请求，包含serviceCode、httpMethod、path
     * @return 接口检查响应，包含是否授权和匹配的API信息
     */
    CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req);

    /**
     * 查询可访问资源
     * <p>
     * 查询主体对指定资源类型和操作可访问的资源列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      资源查询请求，包含资源类型、操作类型等
     * @return 可访问资源响应
     */
    QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req);

    /**
     * 查询范围资源
     * <p>
     * 在主资源上下文中查询范围资源。
     * 包括直接范围（DIRECT）和依赖范围（DEPENDENT）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      范围查询请求
     * @return 范围资源响应
     */
    QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req);

    /**
     * 接口快照查询
     * <p>
     * 为Gateway提供接口权限快照（可选的优化路径）。
     * 用于减少实时查询开销。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口快照请求
     * @return 接口快照响应
     */
    InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req);

    /**
     * 查询权限树
     * <p>
     * 从起始资源节点查询权限树。
     * 返回在祖先和子孙方向上可访问的资源。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限树查询请求，指定起始节点和方向
     * @return 权限树响应
     */
    PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req);
}