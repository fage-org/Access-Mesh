package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysFileTableDef.SYS_FILE;
import static cn.ac.fage.accessmesh.admin.entity.table.SysFileTableDef.SYS_FILE;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    /**
     * 默认最大文件大小：10MB
     */
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 默认允许的文件类型（白名单）
     */
    private static final Set<String> DEFAULT_ALLOWED_TYPES = Set.of(
        // 图片
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml",
        // 文档
        "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain", "text/csv",
        // 压缩包
        "application/zip", "application/x-rar-compressed", "application/x-7z-compressed",
        "application/x-tar", "application/gzip"
    );

    /**
     * 默认允许的文件扩展名（白名单）
     */
    private static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of(
        // 图片
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
        // 文档
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv",
        // 压缩包
        ".zip", ".rar", ".7z", ".tar", ".gz"
    );

    /**
     * 危险文件扩展名（黑名单，禁止上传）
     */
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        ".exe", ".bat", ".cmd", ".sh", ".ps1", ".vbs", ".js", ".jar",
        ".jsp", ".php", ".asp", ".aspx", ".py", ".rb", ".pl",
        ".dll", ".so", ".dylib"
    );

    /**
     * 按 bizType 配置的允许类型（可扩展）
     */
    private static final Map<String, Set<String>> BIZ_TYPE_ALLOWED_EXTENSIONS = Map.of(
        "avatar", Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp"),
        "document", Set.of(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv"),
        "image", Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg")
    );

    @Value("${file.storage.path:${user.home}/accessmesh-files}")
    private String storagePath;

    @Value("${file.max-size:${DEFAULT_MAX_FILE_SIZE}}")
    private long maxFileSize;

    private final SysFileMapper fileMapper;

    public FileServiceImpl(SysFileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadFile(MultipartFile file, String bizType) {
        // 1. 检查文件是否为空
        if (file == null || file.isEmpty()) {
            throw new BizException(AdminErrorCode.FILE_UPLOAD_FAILED.getCode(), "上传文件不能为空");
        }

        // 2. 检查文件大小
        if (file.getSize() > maxFileSize) {
            throw new BizException(AdminErrorCode.FILE_TOO_LARGE.getCode(),
                "文件大小超出限制，最大允许 " + (maxFileSize / 1024 / 1024) + "MB");
        }

        // 3. 获取并验证原始文件名
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BizException(AdminErrorCode.FILE_UPLOAD_FAILED.getCode(), "文件名不能为空");
        }

        // 4. Sanitize 文件名（移除路径遍历字符和特殊字符）
        originalName = sanitizeFileName(originalName);

        // 5. 获取并验证文件扩展名
        String extension = getFileExtension(originalName);
        if (extension == null || extension.isBlank()) {
            throw new BizException(AdminErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(), "无法识别文件类型");
        }
        String lowerExtension = extension.toLowerCase();

        // 6. 检查危险扩展名（黑名单）
        if (DANGEROUS_EXTENSIONS.contains(lowerExtension)) {
            log.warn("Blocked dangerous file upload: originalName={}, extension={}", originalName, extension);
            throw new BizException(AdminErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(),
                "禁止上传可执行文件: " + extension);
        }

        // 7. 检查扩展名白名单（根据 bizType 或默认）
        Set<String> allowedExtensions = BIZ_TYPE_ALLOWED_EXTENSIONS.getOrDefault(bizType, DEFAULT_ALLOWED_EXTENSIONS);
        if (!allowedExtensions.contains(lowerExtension)) {
            log.warn("Blocked unauthorized file type: originalName={}, extension={}, bizType={}",
                originalName, extension, bizType);
            throw new BizException(AdminErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(),
                "不允许的文件类型: " + extension + "，允许的类型: " + allowedExtensions);
        }

        // 8. 检查 Content-Type（可选，作为额外验证）
        String contentType = file.getContentType();
        if (contentType != null && !DEFAULT_ALLOWED_TYPES.contains(contentType)) {
            log.warn("Suspicious content type: originalName={}, contentType={}", originalName, contentType);
            // 不直接拒绝，但记录警告，因为有些文件类型可能不在默认列表中
        }

        // 9. 生成安全的文件名（UUID + 扩展名）
        String fileName = UUID.randomUUID().toString().replace("-", "") + lowerExtension;

        // 10. 构建存储路径
        String dateDir = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        Path dirPath = Paths.get(storagePath, bizType != null ? bizType : "default", dateDir);
        try {
            Files.createDirectories(dirPath);
            Path targetPath = dirPath.resolve(fileName);

            // 11. 确保目标路径在允许的目录内（防止路径遍历）
            if (!targetPath.normalize().startsWith(dirPath.normalize())) {
                throw new BizException(AdminErrorCode.FILE_UPLOAD_FAILED.getCode(), "非法的文件路径");
            }

            // 12. 保存文件
            file.transferTo(targetPath.toFile());
            log.info("File uploaded successfully: originalName={}, fileName={}, size={}, bizType={}",
                originalName, fileName, file.getSize(), bizType);

        } catch (IOException e) {
            log.error("Failed to save file: originalName={}, error={}", originalName, e.getMessage());
            throw new BizException(AdminErrorCode.FILE_UPLOAD_FAILED.getCode(), "文件保存失败: " + e.getMessage());
        }

        // 13. 记录文件信息到数据库
        String filePath = bizType != null ? bizType + "/" + dateDir + "/" + fileName : "default/" + dateDir + "/" + fileName;
        Long tenantId = TenantContextHolder.getTenantId();
        SysFile sysFile = new SysFile();
        sysFile.setTenantId(tenantId);
        sysFile.setOriginalName(originalName);
        sysFile.setFileName(fileName);
        sysFile.setFilePath(filePath);
        sysFile.setFileUrl("/files/" + filePath);
        sysFile.setFileSize(file.getSize());
        sysFile.setFileType(contentType);
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
        Long tenantId = TenantContextHolder.getTenantId();
        // Batch query valid files (performance fix: avoid N+1 queries for SELECT)
        List<SysFile> files = fileMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_FILE.ID.in(req.ids()))
                .and(SYS_FILE.TENANT_ID.eq(tenantId))
                .and(SYS_FILE.DELETE_FLAG.eq(0))
        );

        if (files.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Delete physical files (must remain as loop - file system operation)
        for (SysFile f : files) {
            try {
                Path path = Paths.get(storagePath, f.getFilePath());
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }

        // Batch soft delete in database (performance fix: use single SQL instead of loop)
        List<Long> validIds = files.stream().map(SysFile::getId).collect(java.util.stream.Collectors.toList());
        fileMapper.softDeleteBatch(tenantId, validIds, now);
    }

    @Override
    public FileResp getFile(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysFile f = fileMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_FILE.ID.eq(id))
                .and(SYS_FILE.TENANT_ID.eq(tenantId))
                .and(SYS_FILE.DELETE_FLAG.eq(0))
        );
        if (f == null) {
            throw new BizException(AdminErrorCode.FILE_NOT_FOUND.getCode(), AdminErrorCode.FILE_NOT_FOUND.getMessage());
        }
        return toResp(f);
    }

    @Override
    public PaginatedResult<FileResp> pageFiles(FilePageReq pageReq, String bizType) {
        Long tenantId = TenantContextHolder.getTenantId();
        QueryWrapper qw = QueryWrapper.create()
            .where(SYS_FILE.TENANT_ID.eq(tenantId))
            .and(SYS_FILE.DELETE_FLAG.eq(0));
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
        Long tenantId = TenantContextHolder.getTenantId();
        SysFile f = fileMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_FILE.ID.eq(id))
                .and(SYS_FILE.TENANT_ID.eq(tenantId))
                .and(SYS_FILE.DELETE_FLAG.eq(0))
        );
        if (f == null) {
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

    /**
     * Sanitize 文件名，移除路径遍历字符和危险字符
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return null;

        // 移除路径分隔符和危险字符
        String sanitized = fileName
            .replace("/", "")
            .replace("\\", "")
            .replace("..", "")
            .replace("~", "")
            .replace("|", "")
            .replace(":", "")
            .replace("*", "")
            .replace("?", "")
            .replace("<", "")
            .replace(">", "")
            .replace("\"", "");

        // 限制长度（保留扩展名）
        if (sanitized.length() > 200) {
            String ext = getFileExtension(sanitized);
            String nameWithoutExt = sanitized.substring(0, sanitized.lastIndexOf('.'));
            sanitized = nameWithoutExt.substring(0, 200 - (ext != null ? ext.length() : 0)) + (ext != null ? ext : "");
        }

        return sanitized;
    }

    /**
     * 获取文件扩展名（包含点号，如 ".jpg"）
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}
