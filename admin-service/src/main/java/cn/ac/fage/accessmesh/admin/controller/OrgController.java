package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.*;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.admin.service.OrgService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/org")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @PostMapping("/create")
    @AuditLog(module = "组织管理", action = "创建", targetType = "ORG")
    public PermResult<Long> createOrg(@Valid @RequestBody OrgCreateReq req) {
        return PermResult.success(orgService.createOrg(req));
    }

    @PostMapping("/update")
    @AuditLog(module = "组织管理", action = "修改", targetType = "ORG")
    public PermResult<Void> updateOrg(@Valid @RequestBody OrgUpdateReq req) {
        orgService.updateOrg(req);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "组织管理", action = "删除", targetType = "ORG")
    public PermResult<Void> deleteOrg(@Valid @RequestBody IdReq req) {
        orgService.deleteOrg(req.id());
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<OrgResp> getOrg(@Valid @RequestBody IdReq req) {
        return PermResult.success(orgService.getOrg(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<OrgResp>> pageOrgs(@Valid @RequestBody PageReq pageReq,
                                                           OrgQuery query) {
        return PermResult.success(orgService.pageOrgs(pageReq, query));
    }

    @PostMapping("/tree")
    public PermResult<List<OrgResp>> treeOrgs(OrgQuery query) {
        return PermResult.success(orgService.treeOrgs(query));
    }
}
