package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleTreeReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleDetailReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleMoveReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.access.permission.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器
 * <p>
 * 提供角色的CRUD操作、树结构查询、角色移动等功能。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p><p>
 * T-PERM-043：GROUP_ROLE 专用写入口（/extra-roles/add|remove|list）已删除，
 * 通用 create/update 入口显式拒绝 GROUP_ROLE（首期功能角色仅 BASIC_ROLE）。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/abstract-role")
public class PermRoleController {

    private final RoleManageAppService roleManageAppService;

    /**
     * 构造函数注入依赖
     *
     * @param roleManageAppService       角色管理服务
     */
    public PermRoleController(RoleManageAppService roleManageAppService) {
        this.roleManageAppService = roleManageAppService;
    }

    /**
     * 创建角色
     * <p>
     * 在指定域下创建新角色，支持设置角色名称、类型、父角色等属性。
     * </p>
     *
     * @param req 角色创建请求，包含角色基本信息
     * @return 创建成功的角色详情
     */
    @PostMapping("/create")
    public PermResult<RoleResp> createRole(@Valid @RequestBody RoleCreateReq req) {
        return PermResult.success(roleManageAppService.createRole(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取角色详情
     * <p>
     * 用业务键二元组（roleTypeCode + roleExternalId）定位角色（T-PERM-022），
     * 未命中返回 data=null。
     * </p>
     *
     * @param req 业务键请求
     * @return 角色详情信息
     */
    @PostMapping("/detail")
    public PermResult<RoleResp> getRole(@Valid @RequestBody RoleDetailReq req) {
        return PermResult.success(roleManageAppService.getRole(
                TenantContextHolder.getTenantId(), req.roleTypeCode(), req.roleExternalId()));
    }

    /**
     * 更新角色信息
     * <p>
     * 更新角色的名称、状态、排序顺序等属性。
     * </p>
     *
     * @param req 角色更新请求，包含待更新的角色ID和新属性值
     * @return 更新后的角色详情
     */
    @PostMapping("/update")
    public PermResult<RoleResp> updateRole(@Valid @RequestBody RoleUpdateReq req) {
        return PermResult.success(roleManageAppService.updateRole(
                TenantContextHolder.getTenantId(), req.roleId(), req.name(), req.status(), req.sortOrder(), req.extra(), null));
    }

    /**
     * 删除角色
     * <p>
     * 批量删除角色，会同时处理角色下的权限配置和用户关联。
     * </p>
     *
     * @param req ID集合请求，包含待删除的角色ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteRole(@Valid @RequestBody IdsReq req) {
        roleManageAppService.deleteRoles(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 查询角色树
     * <p>
     * 返回指定域下的角色层级树结构，用于前端展示角色组织关系。
     * </p>
     *
     * @param req 角色树查询请求，包含域编码
     * @return 角色树结构列表
     */
    @PostMapping("/tree")
    public PermResult<ItemsResp<RoleTreeResp>> getRoleTree(@Valid @RequestBody RoleTreeReq req) {
        return PermResult.success(new ItemsResp<>(
            roleManageAppService.getRoleTree(TenantContextHolder.getTenantId(), req.domainCode(),
                Boolean.TRUE.equals(req.enabledOnly()))
        ));
    }

    /**
     * 分页查询角色列表
     * <p>
     * 支持按域、角色类型、关键字过滤，返回分页结果。
     * </p>
     *
     * @param req 角色列表查询请求，包含分页参数和过滤条件
     * @return 分页角色列表结果
     */
    @PostMapping("/list")
    public PermResult<PaginatedResp<RoleResp>> listRoles(@Valid @RequestBody RoleListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = roleManageAppService.countRoles(
            tenantId, req.domainCode(), req.roleTypeCode(), req.roleTypeCodes(), req.keyword()
        );
        List<RoleResp> items = roleManageAppService.listRoles(
            tenantId, req.domainCode(), req.roleTypeCode(), req.roleTypeCodes(), req.keyword(), offset, pageSize
        );
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    /**
     * 移动角色
     * <p>
     * 将角色移动到新的父角色下，调整角色在树结构中的位置。
     * </p>
     *
     * @param req 角色移动请求，包含角色ID和目标父角色ID
     * @return 操作成功结果
     */
    @PostMapping("/move")
    public PermResult<Void> moveRole(@Valid @RequestBody RoleMoveReq req) {
        roleManageAppService.moveRole(TenantContextHolder.getTenantId(), req.roleId(), req.parentId(), null);
        return PermResult.success();
    }
}
