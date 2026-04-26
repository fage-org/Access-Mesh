package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.admin.service.FileService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    @AuditLog(module = "文件管理", action = "上传", targetType = "FILE")
    public PermResult<Long> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false, defaultValue = "default") String bizType) {
        return PermResult.success(fileService.uploadFile(file, bizType));
    }

    @PostMapping("/delete")
    @AuditLog(module = "文件管理", action = "删除", targetType = "FILE")
    public PermResult<Void> deleteFiles(@Valid @RequestBody IdsReq req) {
        fileService.deleteFiles(req);
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<FileResp> getFile(@Valid @RequestBody IdReq req) {
        return PermResult.success(fileService.getFile(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<FileResp>> pageFiles(@Valid @RequestBody PageReq pageReq,
                                                             @RequestParam(required = false) String bizType) {
        return PermResult.success(fileService.pageFiles(pageReq, bizType));
    }

    @PostMapping("/download")
    public void downloadFile(@Valid @RequestBody IdReq req, HttpServletResponse response) {
        byte[] data = fileService.downloadFile(req.id(), response);
        try {
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            // Response may already be committed — nothing to do
        }
    }
}
