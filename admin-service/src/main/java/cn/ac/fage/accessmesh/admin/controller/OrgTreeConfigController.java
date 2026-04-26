package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.service.OrgTreeConfigService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/org-tree-config")
public class OrgTreeConfigController {

    private final OrgTreeConfigService orgTreeConfigService;

    public OrgTreeConfigController(OrgTreeConfigService orgTreeConfigService) {
        this.orgTreeConfigService = orgTreeConfigService;
    }

    @PostMapping("/create")
    @AuditLog(module = "组织树配置", action = "创建")
    public PermResult<Long> createOrgTreeConfig(@Valid @RequestBody SysOrgTreeConfig config) {
        return PermResult.success(orgTreeConfigService.createOrgTreeConfig(config));
    }

    @PostMapping("/update")
    @AuditLog(module = "组织树配置", action = "更新")
    public PermResult<Void> updateOrgTreeConfig(@Valid @RequestBody SysOrgTreeConfig config) {
        orgTreeConfigService.updateOrgTreeConfig(config);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "组织树配置", action = "删除")
    public PermResult<Void> deleteOrgTreeConfigs(@Valid @RequestBody IdsReq req) {
        orgTreeConfigService.deleteOrgTreeConfigs(req);
        return PermResult.success();
    }

    @PostMapping("/set-default")
    @AuditLog(module = "组织树配置", action = "设置默认")
    public PermResult<Void> setDefault(@Valid @RequestBody IdReq req) {
        orgTreeConfigService.setDefault(req.id());
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<SysOrgTreeConfig> getOrgTreeConfig(@Valid @RequestBody IdReq req) {
        return PermResult.success(orgTreeConfigService.getOrgTreeConfig(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<SysOrgTreeConfig>> pageOrgTreeConfigs(@Valid @RequestBody PageReq req) {
        return PermResult.success(orgTreeConfigService.pageOrgTreeConfigs(req));
    }
}
