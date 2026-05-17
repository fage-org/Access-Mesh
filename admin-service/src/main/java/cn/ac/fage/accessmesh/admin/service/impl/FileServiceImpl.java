package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.admin.entity.SysFile;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysFileMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.FileService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * 文件管理服务实现类
 * <p>
 * 提供文件的上传、下载、删除、分页查询等功能。
 * 支持多业务类型（bizType）的文件分类存储，如头像、文档、图片等。
 * 实现了完整的文件安全校验机制：
 * - 文件大小限制（默认10MB）
 * - 文件类型白名单（按bizType配置）
 * - 文件扩展名白名单和黑名单
 * - Content-Type校验
 * - 文件名安全化处理（移除路径遍历字符）
 * - UUID随机文件名防止路径泄露
 * 使用日期目录结构存储文件（yyyy/MM/dd），便于管理和归档。
 * </p>
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    /**
     * 默认最大文件大小：10MB
     */
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 默认允许的文件类型（白名单）
     * <p>
     * 包含图片、文档、压缩包等常见类型。
     * </p>
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
     * <p>
     * 包含图片、文档、压缩包等常见扩展名。
     * </p>
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
     * <p>
     * 包含可执行文件、脚本文件、动态库等危险类型。
     * </p>
     */
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        ".exe", ".bat", ".cmd", ".sh", ".ps1", ".vbs", ".js", ".jar",
        ".jsp", ".php", ".asp", ".aspx", ".py", ".rb", ".pl",
        ".dll", ".so", ".dylib"
    );

    /**
     * 按业务类型配置的允许扩展名
     * <p>
     * 不同业务场景允许不同的文件类型，如头像只允许图片。
     * </p>
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
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param fileMapper 文件数据访问Mapper
     * @param permissionValidator 权限校验器，校验文件操作权限
     */
    public FileServiceImpl(SysFileMapper fileMapper, AdminPermissionValidator permissionValidator) {
        this.fileMapper = fileMapper;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 上传文件
     * <p>
     * 执行完整的文件安全校验流程：
     * 1. 检查文件是否为空
     * 2. 检查文件大小是否超限
     * 3. 安全化文件名（移除路径遍历字符）
     * 4. 获取并验证文件扩展名
     * 5. 检查危险扩展名黑名单
     * 6. 检查扩展名白名单（按bizType）
     * 7. 检查Content-Type
     * 8. 生成UUID随机文件名
     * 9. 构建日期目录结构存储路径
     * 10. 确保目标路径在允许目录内（防路径遍历）
     * 11. 保存物理文件
     * 12. 记录文件信息到数据库
     * </p>
     *
     * @param file 上传的文件
     * @param bizType 业务类型（如avatar、document、image）
     * @return 文件记录ID
     * @throws BizException 文件为空、文件超限、文件类型不允许、文件保存失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadFile(MultipartFile file, String bizType) {
        // 权限检查 — FILE 类型级 CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.FILE, AdminOperationCode.CREATE);

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

    /**
     * 批量删除文件
     * <p>
     * 执行批量实例级权限校验后删除文件。
     * 先尝试删除所有物理文件，失败仅记录（不抛异常）。
     * 如果有任何物理文件删除失败，抛异常但不执行数据库软删除。
     * 只有全部物理文件删除成功，才执行数据库软删除。
     * </p>
     *
     * @param req ID集合请求，包含待删除的文件ID列表
     * @throws BizException 文件删除失败
     */
    @Override
    @Transactional
    public void deleteFiles(IdsReq req) {
        // 权限检查 — FILE 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.FILE, resourceCodes, AdminOperationCode.DELETE);

        Long tenantId = TenantContextHolder.getTenantId();
        // 批量查询有效文件
        List<SysFile> files = fileMapper.selectValidByIds(tenantId, req.ids());

        if (files.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 先尝试删除所有物理文件，失败仅记录（不抛异常）
        List<String> failedFiles = new ArrayList<>();
        for (SysFile f : files) {
            try {
                Path path = Paths.get(storagePath, f.getFilePath());
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.error("Failed to delete physical file: filePath={}, error={}", f.getFilePath(), e.getMessage());
                failedFiles.add(f.getFilePath());
            }
        }

        // 如果有任何物理文件删除失败，抛异常但不执行数据库软删除
        if (!failedFiles.isEmpty()) {
            throw new BizException(AdminErrorCode.FILE_DELETE_FAILED.getCode(),
                "文件删除失败: " + String.join(", ", failedFiles));
        }

        // 只有全部物理文件删除成功，才执行数据库软删除
        List<Long> validIds = files.stream().map(SysFile::getId).collect(Collectors.toList());
        fileMapper.softDeleteBatch(tenantId, validIds, now);
    }

    /**
     * 获取文件详情
     * <p>
     * 根据文件ID查询文件完整信息，包含文件名、URL、大小等。
     * </p>
     *
     * @param id 文件ID
     * @return 文件详情响应
     * @throws BizException 文件不存在
     */
    @Override
    public FileResp getFile(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysFile f = fileMapper.selectValidById(tenantId, id);
        if (f == null) {
            throw new BizException(AdminErrorCode.FILE_NOT_FOUND.getCode(), AdminErrorCode.FILE_NOT_FOUND.getMessage());
        }
        return toResp(f);
    }

    /**
     * 分页查询文件列表
     * <p>
     * 支持按业务类型过滤，按创建时间倒序排列。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @param bizType 业务类型过滤条件，可选
     * @return 分页文件列表结果
     */
    @Override
    public PaginatedResult<FileResp> pageFiles(FilePageReq pageReq, String bizType) {
        Long tenantId = TenantContextHolder.getTenantId();

        Page<SysFile> result = fileMapper.paginateFiles(Page.of(pageReq.pageNum(), pageReq.pageSize()), tenantId, bizType);

        List<FileResp> items = result.getRecords().stream()
            .map(this::toResp)
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 将文件实体转换为响应对象
     * <p>
     * 转换文件实体为API响应格式。
     * </p>
     *
     * @param f 文件实体
     * @return 文件响应对象
     */
    private FileResp toResp(SysFile f) {
        return new FileResp(
            f.getId(), f.getFileName(), f.getOriginalName(),
            f.getFileType(), f.getFileUrl(),
            f.getFileSize() != null ? f.getFileSize().toString() : null,
            f.getFileType(), f.getFilePath(), f.getCreatedAt()
        );
    }

    /**
     * 下载文件
     * <p>
     * 根据文件ID读取物理文件并返回内容。
     * 设置HTTP响应头Content-Disposition和Content-Type。
     * </p>
     *
     * @param id 文件ID
     * @param response HTTP响应对象，用于设置下载头
     * @return 文件内容字节数组
     * @throws BizException 文件不存在、物理文件不存在、文件读取失败
     */
    @Override
    public byte[] downloadFile(Long id, jakarta.servlet.http.HttpServletResponse response) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysFile f = fileMapper.selectValidById(tenantId, id);
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
     * 安全化文件名
     * <p>
     * 移除路径遍历字符和危险字符，防止路径遍历攻击。
     * 限制文件名长度（最大200字符），保留扩展名。
     * </p>
     *
     * @param fileName 原始文件名
     * @return 安全化后的文件名
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
     * 获取文件扩展名
     * <p>
     * 从文件名中提取扩展名，包含点号（如".jpg"）。
     * </p>
     *
     * @param fileName 文件名
     * @return 文件扩展名（包含点号），无扩展名返回null
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}