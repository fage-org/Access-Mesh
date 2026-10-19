package cn.ac.fage.accessmesh.example.data.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.example.data.dto.ImportDtos.*;
import cn.ac.fage.accessmesh.example.data.service.DemoImportAppService;
import cn.ac.fage.accessmesh.example.security.ExampleRequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/example/demo-data")
public class DemoImportController {
    private final DemoImportAppService service;
    public DemoImportController(DemoImportAppService service) { this.service=service; }
    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportResp> submit(@RequestPart("file") MultipartFile file,@Valid @RequestPart("request") ImportReq request,HttpServletRequest http) {
        return R.ok(service.submit(ExampleRequestIdentity.read(http),request,file));
    }
    @PostMapping("/import-status")
    public R<ImportResp> status(@Valid @RequestBody KeyReq request,HttpServletRequest http) { return R.ok(service.status(ExampleRequestIdentity.read(http),request)); }
    @PostMapping("/revoke-import")
    public R<ImportResp> revoke(@Valid @RequestBody KeyReq request,HttpServletRequest http) { return R.ok(service.revoke(ExampleRequestIdentity.read(http),request)); }
}
