package cn.ac.fage.accessmesh.access.role.controller;

import cn.ac.fage.accessmesh.access.role.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.role.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.access.role.service.UserRoleQueryAppService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户-角色控制器
 * <p>
 * T-ACCESS-006 起用户角色管理由权限域直接提供（/api/access/user-role/*），本控制器仅保留
 * 管理视图读接口（/api/access/user-role/view，经跨域只读查询服务聚合）；原写代理接口
 * （旧 /user-role/assign、/user-role/revoke）已删除（T-ADMIN-024，无映射 404）。
 * T-ACCESS-042：子路径由 list 改名 view——与权限轨持有角色查询（/api/access/user-role/list，
 * SDK 消费）统一命名空间后撞路径，管理轨改名对应 USER:VIEW 门禁语义。
 * </p>
 * <p>
 * 契约依据：{@code docs/design/access-service-api-contract.md} §10.1
 * </p>
 */
@RestController
@RequestMapping("/api/access/user-role")
public class AdminUserRoleController {

    private final UserRoleQueryAppService userRoleQueryService;

    public AdminUserRoleController(UserRoleQueryAppService userRoleQueryService) {
        this.userRoleQueryService = userRoleQueryService;
    }

    /**
     * 查询用户角色管理视图
     * <p>
     * 返回全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * POSITION 角色补充所属组织名等显示字段。
     * 门禁：USER:VIEW@userId。
     * </p>
     *
     * @param req 用户角色列表查询请求（含 userId）
     * @return 用户角色列表（{ items: [...] } 包装）
     */
    @PostMapping("/view")
    public R<ItemsResp<UserRoleItemResp>> listUserRoles(
        @Valid @RequestBody UserRoleListReq req) {
        return R.ok(new ItemsResp<>(userRoleQueryService.listUserRoles(req.userId())));
    }
}
