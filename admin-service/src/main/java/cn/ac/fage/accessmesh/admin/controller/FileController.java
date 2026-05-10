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

/**
 * 文件管理控制器
 * <p>
 * 提供文件的上传、下载、删除、分页查询等功能。
 * 文件上传支持按业务类型分类存储。
 * 所有接口采用POST + JSON Body方式，上传接口使用multipart/form-data。
 * </p>
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;

    /**
     * 构造函数注入依赖
     *
     * @param fileService 文件管理服务
     */
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传文件
     * <p>
     * 上传文件到系统，支持按业务类型分类存储。
     * 文件上传后返回文件ID，可用于后续下载和管理。
     * </p>
     *
     * @param file    上传的文件对象
     * @param bizType 业务类型，用于文件分类，默认为"default"
     * @return 上传成功后的文件ID
     */
    @PostMapping("/upload")
    @AuditLog(module = "文件管理", action = "上传", targetType = "FILE")
    public PermResult<Long> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false, defaultValue = "default") String bizType) {
        return PermResult.success(fileService.uploadFile(file, bizType));
    }

    /**
     * 删除文件
     * <p>
     * 批量删除文件，会同时清理文件存储。
     * </p>
     *
     * @param req ID集合请求，包含待删除的文件ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    @AuditLog(module = "文件管理", action = "删除", targetType = "FILE")
    public PermResult<Void> deleteFiles(@Valid @RequestBody IdsReq req) {
        fileService.deleteFiles(req);
        return PermResult.success();
    }

    /**
     * 获取文件详情
     * <p>
     * 根据文件ID查询文件的完整信息，包括文件名、路径、大小等。
     * </p>
     *
     * @param req ID请求，包含文件ID
     * @return 文件详情信息
     */
    @PostMapping("/detail")
    public PermResult<FileResp> getFile(@Valid @RequestBody IdReq req) {
        return PermResult.success(fileService.getFile(req.id()));
    }

    /**
     * 分页查询文件列表
     * <p>
     * 支持按业务类型过滤，返回分页结果。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数和业务类型过滤条件
     * @return 分页文件列表结果
     */
    @PostMapping("/page")
    public PermResult<PaginatedResult<FileResp>> pageFiles(@Valid @RequestBody FilePageReq pageReq) {
        return PermResult.success(fileService.pageFiles(pageReq, pageReq.bizType()));
    }

    /**
     * 下载文件
     * <p>
     * 根据文件ID下载文件内容。
     * 文件内容直接写入HTTP响应流。
     * </p>
     *
     * @param req      ID请求，包含文件ID
     * @param response HTTP响应对象，用于输出文件内容
     * @param request  HTTP请求对象，用于获取客户端信息
     */
    @PostMapping("/download")
    public void downloadFile(@Valid @RequestBody IdReq req,
                             HttpServletResponse response,
                             HttpServletRequest request) {
        byte[] data = fileService.downloadFile(req.id(), response);
        try {
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            // 响应可能已提交，无法返回错误状态
            log.warn("文件下载IO失败: fileId={}, clientIP={}, error={}",
                req.id(), request.getRemoteAddr(), e.getMessage());
            log.debug("文件下载IO异常详情", e);
        }
    }
}