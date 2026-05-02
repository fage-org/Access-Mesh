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
 * Core permission check service — auth check, query, and interface snapshot.
 */
public interface PermissionService {

    /**
     * Single auth check using stable business keys.
     * tenantId is obtained from TenantContextHolder.
     */
    AuthCheckResp check(Long tenantId, AuthCheckReq req);

    /**
     * Batch auth check for multiple resource+operation combinations.
     */
    BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req);

    /**
     * Gateway callback: check permission by serviceCode + httpMethod + path.
     */
    CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req);

    /**
     * Query resources accessible to a subject for a given resource type and operation.
     */
    QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req);

    /**
     * Query scope resources within a primary resource context (DIRECT \u222a DEPENDENT).
     */
    QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req);

    /**
     * Interface snapshot for Gateway consumption (optional optimisation path).
     */
    InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req);

    /**
     * Query permission tree from a starting resource node.
     * Returns accessible resources in ancestor/descendant directions.
     */
    PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req);
}
