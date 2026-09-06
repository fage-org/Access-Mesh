package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserOrgRemoveReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserOrgSetPrimaryReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户组织关联管理控制器
 * <p>
 * 提供用户与组织关联的分配、移除、设置主组织等功能。
 * 用户可以属于多个组织，通过组织获得角色和权限。
 * 主组织用于确定用户的核心归属，影响数据范围权限。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/user-org")
public class UserOrgController {

    private final UserOrgService userOrgService;

    /**
     * 构造函数注入依赖
     *
     * @param userOrgService 用户组织关联服务
     */
    public UserOrgController(UserOrgService userOrgService) {
        this.userOrgService = userOrgService;
    }

    /**
     * 分配用户到组织
     * <p>
     * 将用户分配到指定的组织列表，用户将获得这些组织的权限。
     * </p>
     *
     * @param req 用户组织分配请求，包含用户ID和组织ID列表
     * @return 操作成功结果
     */
    @PostMapping("/assign")
    public R<Void> assignUserToOrgs(@Valid @RequestBody UserOrgAssignReq req) {
        userOrgService.assignUserToOrgs(req);
        return R.ok();
    }

    /**
     * 从组织移除用户
     * <p>
     * 将用户从指定组织中移除，用户将失去该组织的权限。
     * </p>
     *
     * @param req 用户组织移除请求，包含用户ID和组织ID
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public R<Void> removeUserFromOrg(@Valid @RequestBody UserOrgRemoveReq req) {
        userOrgService.removeUserFromOrg(req.userId(), req.orgId());
        return R.ok();
    }

    /**
     * 设置用户主组织
     * <p>
     * 将指定组织设置为用户的主组织。
     * 主组织影响用户的数据范围权限判定。
     * </p>
     *
     * @param req 设置主组织请求，包含用户ID和组织ID
     * @return 操作成功结果
     */
    @PostMapping("/set-primary")
    public R<Void> setPrimaryOrg(@Valid @RequestBody UserOrgSetPrimaryReq req) {
        userOrgService.setPrimaryOrg(req.userId(), req.orgId());
        return R.ok();
    }

    /**
     * 查询用户所属组织列表
     * <p>
     * 查询指定用户所属的所有组织，包括主组织信息。
     * </p>
     *
     * @param req ID请求，包含用户ID
     * @return 用户组织列表，包含组织基本信息和是否为主组织标记
     */
    @PostMapping("/list")
    public R<List<UserPageItemResp.OrgBrief>> getUserOrgs(@Valid @RequestBody IdReq req) {
        return R.ok(userOrgService.getUserOrgs(req.id()));
    }
}