package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    Long uploadFile(MultipartFile file, String bizType);

    void deleteFiles(IdsReq req);

    FileResp getFile(Long id);

    PaginatedResult<FileResp> pageFiles(PageReq pageReq, String bizType);

    byte[] downloadFile(Long id, jakarta.servlet.http.HttpServletResponse response);
}
