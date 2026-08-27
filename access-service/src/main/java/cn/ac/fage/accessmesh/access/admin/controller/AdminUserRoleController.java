package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.application.query.UserRoleQueryService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户-角色控制器
 * <p>
 * T-ACCESS-006 起用户角色管理由权限域直接提供（/api/perm/user-role/*），本控制器仅保留
 * 读接口（/user-role/list，经跨域只读查询服务聚合）；原写代理接口（/user-role/assign、
 * /user-role/revoke）已删除（T-ADMIN-024，无映射 404）。
 * </p>
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4
 * </p>
 */
@RestController
@RequestMapping("/user-role")
public class AdminUserRoleController {

    private final UserRoleQueryService userRoleQueryService;

    public AdminUserRoleController(UserRoleQueryService userRoleQueryService) {
        this.userRoleQueryService = userRoleQueryService;
    }

    /**
     * 查询用户角色列表
     * <p>
     * 返回全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * POSITION 角色补充所属组织名等显示字段。
     * 门禁：USER:VIEW@userId。
     * </p>
     *
     * @param req 用户角色列表查询请求（含 userId）
     * @return 用户角色列表（{ items: [...] } 包装）
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<UserRoleItemResp>> listUserRoles(
        @Valid @RequestBody UserRoleListReq req) {
        return PermResult.success(new ItemsResp<>(userRoleQueryService.listUserRoles(req.userId())));
    }
}
