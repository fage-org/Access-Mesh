package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.dto.req.UserRoleAssignReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserRoleRevokeReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户-角色代理控制器
 * <p>
 * 代理 permission-center 的用户角色查询/分配/回收接口。
 * 前端使用 admin 数字 ID，Controller 内部完成 ID ↔ 业务键翻译。
 * 仅服务功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），排除 ORG/POSITION（后者走 /user-org/*）。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4
 */
@RestController
@RequestMapping("/user-role")
public class UserRoleController {

    private final RoleProxyService roleProxyService;

    public UserRoleController(RoleProxyService roleProxyService) {
        this.roleProxyService = roleProxyService;
    }

    /**
     * 查询用户角色列表
     * <p>
     * 返回全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * 代理层补 relationOrgName 等显示字段。
     * </p>
     *
     * @param req 用户角色列表查询请求（含 userId）
     * @return 用户角色列表（{ items: [...] } 包装）
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<UserRoleItemResp>> listUserRoles(
        @Valid @RequestBody UserRoleListReq req) {
        return PermResult.success(new ItemsResp<>(roleProxyService.listUserRoles(req.userId())));
    }

    /**
     * 为用户分配功能角色
     * <p>
     * 代理 permission-center /api/perm/user-role/assign。
     * 前端传入 admin 数字 ID（userId + roleId），代理层完成 ID → 业务键翻译。
     * 对目标角色做实例级 ADMIN_ROLE:GRANT 权限校验。
     * 仅允许分配功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），ORG/POSITION 走 /user-org/*。
     * </p>
     *
     * @param req 用户角色分配请求（含 userId + roleId）
     * @return 操作成功结果
     */
    @PostMapping("/assign")
    public PermResult<Void> assignRole(
        @Valid @RequestBody UserRoleAssignReq req) {
        roleProxyService.assignRole(req.userId(), req.roleId(), req.validFrom(), req.validTo());
        return PermResult.success();
    }

    /**
     * 回收用户功能角色
     * <p>
     * 代理 permission-center /api/perm/user-role/revoke。
     * 前端传入 admin 数字 ID（userId + roleId），代理层完成 ID → 业务键翻译。
     * 对目标角色做实例级 ADMIN_ROLE:REVOKE 权限校验。
     * 仅允许回收功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），ORG/POSITION 走 /user-org/*。
     * </p>
     *
     * @param req 用户角色回收请求（含 userId + roleId）
     * @return 操作成功结果
     */
    @PostMapping("/revoke")
    public PermResult<Void> revokeRole(
        @Valid @RequestBody UserRoleRevokeReq req) {
        roleProxyService.revokeRole(req.userId(), req.roleId());
        return PermResult.success();
    }
}
