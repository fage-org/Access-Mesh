package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionVersionQueryReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionVersionResp;
import cn.ac.fage.accessmesh.permission.service.PermissionVersionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限版本控制器
 * <p>
 * 提供权限版本查询功能。
 * 权限版本用于缓存一致性检查，Gateway通过版本号判断缓存的权限快照是否过期。
 * 当角色权限变更时，版本号会递增，触发缓存失效。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/permission-version")
public class PermissionVersionController {

    private final PermissionVersionService permissionVersionService;

    /**
     * 构造函数注入依赖
     *
     * @param permissionVersionService 权限版本服务
     */
    public PermissionVersionController(PermissionVersionService permissionVersionService) {
        this.permissionVersionService = permissionVersionService;
    }

    /**
     * 查询权限版本号
     * <p>
     * 查询指定角色或全局的权限版本号。
     * Gateway定期查询版本号，与本地缓存版本比较，判断是否需要刷新缓存。
     * </p>
     *
     * @param req 版本查询请求，包含角色ID（可选）
     * @return 权限版本响应，包含版本号和更新时间
     */
    @PostMapping("/query")
    public PermResult<PermissionVersionResp> queryVersion(@Valid @RequestBody PermissionVersionQueryReq req) {
        return PermResult.success(permissionVersionService.queryVersion(TenantContextHolder.getTenantId(), req));
    }
}