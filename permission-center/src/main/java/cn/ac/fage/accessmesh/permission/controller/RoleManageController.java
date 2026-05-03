package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleTreeReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleMoveReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleSummaryResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.permission.service.GroupRoleManageService;
import cn.ac.fage.accessmesh.permission.service.RoleManageService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Role management API.
 */
@RestController
@RequestMapping("/api/perm/abstract-role")
public class RoleManageController {

    private final RoleManageService roleManageService;
    private final GroupRoleManageService groupRoleManageService;

    public RoleManageController(RoleManageService roleManageService,
                                GroupRoleManageService groupRoleManageService) {
        this.roleManageService = roleManageService;
        this.groupRoleManageService = groupRoleManageService;
    }

    @PostMapping("/create")
    public PermResult<RoleResp> createRole(@Valid @RequestBody RoleCreateReq req) {
        return PermResult.success(roleManageService.createRole(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<RoleResp> getRole(@Valid @RequestBody IdReq req) {
        return PermResult.success(roleManageService.getRole(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/update")
    public PermResult<RoleResp> updateRole(@Valid @RequestBody RoleUpdateReq req) {
        return PermResult.success(roleManageService.updateRole(
                TenantContextHolder.getTenantId(), req.roleId(), req.name(), req.status(), req.sortOrder(), req.extra(), null));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteRole(@Valid @RequestBody IdsReq req) {
        roleManageService.deleteRoles(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/tree")
    public PermResult<ItemsResp<RoleTreeResp>> getRoleTree(@Valid @RequestBody RoleTreeReq req) {
        return PermResult.success(new ItemsResp<>(
            roleManageService.getRoleTree(TenantContextHolder.getTenantId(), req.domainCode())
        ));
    }

    @PostMapping("/list")
    public PermResult<PaginatedResp<RoleResp>> listRoles(@Valid @RequestBody RoleListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = roleManageService.countRoles(tenantId, req.domainCode(), req.roleTypeCode(), req.keyword());
        List<RoleResp> items = roleManageService.listRoles(
            tenantId, req.domainCode(), req.roleTypeCode(), req.keyword(), offset, pageSize
        );
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    @PostMapping("/move")
    public PermResult<Void> moveRole(@Valid @RequestBody RoleMoveReq req) {
        roleManageService.moveRole(TenantContextHolder.getTenantId(), req.roleId(), req.parentId(), null);
        return PermResult.success();
    }

    @PostMapping("/extra-roles/list")
    public PermResult<ItemsResp<RoleSummaryResp>> listExtraRoles(@Valid @RequestBody GroupRoleExtraRolesListReq req) {
        return PermResult.success(new ItemsResp<>(
            groupRoleManageService.listGroupRoleExtraRoles(TenantContextHolder.getTenantId(), req)
        ));
    }

    @PostMapping("/extra-roles/add")
    public PermResult<Void> addExtraRole(@Valid @RequestBody GroupRoleExtraRoleReq req) {
        groupRoleManageService.addGroupRoleExtraRole(TenantContextHolder.getTenantId(), req, null);
        return PermResult.success();
    }

    @PostMapping("/extra-roles/remove")
    public PermResult<Void> removeExtraRole(@Valid @RequestBody GroupRoleExtraRoleReq req) {
        groupRoleManageService.removeGroupRoleExtraRole(TenantContextHolder.getTenantId(), req, null);
        return PermResult.success();
    }
}