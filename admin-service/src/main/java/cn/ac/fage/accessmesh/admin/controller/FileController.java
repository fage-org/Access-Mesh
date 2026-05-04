package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.admin.service.FileService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

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
    public PermResult<PaginatedResult<FileResp>> pageFiles(@Valid @RequestBody FilePageReq pageReq) {
        return PermResult.success(fileService.pageFiles(pageReq, pageReq.bizType()));
    }

    @PostMapping("/download")
    public void downloadFile(@Valid @RequestBody IdReq req,
                             HttpServletResponse response,
                             HttpServletRequest request) {
        byte[] data = fileService.downloadFile(req.id(), response);
        try {
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            // Response may already be committed — cannot return error status
            log.warn("File download IO failed: fileId={}, clientIP={}, error={}",
                req.id(), request.getRemoteAddr(), e.getMessage());
            log.debug("File download IO exception details", e);
        }
    }
}
