package cn.ac.fage.accessmesh.example.metadata.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.security.ExampleRequestIdentity;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.*;
import cn.ac.fage.accessmesh.example.metadata.service.DemoMetadataAppService;
import cn.ac.fage.accessmesh.example.metadata.service.DemoPublicationAppService;
import cn.ac.fage.accessmesh.example.metadata.dto.PublicationDtos.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/example")
public class DemoMetadataController {
    private final DemoMetadataAppService service;
    private final DemoPublicationAppService publications;
    public DemoMetadataController(DemoMetadataAppService service, DemoPublicationAppService publications) {
        this.service = service; this.publications = publications;
    }
    @PostMapping("/demo-report/create")
    public R<ReportResp> create(@Valid @RequestBody ReportCreateReq r, HttpServletRequest h) { return R.ok(service.create(caller(h), r)); }
    @PostMapping("/demo-report/get")
    public R<ReportResp> get(@Valid @RequestBody ReportGetReq r, HttpServletRequest h) { return R.ok(service.get(caller(h), r)); }
    @PostMapping("/demo-report/update")
    public R<ReportResp> update(@Valid @RequestBody ReportUpdateReq r, HttpServletRequest h) { return R.ok(service.update(caller(h), r)); }
    @PostMapping("/demo-field/register")
    public R<FieldRegistrationResp> register(@Valid @RequestBody FieldRegisterReq r, HttpServletRequest h) { return R.ok(service.register(caller(h), r)); }
    @PostMapping("/demo-field/list")
    public R<PageResp<FieldResp>> fields(@Valid @RequestBody FieldListReq r, HttpServletRequest h) { return R.ok(service.fields(caller(h), r)); }
    @PostMapping("/demo-field/update")
    public R<Void> updateField(@Valid @RequestBody FieldUpdateReq r, HttpServletRequest h) { service.updateField(caller(h), r); return R.ok(); }
    @PostMapping("/demo-field/remove")
    public R<Void> removeFields(@Valid @RequestBody FieldRemoveReq r, HttpServletRequest h) { service.removeFields(caller(h), r); return R.ok(); }
    @PostMapping("/demo-scope/save")
    public R<ScopeResp> saveScope(@Valid @RequestBody ScopeSaveReq r, HttpServletRequest h) { return R.ok(service.saveScope(caller(h), r)); }
    @PostMapping("/demo-publication/get")
    public R<PublicationResp> publication(@Valid @RequestBody PublicationGetReq r, HttpServletRequest h) { return R.ok(publications.get(caller(h), r)); }
    @PostMapping("/demo-publication/page")
    public R<PageResp<PublicationResp>> publicationPage(@Valid @RequestBody PublicationPageReq r, HttpServletRequest h) { return R.ok(publications.page(caller(h), r)); }
    @PostMapping("/demo-publication/retry")
    public R<PublicationResp> retry(@Valid @RequestBody PublicationGetReq r, HttpServletRequest h) { return R.ok(publications.retry(caller(h), r)); }
    private static Caller caller(HttpServletRequest request) {
        return ExampleRequestIdentity.read(request);
    }
}
