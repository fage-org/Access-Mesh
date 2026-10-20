package cn.ac.fage.accessmesh.example.task.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.task.dto.TaskDtos.*;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.ExecutionResp;
import cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService;
import cn.ac.fage.accessmesh.example.security.ExampleRequestIdentity;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/example/demo-task")
public class DemoTaskController {
    private final DemoTaskAppService service;
    public DemoTaskController(DemoTaskAppService service) { this.service=service; }
    @PostMapping("/create")
    public R<TaskResp> create(@Valid @RequestBody CreateReq r,HttpServletRequest h) { return R.ok(service.create(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/get")
    public R<TaskResp> get(@Valid @RequestBody KeyReq r,HttpServletRequest h) { return R.ok(service.get(ExampleRequestIdentity.read(h),r.taskId())); }
    @PostMapping("/page")
    public R<PageResp<TaskResp>> page(@Valid @RequestBody PageReq r,HttpServletRequest h) { return R.ok(service.page(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/update")
    public R<TaskResp> update(@Valid @RequestBody UpdateReq r,HttpServletRequest h) { return R.ok(service.update(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/set-enabled")
    public R<TaskResp> enabled(@Valid @RequestBody EnabledReq r,HttpServletRequest h) { return R.ok(service.setEnabled(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/trigger")
    public R<ExecutionResp> trigger(@Valid @RequestBody TriggerReq r,HttpServletRequest h) { return R.ok(service.trigger(ExampleRequestIdentity.read(h),r)); }
}
