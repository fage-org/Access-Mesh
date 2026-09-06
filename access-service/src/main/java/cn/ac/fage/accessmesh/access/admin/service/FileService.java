package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 * <p>
 * 提供文件管理相关的服务方法，包括文件上传、下载、删除、查询等。
 * 支持按业务类型分类管理文件。
 * </p>
 */
public interface FileService {

    /**
     * 上传文件
     * <p>
     * 上传单个文件并关联业务类型。
     * 文件存储后返回文件ID，可用于后续下载和引用。
     * </p>
     *
     * @param file    待上传的文件
     * @param bizType 业务类型，用于文件分类管理
     * @return 上传后的文件ID
     */
    Long uploadFile(MultipartFile file, String bizType);

    /**
     * 批量删除文件
     * <p>
     * 批量删除多个文件记录及其物理文件。
     * </p>
     *
     * @param req 待删除的文件ID列表请求
     */
    void deleteFiles(IdsReq req);

    /**
     * 获取文件详情
     * <p>
     * 根据ID查询文件详细信息。
     * 包含文件名称、路径、大小、类型等。
     * </p>
     *
     * @param id 文件ID
     * @return 文件详情响应
     */
    FileResp getFile(Long id);

    /**
     * 分页查询文件列表
     * <p>
     * 根据条件分页查询文件列表。
     * 支持按业务类型、文件名称等条件筛选。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @param bizType 业务类型
     * @return 分页文件列表结果
     */
    PageResp<FileResp> pageFiles(FilePageReq pageReq, String bizType);

    /**
     * 下载文件
     * <p>
     * 根据ID下载文件内容。
     * 将文件内容写入响应流，供客户端下载。
     * </p>
     *
     * @param id       文件ID
     * @param response HTTP响应对象
     * @return 文件内容字节数组
     */
    byte[] downloadFile(Long id, jakarta.servlet.http.HttpServletResponse response);
}