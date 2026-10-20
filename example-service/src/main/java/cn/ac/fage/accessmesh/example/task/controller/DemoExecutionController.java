package cn.ac.fage.accessmesh.example.task.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.*;
import cn.ac.fage.accessmesh.example.task.service.DemoExecutionAppService;
import cn.ac.fage.accessmesh.example.security.ExampleRequestIdentity;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/example")
public class DemoExecutionController {
    private final DemoExecutionAppService service;
    public DemoExecutionController(DemoExecutionAppService service) { this.service=service; }
    @PostMapping("/demo-snapshot/submit")
    public R<ExecutionResp> submit(@Valid @RequestBody SubmitReq r,HttpServletRequest h) { return R.ok(service.submit(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/demo-execution/get")
    public R<ExecutionResp> get(@Valid @RequestBody KeyReq r,HttpServletRequest h) { return R.ok(service.get(ExampleRequestIdentity.read(h),r.executionKey())); }
    @PostMapping("/demo-execution/page")
    public R<PageResp<ExecutionResp>> page(@Valid @RequestBody PageReq r,HttpServletRequest h) { return R.ok(service.page(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/demo-execution/download")
    public ResponseEntity<byte[]> download(@Valid @RequestBody KeyReq r,HttpServletRequest h) {
        byte[] bytes=service.download(ExampleRequestIdentity.read(h),r.executionKey());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"snapshot-"+r.executionKey()+".csv\"")
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
