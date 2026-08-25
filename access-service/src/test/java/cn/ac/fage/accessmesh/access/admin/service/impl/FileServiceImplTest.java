package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysFile;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysFileMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文件服务安全加固单元测试（T-ADMIN-023）。
 * <p>
 * 覆盖三类验收：① detail/page/download 补 ADMIN_FILE 类型级 VIEW 门禁（无权限 SecurityException）；
 * ② 统一路径安全函数（DB filePath 穿越/上传 bizType 路径注入均拒绝 10506）；
 * ③ 删除顺序反转（同事务先软删元数据、提交后 afterCommit 才物理清理，
 * 清理失败保留孤儿文件记 WARN、不影响已提交的软删）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock
    private SysFileMapper fileMapper;

    @Mock
    private AdminPermissionValidator permissionValidator;

    private FileServiceImpl service;

    @TempDir
    Path storageRoot;

    @BeforeEach
    void setUp() {
        service = new FileServiceImpl(fileMapper, permissionValidator);
        ReflectionTestUtils.setField(service, "storagePath", storageRoot.toString());
        ReflectionTestUtils.setField(service, "maxFileSize", 10L * 1024 * 1024);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private SysFile fileRow(long id, String filePath) {
        SysFile f = new SysFile();
        f.setId(id);
        f.setTenantId(TENANT);
        f.setOriginalName("a.txt");
        f.setFileName("uuid.txt");
        f.setFilePath(filePath);
        f.setFileSize(3L);
        f.setFileType("text/plain");
        f.setBucketName("default");
        f.setDeleteFlag(0L);
        return f;
    }

    // ===== ① VIEW 门禁（detail/page/download 类型级，对齐 upload/delete 已有校验） =====

    @Test
    @DisplayName("getFile 无 ADMIN_FILE:VIEW → SecurityException 且不触库")
    void getFileDeniedWithoutViewPermission() {
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);

        assertThatThrownBy(() -> service.getFile(1L)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fileMapper);
    }

    @Test
    @DisplayName("pageFiles 无 ADMIN_FILE:VIEW → SecurityException 且不触库")
    void pageFilesDeniedWithoutViewPermission() {
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);

        assertThatThrownBy(() -> service.pageFiles(new FilePageReq(1, 10, null, null), null))
            .isInstanceOf(SecurityException.class);
        verifyNoInteractions(fileMapper);
    }

    @Test
    @DisplayName("downloadFile 无 ADMIN_FILE:VIEW → SecurityException 且不触库")
    void downloadFileDeniedWithoutViewPermission() {
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);

        assertThatThrownBy(() -> service.downloadFile(1L, new MockHttpServletResponse()))
            .isInstanceOf(SecurityException.class);
        verifyNoInteractions(fileMapper);
    }

    @Test
    @DisplayName("getFile 有权限 → 放行并校验 VIEW 门禁调用")
    void getFileAllowedWithViewPermission() {
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "default/2026/08/25/uuid.txt"));

        assertThat(service.getFile(1L).id()).isEqualTo(1L);
        verify(permissionValidator).checkTypeLevel(ResourceTypeCode.ADMIN_FILE, AdminOperationCode.VIEW);
    }

    // ===== ② 统一路径安全函数（上传/下载/删除物理路径） =====

    @Test
    @DisplayName("downloadFile DB filePath 穿越（../evil.txt）→ 拒绝 10506 且不触盘")
    void downloadFileRejectsTraversalFilePath() {
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "../evil.txt"));

        assertThatThrownBy(() -> service.downloadFile(1L, new MockHttpServletResponse()))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.FILE_PATH_ILLEGAL.getCode()));
    }

    @Test
    @DisplayName("downloadFile 物理文件缺失 → 10501（原裸 10504 归位）")
    void downloadFileMissingPhysicalFileReportsNotFound() throws Exception {
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "default/2026/08/25/uuid.txt"));

        assertThatThrownBy(() -> service.downloadFile(1L, new MockHttpServletResponse()))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.FILE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("downloadFile 正常路径 → 返回内容并设置下载头")
    void downloadFileReturnsContent() throws Exception {
        Path target = storageRoot.resolve("default/2026/08/25/uuid.txt");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "abc");
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "default/2026/08/25/uuid.txt"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] data = service.downloadFile(1L, response);

        assertThat(data).isEqualTo("abc".getBytes());
        assertThat(response.getHeader("Content-Disposition")).contains("a.txt");
    }

    @Test
    @DisplayName("uploadFile bizType 路径注入（a/b、../x、含空格、超长）→ 拒绝 10506")
    void uploadFileRejectsIllegalBizType() {
        List<String> illegal = List.of("a/b", "../x", "a b", "a".repeat(33), "a.b", "..");
        for (String bizType : illegal) {
            MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes());
            assertThatThrownBy(() -> service.uploadFile(file, bizType))
                .as("bizType=%s 应被拒绝", bizType)
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AdminErrorCode.FILE_PATH_ILLEGAL.getCode()));
        }
        verifyNoInteractions(fileMapper);
    }

    @Test
    @DisplayName("uploadFile bizType=null/空白 → 归一 default，落盘与 DB 路径以 default/ 开头")
    void uploadFileNormalizesBlankBizTypeToDefault() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes());

        service.uploadFile(file, null);

        var captor = org.mockito.ArgumentCaptor.forClass(SysFile.class);
        verify(fileMapper).insert(captor.capture());
        SysFile saved = captor.getValue();
        assertThat(saved.getFilePath()).startsWith("default/");
        assertThat(saved.getBucketName()).isEqualTo("default");
        assertThat(Files.exists(storageRoot.resolve(saved.getFilePath()))).isTrue();
    }

    @Test
    @DisplayName("uploadFile 落库 filePath 位于存储根内（bizType/default/yyyy/MM/dd/uuid.ext）")
    void uploadFilePersistsPathInsideStorageRoot() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "abc".getBytes());

        service.uploadFile(file, "avatar");

        var captor = org.mockito.ArgumentCaptor.forClass(SysFile.class);
        verify(fileMapper).insert(captor.capture());
        SysFile saved = captor.getValue();
        assertThat(saved.getFilePath()).startsWith("avatar/").endsWith(".jpg");
        assertThat(saved.getBucketName()).isEqualTo("avatar");
        // 落盘文件可经统一路径安全函数解析回读（路径在存储根内）
        assertThat(Files.exists(storageRoot.resolve(saved.getFilePath()))).isTrue();
    }

    @Test
    @DisplayName("uploadFile 无 ADMIN_FILE:CREATE → SecurityException")
    void uploadFileDeniedWithoutCreatePermission() {
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkTypeLevel(anyString(), anyString());

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes());
        assertThatThrownBy(() -> service.uploadFile(file, "default")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fileMapper);
    }

    // ===== ③ 删除顺序反转（先软删提交、afterCommit 物理清理、失败容忍孤儿） =====

    @Test
    @DisplayName("deleteFiles 事务内先软删、不物理删；提交后 afterCommit 才物理删")
    void deleteFilesSoftDeletesFirstAndCleansAfterCommit() throws Exception {
        Path physical = storageRoot.resolve("default/2026/08/25/uuid.txt");
        Files.createDirectories(physical.getParent());
        Files.writeString(physical, "abc");
        when(fileMapper.selectValidByIds(eq(TENANT), anyList()))
            .thenReturn(List.of(fileRow(1L, "default/2026/08/25/uuid.txt")));

        TransactionSynchronizationManager.initSynchronization();
        service.deleteFiles(new IdsReq(List.of(1L)));

        // 事务内：软删已执行，物理文件仍在（文件不可回滚，必须在提交后清理）
        verify(fileMapper).softDeleteBatch(eq(TENANT), eq(List.of(1L)), any());
        assertThat(Files.exists(physical)).as("提交前不得物理删除").isTrue();

        // 模拟事务提交
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        assertThat(Files.exists(physical)).as("提交后物理清理").isFalse();
    }

    @Test
    @DisplayName("deleteFiles 物理清理失败（非空目录）→ 保留孤儿文件、不抛出、软删不受影响")
    void deleteFilesToleratesCleanupFailure() throws Exception {
        // DB filePath 指向非空目录：deleteIfExists 抛 DirectoryNotEmptyException
        Path dir = storageRoot.resolve("default/orphan-dir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("child.txt"), "x");
        when(fileMapper.selectValidByIds(eq(TENANT), anyList()))
            .thenReturn(List.of(fileRow(1L, "default/orphan-dir")));

        TransactionSynchronizationManager.initSynchronization();
        service.deleteFiles(new IdsReq(List.of(1L)));
        verify(fileMapper).softDeleteBatch(eq(TENANT), eq(List.of(1L)), any());

        assertThatCode(() -> {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        }).as("清理失败不外抛（WARN 容忍孤儿）").doesNotThrowAnyException();

        assertThat(Files.exists(dir)).as("孤儿文件保留，可人工清理").isTrue();
    }

    @Test
    @DisplayName("deleteFiles 无事务上下文 → 软删后立即物理清理")
    void deleteFilesCleansImmediatelyWithoutTransaction() throws Exception {
        Path physical = storageRoot.resolve("default/2026/08/25/uuid.txt");
        Files.createDirectories(physical.getParent());
        Files.writeString(physical, "abc");
        when(fileMapper.selectValidByIds(eq(TENANT), anyList()))
            .thenReturn(List.of(fileRow(1L, "default/2026/08/25/uuid.txt")));

        service.deleteFiles(new IdsReq(List.of(1L)));

        verify(fileMapper).softDeleteBatch(eq(TENANT), eq(List.of(1L)), any());
        assertThat(Files.exists(physical)).as("无事务时立即清理").isFalse();
    }

    @Test
    @DisplayName("deleteFiles 无 DELETE 权限 → SecurityException 且软删不执行")
    void deleteFilesDeniedWithoutDeletePermission() {
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkBatchInstanceLevel(anyString(), anyList(), anyString());

        assertThatThrownBy(() -> service.deleteFiles(new IdsReq(List.of(1L))))
            .isInstanceOf(SecurityException.class);
        verify(fileMapper, never()).softDeleteBatch(any(), anyList(), any());
    }

    @Test
    @DisplayName("deleteFiles DB filePath 穿越 → 软删正常提交，清理阶段拒绝并保留孤儿（不外抛）")
    void deleteFilesTraversalFilePathToleratedInCleanupPhase() throws Exception {
        when(fileMapper.selectValidByIds(eq(TENANT), anyList()))
            .thenReturn(List.of(fileRow(1L, "../evil.txt")));

        TransactionSynchronizationManager.initSynchronization();
        service.deleteFiles(new IdsReq(List.of(1L)));

        verify(fileMapper).softDeleteBatch(eq(TENANT), eq(List.of(1L)), any());
        assertThatCode(() -> {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        }).as("路径非法在清理阶段仅 WARN，不回滚已提交软删").doesNotThrowAnyException();
    }
}
