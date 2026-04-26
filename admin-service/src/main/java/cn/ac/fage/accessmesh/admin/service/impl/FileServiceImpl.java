package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.admin.entity.SysFile;
import cn.ac.fage.accessmesh.admin.entity.table.SysFileTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysFileMapper;
import cn.ac.fage.accessmesh.admin.service.FileService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysFileTableDef.SYS_FILE;

@Service
public class FileServiceImpl implements FileService {

    @Value("${file.storage.path:${user.home}/accessmesh-files}")
    private String storagePath;

    private final SysFileMapper fileMapper;

    public FileServiceImpl(SysFileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    @Override
    @Transactional
    public Long uploadFile(MultipartFile file, String bizType) {
        if (file.isEmpty()) {
            throw new BizException(10502, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String suffix = originalName != null && originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;

        String dateDir = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        Path dirPath = Paths.get(storagePath, bizType != null ? bizType : "default", dateDir);
        try {
            Files.createDirectories(dirPath);
            Path targetPath = dirPath.resolve(fileName);
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new BizException(10503, "文件保存失败: " + e.getMessage());
        }

        String filePath = dirPath.relativize(Paths.get(storagePath, bizType != null ? bizType : "default", dateDir, fileName)).toString();
        SysFile sysFile = new SysFile();
        sysFile.setOriginalName(originalName);
        sysFile.setFileName(fileName);
        sysFile.setFilePath(filePath);
        sysFile.setFileUrl("/files/" + (bizType != null ? bizType + "/" : "") + dateDir + "/" + fileName);
        sysFile.setFileSize(file.getSize());
        sysFile.setFileType(file.getContentType());
        sysFile.setBucketName(bizType);
        sysFile.setCreatedAt(LocalDateTime.now());
        sysFile.setUpdatedAt(LocalDateTime.now());
        sysFile.setDeleteFlag(0L);
        fileMapper.insert(sysFile);
        return sysFile.getId();
    }

    @Override
    @Transactional
    public void deleteFiles(IdsReq req) {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            SysFile f = fileMapper.selectOneById(id);
            if (f == null || f.getDeleteFlag() != 0L) continue;
            try {
                Path path = Paths.get(storagePath, f.getFilePath());
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            f.setDeleteFlag(1L);
            f.setDeletedAt(now);
            fileMapper.update(f);
        }
    }

    @Override
    public FileResp getFile(Long id) {
        SysFile f = fileMapper.selectOneById(id);
        if (f == null || f.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.FILE_NOT_FOUND.getCode(), AdminErrorCode.FILE_NOT_FOUND.getMessage());
        }
        return toResp(f);
    }

    @Override
    public PaginatedResult<FileResp> pageFiles(PageReq pageReq, String bizType) {
        QueryWrapper qw = QueryWrapper.create()
            .where(SYS_FILE.DELETE_FLAG.eq(0));
        if (bizType != null) qw.and(SYS_FILE.BUCKET_NAME.eq(bizType));
        qw.orderBy(SYS_FILE.CREATED_AT.desc());

        Page<SysFile> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysFile> result = fileMapper.paginate(page, qw);

        List<FileResp> items = result.getRecords().stream()
            .map(this::toResp)
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    private FileResp toResp(SysFile f) {
        return new FileResp(
            f.getId(), f.getFileName(), f.getOriginalName(),
            f.getFileType(), f.getFileUrl(),
            f.getFileSize() != null ? f.getFileSize().toString() : null,
            f.getFileType(), f.getFilePath(), f.getCreatedAt()
        );
    }

    @Override
    public byte[] downloadFile(Long id, jakarta.servlet.http.HttpServletResponse response) {
        SysFile f = fileMapper.selectOneById(id);
        if (f == null || f.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.FILE_NOT_FOUND.getCode(), AdminErrorCode.FILE_NOT_FOUND.getMessage());
        }
        Path filePath = Paths.get(storagePath, f.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BizException(10504, "文件不存在");
        }
        try {
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + f.getOriginalName() + "\"");
            response.setContentType(f.getFileType() != null ? f.getFileType() : "application/octet-stream");
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new BizException(10505, "文件读取失败: " + e.getMessage());
        }
    }
}
