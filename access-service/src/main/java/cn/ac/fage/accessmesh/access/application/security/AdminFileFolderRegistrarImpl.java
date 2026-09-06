package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminFileFolderRegistrar;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * ADMIN_FILE 文件夹投影登记端口实现（T-ADMIN-025）。
 * <p>
 * 落位 access.application（admin 与 permission 禁止横向调用的跨域桥接层，
 * 与 {@link AdminPermissionValidatorImpl} 同款依赖反转），委托
 * {@link LocalProjectionDomainService#ensureAdminFileFolder}；事务由调用方
 * （FileServiceImpl 上传事务 / bootstrap 固定图事务）声明。
 * </p>
 * <p>
 * 并发窗口转译（评审批次补强，对齐「DB 唯一索引兜底转同码」先例）：两个事务并发首传同一
 * 新 bizType 时，后提交方在 {@code uk_resource_entity} 上撞 DuplicateKeyException（此时
 * 对手行已提交）。PG 语句错误后同事务进入 aborted 态不可继续查询，故不吞掉重查而是转译为
 * 业务错误——按对外入口所属领域（admin /file/upload）选 {@code 10502}，调用方整事务回滚
 * （登记已前置到物理落盘之前，不留孤儿文件），重试即成功（新事务 select 可见已提交行）。
 * bootstrap 预置路径直调 LocalProjectionDomainService 不经本端口：启动窗口与首传并发的
 * 撞键表现为启动失败重试，可接受。
 * </p>
 */
@Service
public class AdminFileFolderRegistrarImpl implements AdminFileFolderRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AdminFileFolderRegistrarImpl.class);

    private final LocalProjectionDomainService localProjectionDomainService;

    public AdminFileFolderRegistrarImpl(LocalProjectionDomainService localProjectionDomainService) {
        this.localProjectionDomainService = localProjectionDomainService;
    }

    @Override
    public Long ensureFolder(Long tenantId, String folderCode, String name) {
        try {
            return localProjectionDomainService.ensureAdminFileFolder(tenantId, folderCode, name);
        } catch (DuplicateKeyException e) {
            log.warn("Folder registration concurrent conflict (uk_resource_entity), retryable: "
                + "tenantId={}, folderCode={}", tenantId, folderCode);
            throw new BizException(AdminErrorCode.FILE_UPLOAD_FAILED.getCode(),
                "文件夹登记并发冲突，请重试上传: " + folderCode);
        }
    }
}
