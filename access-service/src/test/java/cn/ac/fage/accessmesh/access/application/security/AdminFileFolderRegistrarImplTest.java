package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * ADMIN_FILE 文件夹投影登记端口单测（T-ADMIN-025，评审批次补强）。
 * <p>
 * 固化并发撞键转译语义：两个事务并发首传同一新 bizType 时后提交方撞
 * {@code uk_resource_entity}——PG 语句错误后同事务 aborted 不可重查，端口层
 * 按「DB 唯一索引兜底转同码」先例转译为业务错误（对外入口 admin /file/upload
 * 所属领域错误码 10502），调用方整事务回滚、重试即成功。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AdminFileFolderRegistrarImplTest {

    private static final Long TENANT = 1L;

    @Mock
    private LocalProjectionDomainService localProjectionDomainService;

    private AdminFileFolderRegistrarImpl registrar;

    @BeforeEach
    void setUp() {
        registrar = new AdminFileFolderRegistrarImpl(localProjectionDomainService);
    }

    @Test
    @DisplayName("正常路径：委托 ensureAdminFileFolder 并透传返回 id")
    void delegatesToProjectionService() {
        when(localProjectionDomainService.ensureAdminFileFolder(TENANT, "avatar", "头像"))
            .thenReturn(42L);

        assertThat(registrar.ensureFolder(TENANT, "avatar", "头像")).isEqualTo(42L);
    }

    @Test
    @DisplayName("并发首传撞 uk_resource_entity → 转译 10502 业务错误（可重试），非 500 裸传播")
    void translatesUniqueViolationToRetryableBizError() {
        when(localProjectionDomainService.ensureAdminFileFolder(TENANT, "brandnew", "brandnew"))
            .thenThrow(new DuplicateKeyException("uk_resource_entity concurrent insert"));

        assertThatThrownBy(() -> registrar.ensureFolder(TENANT, "brandnew", "brandnew"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.FILE_UPLOAD_FAILED.getCode()))
            .hasMessageContaining("并发冲突")
            .hasMessageContaining("brandnew");
    }
}
