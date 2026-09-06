package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysFile;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysFileMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.admin.service.FileService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 文件管理服务实现类
 * <p>
 * 提供文件的上传、下载、删除、分页查询等功能。
 * 支持多业务类型（bizType）的文件分类存储，如头像、文档、图片等。
 * 实现了完整的文件安全校验机制：
 * - 全接口权限门禁（upload=CREATE、detail/page/download=VIEW、delete=DELETE，ADMIN_FILE）
 * - 文件大小限制（默认10MB）
 * - 文件类型白名单（按bizType配置）
 * - 文件扩展名白名单和黑名单
 * - Content-Type校验
 * - bizType格式白名单（仅字母/数字/下划线/连字符，防路径注入）
 * - 文件名安全化处理（移除路径遍历字符）
 * - UUID随机文件名防止路径泄露
 * - 统一路径安全函数（{@link #securePath}，规范化后必须位于存储根目录内，
 *   应用于上传目录构造、上传目标文件、下载、删除四条物理路径；filePath 虽源于 DB 仍做纵深防御）
 * 使用日期目录结构存储文件（yyyy/MM/dd），便于管理和归档。
 * </p>
 * <p>
 * <b>存储约束（T-ADMIN-023）</b>：文件存储在本地磁盘（默认 ${user.home}/accessmesh-files），
 * <b>仅支持单实例部署</b>——多实例部署下本地盘不可共享为已知限制，未来多实例/对象存储另立任务。
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

    /**
     * bizType 格式白名单：仅字母/数字/下划线/连字符，长度 1-32。
     * <p>
     * bizType 是存储路径第一段，禁止 `/`、`..` 等路径注入字符；不限定具体取值，
     * 不排斥未来新增业务类型。
     * </p>
     */
    private static final Pattern BIZ_TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    /**
     * 存储根目录（本地磁盘）。
     * <p>
     * 单实例约束：多实例部署下本地盘不可共享为已知限制（T-ADMIN-023 登记）。
     * </p>
     */
    @Value("${file.storage.path:${user.home}/accessmesh-files}")
    private String storagePath;

    @Value("${file.max-size:" + DEFAULT_MAX_FILE_SIZE + "}")
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
     * 启动校验存储根必须为绝对路径。
     * <p>
     * securePath 的 containment 在相对配置下仍成立（相对进程 CWD），但落盘位置随启动目录
     * 漂移不可预期；fail-fast 优于运行期不可预期行为。
     * </p>
     *
     * @throws IllegalStateException file.storage.path 非绝对路径
     */
    @PostConstruct
    void validateStorageConfig() {
        if (!Paths.get(storagePath).isAbsolute()) {
            throw new IllegalStateException(
                "file.storage.path 必须为绝对路径，当前配置: " + storagePath);
        }
    }

    /**
     * 上传文件
     * <p>
     * 执行完整的文件安全校验流程：
     * 1. 检查文件是否为空
     * 2. 检查文件大小是否超限
     * 3. 安全化文件名（移除路径遍历字符）
     * 4. bizType 归一化 + 格式白名单校验
     * 5. 获取并验证文件扩展名
     * 6. 检查危险扩展名黑名单
     * 7. 检查扩展名白名单（按bizType）
     * 8. 检查Content-Type
     * 9. 生成UUID随机文件名
     * 10. 构建日期目录结构存储路径（统一路径安全函数防穿越）
     * 11. 保存物理文件
     * 12. 记录文件信息到数据库
     * </p>
     *
     * @param file 上传的文件
     * @param bizType 业务类型（如avatar、document、image），空白按 default 处理
     * @return 文件记录ID
     * @throws BizException 文件为空、文件超限、文件类型不允许、文件保存失败等
     * @throws SecurityException 无 ADMIN_FILE:CREATE 权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "FILE_UPLOAD", targetType = "sys_file",
        targetId = "#result", summary = "'upload file'")
    public Long uploadFile(MultipartFile file, String bizType) {
        // 权限检查 — FILE 类型级 CREATE
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.CREATE);

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

        // 5. bizType 归一化 + 格式白名单（T-ADMIN-023：bizType 是存储路径第一段，禁止路径注入字符）
        String normalizedBizType = normalizeBizType(bizType);

        // 6. 获取并验证文件扩展名
        String extension = getFileExtension(originalName);
        if (extension == null || extension.isBlank()) {
            throw new BizException(AdminErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(), "无法识别文件类型");
        }
        String lowerExtension = extension.toLowerCase();

        // 7. 检查危险扩展名（黑名单）
        if (DANGEROUS_EXTENSIONS.contains(lowerExtension)) {
            log.warn("Blocked dangerous file upload: originalName={}, extension={}", originalName, extension);
            throw new BizException(AdminErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(),
                "禁止上传可执行文件: " + extension);
        }

        // 8. 检查扩展名白名单（根据 bizType 或默认）
        Set<String> allowedExtensions = BIZ_TYPE_ALLOWED_EXTENSIONS.getOrDefault(normalizedBizType, DEFAULT_ALLOWED_EXTENSIONS);
        if (!allowedExtensions.contains(lowerExtension)) {
            log.warn("Blocked unauthorized file type: originalName={}, extension={}, bizType={}",
                originalName, extension, normalizedBizType);
            throw new BizException(AdminErrorCode.FILE_TYPE_NOT_ALLOWED.getCode(),
                "不允许的文件类型: " + extension + "，允许的类型: " + allowedExtensions);
        }

        // 9. 检查 Content-Type（可选，作为额外验证）
        String contentType = file.getContentType();
        if (contentType != null && !DEFAULT_ALLOWED_TYPES.contains(contentType)) {
            log.warn("Suspicious content type: originalName={}, contentType={}", originalName, contentType);
            // 不直接拒绝，但记录警告，因为有些文件类型可能不在默认列表中
        }

        // 10. 生成安全的文件名（UUID + 扩展名）
        String fileName = UUID.randomUUID().toString().replace("-", "") + lowerExtension;

        // 11. 构建存储路径（统一路径安全函数：目录与目标文件都必须位于存储根目录内）
        String dateDir = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        try {
            Path dirPath = securePath(normalizedBizType, dateDir);
            Files.createDirectories(dirPath);
            Path targetPath = securePath(normalizedBizType, dateDir, fileName);

            // 12. 保存文件
            file.transferTo(targetPath.toFile());
            log.info("File uploaded successfully: originalName={}, fileName={}, size={}, bizType={}",
                originalName, fileName, file.getSize(), normalizedBizType);

        } catch (IOException e) {
            log.error("Failed to save file: originalName={}, error={}", originalName, e.getMessage());
            throw new BizException(AdminErrorCode.FILE_UPLOAD_FAILED.getCode(), "文件保存失败: " + e.getMessage());
        }

        // 13. 记录文件信息到数据库
        String filePath = normalizedBizType + "/" + dateDir + "/" + fileName;
        Long tenantId = TenantContextHolder.getTenantId();
        SysFile sysFile = new SysFile();
        sysFile.setTenantId(tenantId);
        sysFile.setOriginalName(originalName);
        sysFile.setFileName(fileName);
        sysFile.setFilePath(filePath);
        sysFile.setFileUrl("/files/" + filePath);
        sysFile.setFileSize(file.getSize());
        sysFile.setFileType(contentType);
        sysFile.setBucketName(normalizedBizType);
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
     * 删除顺序（T-ADMIN-023 反转）：先在同一事务内提交元数据软删除，
     * 事务提交成功后再经事务同步（afterCommit）物理清理文件——物理文件不可回滚，
     * 必须保证元数据先落地；物理清理失败仅记 WARN 保留孤儿文件（孤儿文件优于丢失有效文件），
     * 不因文件失败回滚已提交的软删。无事务上下文时（理论不应发生）立即清理。
     * </p>
     *
     * @param req ID集合请求，包含待删除的文件ID列表
     * @throws SecurityException 任一文件无 ADMIN_FILE:DELETE 权限
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "FILE_DELETE", targetType = "sys_file",
        targetId = "", summary = "'batch delete files'")
    public void deleteFiles(IdsReq req) {
        // 权限检查 — FILE 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(ResourceTypeCode.ADMIN_FILE, resourceCodes, AdminOperationCode.DELETE);

        Long tenantId = TenantContextHolder.getTenantId();
        // 批量查询有效文件
        List<SysFile> files = fileMapper.selectValidByIds(tenantId, req.ids());

        if (files.isEmpty()) {
            return;
        }

        // 1. 先同事务提交元数据软删除（回滚安全：文件尚未物理删除）
        LocalDateTime now = LocalDateTime.now();
        List<Long> validIds = files.stream().map(SysFile::getId).collect(Collectors.toList());
        fileMapper.softDeleteBatch(tenantId, validIds, now);

        // 2. 事务提交成功后再物理清理；失败记 WARN 保留孤儿文件，不影响已提交的软删
        List<String> filePaths = files.stream().map(SysFile::getFilePath).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupPhysicalFiles(filePaths);
                }
            });
        } else {
            cleanupPhysicalFiles(filePaths);
        }
    }

    /**
     * 物理清理文件（事务提交后尽力而为）
     * <p>
     * 单个文件清理失败（IO 异常或路径非法）仅记 WARN 保留孤儿文件，
     * 不抛出、不中断后续文件清理——孤儿文件可人工清理，优于丢失有效文件或回滚已提交的软删。
     * </p>
     *
     * @param filePaths DB 中的相对存储路径列表
     */
    private void cleanupPhysicalFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                Path path = securePath(filePath);
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("Physical file cleanup failed, orphan retained: filePath={}, error={}",
                    filePath, e.getMessage());
            }
        }
    }

    /**
     * 获取文件详情
     * <p>
     * 类型级 VIEW 门禁后根据文件ID查询文件完整信息，包含文件名、URL、大小等。
     * </p>
     *
     * @param id 文件ID
     * @return 文件详情响应
     * @throws SecurityException 无 ADMIN_FILE:VIEW 权限
     * @throws BizException 文件不存在
     */
    @Override
    public FileResp getFile(Long id) {
        // 权限检查 — FILE 类型级 VIEW（T-ADMIN-023）
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);

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
     * 类型级 VIEW 门禁后分页查询，支持按业务类型过滤，按创建时间倒序排列。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @param bizType 业务类型过滤条件，可选
     * @return 分页文件列表结果
     * @throws SecurityException 无 ADMIN_FILE:VIEW 权限
     */
    @Override
    public PageResp<FileResp> pageFiles(FilePageReq pageReq, String bizType) {
        // 权限检查 — FILE 类型级 VIEW（T-ADMIN-023）
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);

        Long tenantId = TenantContextHolder.getTenantId();

        Page<SysFile> result = fileMapper.paginateFiles(Page.of(pageReq.pageNum(), pageReq.pageSize()), tenantId, bizType);

        List<FileResp> items = result.getRecords().stream()
            .map(this::toResp)
            .collect(Collectors.toList());

        return new PageResp<>(items, result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), result.hasNext());
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
     * 类型级 VIEW 门禁后根据文件ID读取物理文件并返回内容。
     * 物理路径经统一路径安全函数校验（filePath 虽源于 DB 仍做纵深防御）。
     * 设置HTTP响应头Content-Disposition和Content-Type。
     * </p>
     *
     * @param id 文件ID
     * @param response HTTP响应对象，用于设置下载头
     * @return 文件内容字节数组
     * @throws SecurityException 无 ADMIN_FILE:VIEW 权限
     * @throws BizException 文件不存在、路径非法、物理文件不存在、文件读取失败
     */
    @Override
    public byte[] downloadFile(Long id, jakarta.servlet.http.HttpServletResponse response) {
        // 权限检查 — FILE 类型级 VIEW（T-ADMIN-023）
        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);

        Long tenantId = TenantContextHolder.getTenantId();
        SysFile f = fileMapper.selectValidById(tenantId, id);
        if (f == null) {
            throw new BizException(AdminErrorCode.FILE_NOT_FOUND.getCode(), AdminErrorCode.FILE_NOT_FOUND.getMessage());
        }
        Path filePath = securePath(f.getFilePath());
        if (!Files.exists(filePath)) {
            throw new BizException(AdminErrorCode.FILE_NOT_FOUND.getCode(), "物理文件不存在");
        }
        try {
            // originalName 源于 DB（可能早于控制字符剥离落库），响应头拼接前防御性剥离
            // 控制字符，阻断 CRLF 类 header 污染
            String safeOriginalName = f.getOriginalName() == null ? "" : f.getOriginalName();
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + safeOriginalName.replaceAll("\\p{Cntrl}", "") + "\"");
            response.setContentType(f.getFileType() != null ? f.getFileType() : "application/octet-stream");
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new BizException(AdminErrorCode.FILE_READ_FAILED.getCode(),
                AdminErrorCode.FILE_READ_FAILED.getMessage() + ": " + e.getMessage());
        }
    }

    /**
     * 统一路径安全函数（T-ADMIN-023）
     * <p>
     * 将存储根目录与相对路径段拼接并规范化，规范化结果必须仍位于存储根目录内，
     * 否则拒绝（路径穿越纵深防御）。应用于上传目录构造、上传目标文件、下载、删除
     * 四条物理路径；filePath 虽源于 DB 仍做校验，防止 DB 数据被污染后穿越读写删。
     * </p>
     *
     * @param segments 存储根目录下的相对路径段（多段或单段斜杠分隔）
     * @return 规范化后的绝对路径（保证位于存储根目录内）
     * @throws BizException FILE_PATH_ILLEGAL(10506) 规范化后越出存储根目录
     */
    private Path securePath(String... segments) {
        Path root = Paths.get(storagePath).normalize();
        Path resolved = root;
        for (String segment : segments) {
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(root)) {
            log.warn("Blocked illegal file path outside storage root: segments={}", (Object) segments);
            throw new BizException(AdminErrorCode.FILE_PATH_ILLEGAL.getCode(),
                AdminErrorCode.FILE_PATH_ILLEGAL.getMessage());
        }
        return resolved;
    }

    /**
     * bizType 归一化 + 格式白名单校验
     * <p>
     * null/空白归一为 "default"（与 Controller defaultValue 语义一致）；
     * 非空必须匹配 ^[A-Za-z0-9_-]{1,32}$——bizType 是存储路径第一段，
     * 拒绝路径注入字符。
     * </p>
     *
     * @param bizType 原始业务类型参数
     * @return 归一化后的业务类型
     * @throws BizException FILE_PATH_ILLEGAL(10506) 格式不合法
     */
    private String normalizeBizType(String bizType) {
        String normalized = (bizType == null || bizType.isBlank()) ? "default" : bizType;
        if (!BIZ_TYPE_PATTERN.matcher(normalized).matches()) {
            log.warn("Blocked illegal bizType: bizType={}", bizType);
            throw new BizException(AdminErrorCode.FILE_PATH_ILLEGAL.getCode(),
                "非法的业务类型: " + bizType + "（仅允许字母/数字/下划线/连字符，长度1-32）");
        }
        return normalized;
    }

    /**
     * 安全化文件名
     * <p>
     * 移除路径遍历字符、危险字符与控制字符（CRLF 可致日志伪造与下载头污染，
     * 写入侧剥离），限制文件名长度（最大200字符，保留扩展名）。
     * </p>
     *
     * @param fileName 原始文件名
     * @return 安全化后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return null;

        // 移除控制字符（CRLF 等）、路径分隔符和危险字符
        String sanitized = fileName
            .replaceAll("\\p{Cntrl}", "")
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

        // 限制长度（保留扩展名；无扩展名直接截断，防 lastIndexOf('.')=-1 越界）
        if (sanitized.length() > 200) {
            int dot = sanitized.lastIndexOf('.');
            if (dot >= 0) {
                String ext = sanitized.substring(dot);
                sanitized = sanitized.substring(0, Math.max(0, 200 - ext.length())) + ext;
            } else {
                sanitized = sanitized.substring(0, 200);
            }
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