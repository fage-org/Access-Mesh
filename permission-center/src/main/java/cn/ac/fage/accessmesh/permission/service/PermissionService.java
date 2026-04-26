package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;

/**
 * Core permission check service — internal SDK contract.
 */
public interface PermissionService {

    /**
     * Internal SDK permission check (used by gateway callback).
     */
    PermCheckResp checkPermission(PermCheckReq req);

    /**
     * Detailed auth check with full chain (user status → role resolution → conflict filter → auth match → condition eval).
     */
    AuthCheckResp check(AuthCheckReq req);

    /**
     * Batch auth check for multiple resource+operation combinations.
     */
    BatchAuthCheckResp batchCheck(BatchAuthCheckReq req);

    /**
     * Gateway callback: check permission by serviceCode + httpMethod + path.
     */
    CheckInterfaceResp checkInterface(CheckInterfaceReq req);
}
