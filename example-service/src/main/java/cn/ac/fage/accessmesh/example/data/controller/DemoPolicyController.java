package cn.ac.fage.accessmesh.example.data.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.data.dto.PolicyDtos.*;
import cn.ac.fage.accessmesh.example.data.service.DemoPolicyAppService;
import cn.ac.fage.accessmesh.example.security.ExampleRequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/example/demo-policy")
public class DemoPolicyController {
    private final DemoPolicyAppService service;
    public DemoPolicyController(DemoPolicyAppService service) { this.service=service; }
    @PostMapping("/get")
    public R<PolicyResp> get(@Valid @RequestBody GetReq r,HttpServletRequest h) { return R.ok(service.get(ExampleRequestIdentity.read(h),r)); }
    @PostMapping("/save")
    public R<PolicyResp> save(@Valid @RequestBody SaveReq r,HttpServletRequest h) { return R.ok(service.save(ExampleRequestIdentity.read(h),r)); }
}
