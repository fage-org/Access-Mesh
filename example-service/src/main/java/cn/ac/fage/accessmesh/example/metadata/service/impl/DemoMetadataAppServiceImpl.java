package cn.ac.fage.accessmesh.example.metadata.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.*;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import cn.ac.fage.accessmesh.example.metadata.service.DemoMetadataAppService;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoMetadataAppServiceImpl implements DemoMetadataAppService {
    private final DemoMetadataDomainService metadata;
    private final BusinessPermChecker permissions;
    private final TransactionTemplate transaction;
    public DemoMetadataAppServiceImpl(DemoMetadataDomainService metadata, BusinessPermChecker permissions,
                                      PlatformTransactionManager transactions) {
        this.metadata = metadata; this.permissions = permissions;
        this.transaction = new TransactionTemplate(transactions);
    }
    @Override
    public ReportResp create(Caller c, ReportCreateReq r) {
        require(c, DemoPermission.REPORT_CREATE, null);
        return transaction.execute(s -> report(metadata.createReport(c.tenantId(), c.userId(), r.reportCode(),
            r.name(), r.datasetCode(), r.fieldResourceTypeCode())));
    }
    @Override
    public ReportResp get(Caller c, ReportGetReq r) {
        require(c, DemoPermission.REPORT_VIEW, r.reportCode());
        return report(metadata.getReport(c.tenantId(), r.reportCode()));
    }
    @Override
    public ReportResp update(Caller c, ReportUpdateReq r) {
        require(c, DemoPermission.REPORT_UPDATE, r.reportCode());
        return transaction.execute(s -> report(metadata.updateReport(c.tenantId(), c.userId(), r.reportCode(),
            r.expectedVersion(), r.name(), r.enabled())));
    }
    @Override
    public FieldRegistrationResp register(Caller c, FieldRegisterReq r) {
        require(c, DemoPermission.REPORT_UPDATE, r.reportCode());
        return transaction.execute(s -> {
            var created = metadata.registerFields(c.tenantId(), c.userId(), r.reportCode(), r.expectedCatalogVersion(), r.fieldKeys());
            var report = metadata.getReport(c.tenantId(), r.reportCode());
            return new FieldRegistrationResp(created.stream().map(f -> field(report, f)).toList(), report.getCatalogVersion());
        });
    }
    @Override
    public PageResp<FieldResp> fields(Caller c, FieldListReq r) {
        require(c, DemoPermission.REPORT_UPDATE, r.reportCode());
        var report = metadata.getReport(c.tenantId(), r.reportCode());
        // At most nine live fields from the fixed dataset catalog; never loads business record values.
        var fields = metadata.getFields(c.tenantId(), r.reportCode());
        int page = r.pageNum() == null ? 1 : r.pageNum();
        int size = r.pageSize() == null ? 20 : r.pageSize();
        int from = (int)Math.min((long)(page - 1) * size, fields.size());
        int to = Math.min(from + size, fields.size());
        return new PageResp<>(fields.subList(from, to).stream().map(f -> field(report, f)).toList(),
            fields.size(), page, size, to < fields.size());
    }
    @Override
    public void updateField(Caller c, FieldUpdateReq r) {
        require(c, DemoPermission.REPORT_UPDATE, r.reportCode());
        transaction.executeWithoutResult(s -> metadata.updateField(c.tenantId(), c.userId(), r.reportCode(),
            r.expectedCatalogVersion(), r.fieldId(), r.enabled()));
    }
    @Override
    public void removeFields(Caller c, FieldRemoveReq r) {
        require(c, DemoPermission.REPORT_UPDATE, r.reportCode());
        transaction.executeWithoutResult(s -> metadata.removeFields(c.tenantId(), c.userId(), r.reportCode(),
            r.expectedCatalogVersion(), r.fieldIds()));
    }
    @Override
    public ScopeResp saveScope(Caller c, ScopeSaveReq r) {
        require(c, DemoPermission.POLICY_MANAGE, null);
        return transaction.execute(s -> {
            var saved = metadata.saveScope(c.tenantId(), c.userId(), r.resourceTypeCode(), r.resourceCode(),
                r.codeType(), r.name(), r.enabled(), r.expectedVersion());
            return new ScopeResp(saved.getResourceTypeCode(), saved.getResourceCode(), saved.getCodeType(),
                saved.getName(), saved.getEnabled(), saved.getVersion(), saved.getPublicationStatus());
        });
    }
    private void require(Caller c, DemoPermission permission, String code) {
        var decision = permissions.check(Long.toString(c.tenantId()), Long.toString(c.userId()), c.clientIp(),
            BusinessPermChecker.Target.of(permission.type(), code, permission.operation()));
        if (!decision.allowed()) throw new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(),
            ExampleErrorCode.PERMISSION_DENIED.getMessage());
    }
    private static ReportResp report(DemoReport r) {
        return new ReportResp(r.getReportCode(), r.getName(), "sales-records", "DEMO_REPORT", r.getFieldResourceTypeCode(),
            r.getEnabled(), r.getVersion(), r.getCatalogVersion(), r.getPublicationStatus());
    }
    private static FieldResp field(DemoReport r, DemoReportField f) {
        return new FieldResp(f.getId(), f.getFieldKey(), f.getEnabled(), r.getFieldResourceTypeCode(),
            f.getResourceCode(), "default", f.getPublicationStatus());
    }
}
