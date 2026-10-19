package cn.ac.fage.accessmesh.example.data.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;
import cn.ac.fage.accessmesh.example.data.service.DemoDataAppService;
import cn.ac.fage.accessmesh.example.security.ExampleRequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/example/demo-data")
public class DemoDataController {
    private final DemoDataAppService service;
    public DemoDataController(DemoDataAppService service) { this.service=service; }
    @PostMapping("/fields")
    public R<FieldsResp> fields(@Valid @RequestBody FieldsReq r,HttpServletRequest h) { return R.ok(service.fields(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/page")
    public R<PageDataResp> page(@Valid @RequestBody PageReq r,HttpServletRequest h) { return R.ok(service.page(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/get")
    public R<RecordResp> get(@Valid @RequestBody GetReq r,HttpServletRequest h) { return R.ok(service.get(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/batch-get")
    public R<BatchResp> batch(@Valid @RequestBody BatchGetReq r,HttpServletRequest h) { return R.ok(service.batch(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/update")
    public R<UpdatedResp> update(@Valid @RequestBody UpdateReq r,HttpServletRequest h) { return R.ok(service.update(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/batch-update")
    public R<UpdatedResp> batchUpdate(@Valid @RequestBody BatchUpdateReq r,HttpServletRequest h) { return R.ok(service.batchUpdate(ExampleRequestIdentity.read(h),r)); }
}
