package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.FilePageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysFile;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysFileMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminFileFolderRegistrar;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文件服务单元测试（T-ADMIN-023 安全加固 + T-ADMIN-025 文件夹级授权）。
 * <p>
 * 覆盖四类验收：① detail/download 文件夹实例级 VIEW 门禁、page 过滤语义（无 403）；
 * ② 统一路径安全函数（DB filePath 穿越/上传 bizType 路径注入均拒绝 10506）；
 * ③ 删除顺序反转（同事务先软删元数据、提交后 afterCommit 才物理清理，
 * 清理失败保留孤儿文件记 WARN、不影响已提交软删）；
 * ④ T-ADMIN-025：upload 文件夹实例级 CREATE + 惰性登记投影、page 按可见文件夹裁剪、
 * delete 门禁键 bucket 去重迁移。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock
    private SysFileMapper fileMapper;

    @Mock
    private AdminPermissionValidator permissionValidator;

    @Mock
    private AdminFileFolderRegistrar folderRegistrar;

    private FileServiceImpl service;

    @TempDir
    Path storageRoot;

    @BeforeEach
    void setUp() {
        service = new FileServiceImpl(fileMapper, permissionValidator, folderRegistrar);
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

    // ===== ① 文件夹实例级 VIEW 门禁（T-ADMIN-025：门禁键=元数据 bucket_name，须先取行） =====

    @Test
    @DisplayName("getFile 文件所属文件夹无 VIEW → SecurityException")
    void getFileDeniedWithoutFolderViewPermission() {
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "default/2026/08/25/uuid.txt"));
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkInstanceLevel(ResourceTypeCode.ADMIN_FILE, "default", AdminOperationCode.VIEW);

        assertThatThrownBy(() -> service.getFile(1L)).isInstanceOf(SecurityException.class);
        verify(fileMapper, never()).softDeleteBatch(any(), anyList(), any());
    }

    @Test
    @DisplayName("downloadFile 文件所属文件夹无 VIEW → SecurityException 且不触盘")
    void downloadFileDeniedWithoutFolderViewPermission() {
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "default/2026/08/25/uuid.txt"));
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkInstanceLevel(ResourceTypeCode.ADMIN_FILE, "default", AdminOperationCode.VIEW);

        assertThatThrownBy(() -> service.downloadFile(1L, new MockHttpServletResponse()))
            .isInstanceOf(SecurityException.class);
        verify(fileMapper, never()).selectFilesByCondition(any(), any(), anyList(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("getFile 有权限 → 放行并按文件夹实例级校验 VIEW（bucket_name 为门禁键）")
    void getFileAllowedWithFolderViewPermission() {
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(fileRow(1L, "default/2026/08/25/uuid.txt"));

        assertThat(service.getFile(1L).id()).isEqualTo(1L);
        verify(permissionValidator).checkInstanceLevel(
            ResourceTypeCode.ADMIN_FILE, "default", AdminOperationCode.VIEW);
    }

    // ===== ①' page 过滤语义（T-ADMIN-025：过滤非门禁，不 403） =====

    @Test
    @DisplayName("pageFiles 无有效文件（空全集）→ 空页且不调引擎、不查行")
    void pageFilesReturnsEmptyPageWhenNoFilesAtAll() {
        when(fileMapper.selectDistinctBucketNames(TENANT, null)).thenReturn(List.of());

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp> page =
            service.pageFiles(new FilePageReq(1, 10, null, null), null);

        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        verifyNoInteractions(permissionValidator);
        verify(fileMapper, never()).countFilesByCondition(any(), any(), any());
    }

    @Test
    @DisplayName("pageFiles scopeAll（拒绝集空）→ 可见集=全集仍走 IN 过滤（NULL bucket 行不可见，评审批次统一口径）")
    void pageFilesFiltersByUniverseWhenScopeAll() {
        when(fileMapper.selectDistinctBucketNames(TENANT, null))
            .thenReturn(List.of("avatar", "document"));
        when(permissionValidator.getDeniedResourceCodes(
            eq(ResourceTypeCode.ADMIN_FILE), anySet(), eq(AdminOperationCode.VIEW)))
            .thenReturn(Set.of());
        when(fileMapper.countFilesByCondition(TENANT, null, List.of("avatar", "document"))).thenReturn(2L);
        when(fileMapper.selectFilesByCondition(TENANT, null, List.of("avatar", "document"), 0, 10))
            .thenReturn(List.of(fileRow(1L, "avatar/2026/08/25/uuid.txt")));

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp> page =
            service.pageFiles(new FilePageReq(1, 10, null, null), null);

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.items()).hasSize(1);
        verify(fileMapper).countFilesByCondition(TENANT, null, List.of("avatar", "document"));
        verify(fileMapper).selectFilesByCondition(TENANT, null, List.of("avatar", "document"), 0, 10);
    }

    @Test
    @DisplayName("pageFiles 部分文件夹无权 → SQL 按可见文件夹集合过滤（bucket IN）")
    void pageFilesFiltersByVisibleBuckets() {
        when(fileMapper.selectDistinctBucketNames(TENANT, null))
            .thenReturn(List.of("avatar", "document", "image"));
        when(permissionValidator.getDeniedResourceCodes(
            eq(ResourceTypeCode.ADMIN_FILE), anySet(), eq(AdminOperationCode.VIEW)))
            .thenReturn(Set.of("document"));
        when(fileMapper.countFilesByCondition(TENANT, null, List.of("avatar", "image"))).thenReturn(3L);
        when(fileMapper.selectFilesByCondition(TENANT, null, List.of("avatar", "image"), 0, 10))
            .thenReturn(List.of(fileRow(1L, "avatar/2026/08/25/uuid.txt")));

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp> page =
            service.pageFiles(new FilePageReq(1, 10, null, null), null);

        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.items()).hasSize(1);
        verify(fileMapper).countFilesByCondition(TENANT, null, List.of("avatar", "image"));
        verify(fileMapper).selectFilesByCondition(TENANT, null, List.of("avatar", "image"), 0, 10);
    }

    @Test
    @DisplayName("pageFiles 全部文件夹无权 → 空页（过滤语义，不 403）且不查行")
    void pageFilesReturnsEmptyPageWhenNoVisibleFolder() {
        when(fileMapper.selectDistinctBucketNames(TENANT, null)).thenReturn(List.of("avatar"));
        when(permissionValidator.getDeniedResourceCodes(
            eq(ResourceTypeCode.ADMIN_FILE), anySet(), eq(AdminOperationCode.VIEW)))
            .thenReturn(Set.of("avatar"));

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp> page =
            service.pageFiles(new FilePageReq(1, 10, null, null), null);

        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
        verify(fileMapper, never()).countFilesByCondition(any(), any(), any());
    }

    @Test
    @DisplayName("pageFiles bizType 请求参数收窄全集（传入 distinct 查询）")
    void pageFilesNarrowsUniverseByBizTypeParam() {
        when(fileMapper.selectDistinctBucketNames(TENANT, "avatar")).thenReturn(List.of("avatar"));
        when(permissionValidator.getDeniedResourceCodes(
            eq(ResourceTypeCode.ADMIN_FILE), anySet(), eq(AdminOperationCode.VIEW)))
            .thenReturn(Set.of());
        when(fileMapper.countFilesByCondition(TENANT, "avatar", List.of("avatar"))).thenReturn(1L);
        when(fileMapper.selectFilesByCondition(TENANT, "avatar", List.of("avatar"), 0, 10))
            .thenReturn(List.of(fileRow(1L, "avatar/2026/08/25/uuid.txt")));

        PageResp<cn.ac.fage.accessmesh.access.admin.dto.resp.FileResp> page =
            service.pageFiles(new FilePageReq(1, 10, null, "avatar"), "avatar");

        assertThat(page.total()).isEqualTo(1L);
        verify(fileMapper).selectDistinctBucketNames(TENANT, "avatar");
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
    @DisplayName("uploadFile bizType 路径注入（a/b、../x、含空格、超长）→ 拒绝 10506 且不登记投影")
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
        verifyNoInteractions(folderRegistrar);
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
    @DisplayName("uploadFile 落库 filePath 位于存储根内（bizType/default/yyyy/MM/dd/uuid.ext）+ 惰性登记投影（T-ADMIN-025）")
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
        // 首次出现的 bizType 惰性登记文件夹投影（name=code），与元数据同事务；
        // 登记先于物理落盘与元数据插入（评审批次补强：登记失败不留孤儿物理文件）
        verify(folderRegistrar).ensureFolder(TENANT, "avatar", "avatar");
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(folderRegistrar, fileMapper);
        inOrder.verify(folderRegistrar).ensureFolder(TENANT, "avatar", "avatar");
        inOrder.verify(fileMapper).insert(any(SysFile.class));
    }

    @Test
    @DisplayName("uploadFile 目标文件夹无 CREATE → SecurityException 且不登记投影、不落元数据")
    void uploadFileDeniedWithoutFolderCreatePermission() {
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkInstanceLevel(anyString(), anyString(), anyString());

        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes());
        assertThatThrownBy(() -> service.uploadFile(file, "default")).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fileMapper);
        verifyNoInteractions(folderRegistrar);
    }

    @Test
    @DisplayName("uploadFile 原始文件名含控制字符（CRLF）→ 落库前剥离（防日志伪造/下载头污染）")
    void uploadFileStripsControlCharactersFromOriginalName() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "ev\r\ril.txt", "text/plain", "abc".getBytes());

        service.uploadFile(file, "default");

        var captor = org.mockito.ArgumentCaptor.forClass(SysFile.class);
        verify(fileMapper).insert(captor.capture());
        String savedName = captor.getValue().getOriginalName();
        assertThat(savedName).isEqualTo("evil.txt");
        assertThat(savedName).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("启动校验：storagePath 相对路径 → IllegalStateException fail-fast；绝对路径通过")
    void validateStorageConfigRequiresAbsolutePath() {
        ReflectionTestUtils.setField(service, "storagePath", "relative/dir");
        assertThatThrownBy(() -> service.validateStorageConfig())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("绝对路径");

        ReflectionTestUtils.setField(service, "storagePath", storageRoot.toString());
        assertThatCode(() -> service.validateStorageConfig()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("downloadFile DB originalName 含 CRLF → 响应头剥离控制字符")
    void downloadFileStripsControlCharactersInHeader() throws Exception {
        Path target = storageRoot.resolve("default/2026/08/25/uuid.txt");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "abc");
        SysFile row = fileRow(1L, "default/2026/08/25/uuid.txt");
        row.setOriginalName("po\r\nisoned.txt");
        when(fileMapper.selectValidById(TENANT, 1L)).thenReturn(row);

        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] data = service.downloadFile(1L, response);

        assertThat(data).isEqualTo("abc".getBytes());
        assertThat(response.getHeader("Content-Disposition"))
            .isEqualTo("attachment; filename=\"poisoned.txt\"");
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
    @DisplayName("deleteFiles 目标文件夹无 DELETE → SecurityException 且软删不执行（门禁键=文件夹）")
    void deleteFilesDeniedWithoutFolderDeletePermission() {
        when(fileMapper.selectValidByIds(eq(TENANT), anyList()))
            .thenReturn(List.of(fileRow(1L, "default/2026/08/25/uuid.txt")));
        doThrow(new SecurityException("denied")).when(permissionValidator)
            .checkBatchInstanceLevel(anyString(), anyList(), anyString());

        assertThatThrownBy(() -> service.deleteFiles(new IdsReq(List.of(1L))))
            .isInstanceOf(SecurityException.class);
        verify(fileMapper, never()).softDeleteBatch(any(), anyList(), any());
    }

    @Test
    @DisplayName("deleteFiles 同文件夹多文件 → 门禁按 bucket 去重单键校验（T-ADMIN-025 resourceCode 迁移）")
    void deleteFilesGatesOnDistinctFolderCodes() {
        SysFile f1 = fileRow(1L, "avatar/2026/08/25/a.txt");
        f1.setBucketName("avatar");
        SysFile f2 = fileRow(2L, "avatar/2026/08/25/b.txt");
        f2.setBucketName("avatar");
        SysFile f3 = fileRow(3L, "document/2026/08/25/c.pdf");
        f3.setBucketName("document");
        when(fileMapper.selectValidByIds(eq(TENANT), anyList())).thenReturn(List.of(f1, f2, f3));

        service.deleteFiles(new IdsReq(List.of(1L, 2L, 3L)));

        verify(permissionValidator).checkBatchInstanceLevel(
            ResourceTypeCode.ADMIN_FILE, List.of("avatar", "document"), AdminOperationCode.DELETE);
        verify(fileMapper).softDeleteBatch(eq(TENANT), eq(List.of(1L, 2L, 3L)), any());
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
