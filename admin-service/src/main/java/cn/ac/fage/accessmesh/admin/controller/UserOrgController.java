package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserOrgRemoveReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserOrgSetPrimaryReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user-org")
public class UserOrgController {

    private final UserOrgService userOrgService;

    public UserOrgController(UserOrgService userOrgService) {
        this.userOrgService = userOrgService;
    }

    @PostMapping("/assign")
    @AuditLog(module = "用户组织关联", action = "分配", targetType = "USER_ORG")
    public PermResult<Void> assignUserToOrgs(@Valid @RequestBody UserOrgAssignReq req) {
        userOrgService.assignUserToOrgs(req);
        return PermResult.success();
    }

    @PostMapping("/remove")
    @AuditLog(module = "用户组织关联", action = "移除", targetType = "USER_ORG")
    public PermResult<Void> removeUserFromOrg(@Valid @RequestBody UserOrgRemoveReq req) {
        userOrgService.removeUserFromOrg(req.userId(), req.orgId());
        return PermResult.success();
    }

    @PostMapping("/set-primary")
    @AuditLog(module = "用户组织关联", action = "设置主组织", targetType = "USER_ORG")
    public PermResult<Void> setPrimaryOrg(@Valid @RequestBody UserOrgSetPrimaryReq req) {
        userOrgService.setPrimaryOrg(req.userId(), req.orgId());
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<List<UserPageItemResp.OrgBrief>> getUserOrgs(@Valid @RequestBody IdReq req) {
        return PermResult.success(userOrgService.getUserOrgs(req.id()));
    }
}
