package cn.ac.fage.accessmesh.example.metadata.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoPublicationDomainService;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoPublicationDomainService.Change;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoScope;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoDatasetMapper;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoReportMapper;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoScopeMapper;
import cn.ac.fage.accessmesh.example.metadata.mapper.DemoReportFieldMapper;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DemoMetadataDomainServiceImpl implements DemoMetadataDomainService {
    private final DemoDatasetMapper datasets;
    private final DemoReportMapper reports;
    private final DemoScopeMapper scopes;
    private final DemoPublicationDomainService publications;
    private final DemoReportFieldMapper fields;
    private static final Set<String> FIELD_KEYS = Set.of("recordCode", "ownerUserId", "teamCode", "departmentCode",
        "regionCode", "saleDate", "productName", "amount", "remark");

    public DemoMetadataDomainServiceImpl(DemoDatasetMapper datasets, DemoReportMapper reports, DemoScopeMapper scopes,
                                         DemoPublicationDomainService publications, DemoReportFieldMapper fields) {
        this.datasets = datasets;
        this.reports = reports;
        this.scopes = scopes;
        this.publications = publications;
        this.fields = fields;
    }

    @Override
    public DemoReport createReport(long tenant, long actor, String code, String name, String dataset, String fieldType) {
        if (!"sales-records".equals(dataset)) throw invalid();
        long datasetId = lock(tenant, actor);
        validatePurpose(tenant, fieldType, true);
        if (reports.selectByCode(tenant, code) != null) throw conflict();
        var report = new DemoReport();
        report.setTenantId(tenant); report.setCreatedBy(actor); report.setDatasetId(datasetId);
        report.setReportCode(code); report.setName(name); report.setFieldResourceTypeCode(fieldType);
        reports.insert(report);
        publications.record(tenant, actor, java.util.List.of(new Change("DEMO_REPORT", code, "default", 1,
            "UPSERT", name, true, "demo_report", report.getId().toString())));
        return reports.selectByCode(tenant, code);
    }

    @Override
    public DemoScope saveScope(long tenant, long actor, String type, String code, String codeType,
                               String name, boolean enabled, long expectedVersion) {
        lock(tenant, actor);
        validatePurpose(tenant, type, false);
        String normalized = codeType == null || codeType.isBlank() ? "default" : codeType.trim();
        var scope = scopes.find(tenant, type, code, normalized);
        if (scope == null) {
            if (expectedVersion != 0) throw conflict();
            scope = new DemoScope();
            scope.setTenantId(tenant); scope.setResourceTypeCode(type); scope.setResourceCode(code);
            scope.setCodeType(normalized); scope.setName(name); scope.setEnabled(enabled); scope.setCreatedBy(actor);
            scopes.insert(scope);
        } else {
            if (scope.getVersion() != expectedVersion) throw conflict();
            scope.setName(name); scope.setEnabled(enabled); scope.setCreatedBy(actor);
            if (scopes.update(scope) != 1) throw conflict();
        }
        var saved = scopes.find(tenant, type, code, normalized);
        publications.record(tenant, actor, java.util.List.of(new Change(type, code, normalized, saved.getVersion(),
            "UPSERT", name, enabled, "demo_scope", saved.getId().toString())));
        return saved;
    }

    private long lock(long tenant, long actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Metadata writes require a transaction");
        }
        datasets.ensure(tenant, actor);
        return datasets.lock(tenant);
    }

    @Override
    public DemoReport getReport(long tenant, String code) {
        var report = reports.selectByCode(tenant, code);
        if (report == null) throw error(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE);
        return report;
    }

    @Override
    public DemoReport updateReport(long tenant, long actor, String code, long version, String name, boolean enabled) {
        lock(tenant, actor);
        var report = getReport(tenant, code);
        if (report.getVersion() != version || reports.update(tenant, report.getId(), version, name, enabled, actor) != 1) {
            throw conflict();
        }
        publications.record(tenant, actor, List.of(new Change("DEMO_REPORT", code, "default", version + 1,
            "UPSERT", name, enabled, "demo_report", report.getId().toString())));
        return getReport(tenant, code);
    }

    @Override
    public List<DemoReportField> getFields(long tenant, String code) {
        return fields.selectByReport(tenant, getReport(tenant, code).getId());
    }

    private DemoReport catalog(long tenant, long actor, String code, long version) {
        lock(tenant, actor);
        var report = getReport(tenant, code);
        if (report.getCatalogVersion() != version) throw conflict();
        return report;
    }

    @Override
    public List<DemoReportField> registerFields(long tenant, long actor, String code, long version, List<String> keys) {
        var report = catalog(tenant, actor, code, version);
        if (keys == null || keys.isEmpty() || keys.size() > 100 || Set.copyOf(keys).size() != keys.size()
                || !FIELD_KEYS.containsAll(keys)) throw invalid();
        var existing = fields.selectByReport(tenant, report.getId());
        if (existing.stream().anyMatch(f -> keys.contains(f.getFieldKey()))) throw conflict();
        fields.insertBatch(tenant, report.getId(), keys, actor);
        var created = fields.selectByReport(tenant, report.getId()).stream().filter(f -> keys.contains(f.getFieldKey())).toList();
        publications.record(tenant, actor, created.stream().map(f -> fieldChange(report, f, "UPSERT", f.getVersion())).toList());
        advanceCatalog(tenant, actor, report, version);
        return created;
    }

    @Override
    public void updateField(long tenant, long actor, String code, long version, long fieldId, boolean enabled) {
        var report = catalog(tenant, actor, code, version);
        var field = fields.selectByReport(tenant, report.getId()).stream().filter(f -> f.getId() == fieldId).findFirst()
            .orElseThrow(() -> error(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE));
        if (fields.setEnabled(tenant, report.getId(), fieldId, enabled, actor) != 1) throw conflict();
        field.setEnabled(enabled);
        publications.record(tenant, actor, List.of(fieldChange(report, field, "UPSERT", field.getVersion() + 1)));
        advanceCatalog(tenant, actor, report, version);
    }

    @Override
    public void removeFields(long tenant, long actor, String code, long version, List<Long> ids) {
        var report = catalog(tenant, actor, code, version);
        if (ids == null || ids.isEmpty() || ids.size() > 100 || Set.copyOf(ids).size() != ids.size()) throw invalid();
        var targets = fields.selectByReport(tenant, report.getId()).stream().filter(f -> ids.contains(f.getId())).toList();
        if (targets.size() != ids.size()) throw error(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE);
        if (fields.remove(tenant, report.getId(), ids, actor) != ids.size()) throw conflict();
        publications.record(tenant, actor, targets.stream().map(f -> fieldChange(report, f, "DELETE", f.getVersion() + 1)).toList());
        advanceCatalog(tenant, actor, report, version);
    }

    private Change fieldChange(DemoReport report, DemoReportField field, String operation, long version) {
        return new Change(report.getFieldResourceTypeCode(), field.getResourceCode(), "default", version,
            operation, field.getFieldKey(), field.getEnabled(), "demo_report_field", field.getId().toString());
    }

    private void advanceCatalog(long tenant, long actor, DemoReport report, long version) {
        if (reports.advanceCatalog(tenant, report.getId(), version, actor) != 1) throw conflict();
    }

    private void validatePurpose(long tenant, String type, boolean field) {
        if (type == null || type.isBlank() || java.util.Set.of("DEMO_REPORT", "DEMO_POLICY", "DEMO_DATASET", "DEMO_TASK").contains(type)) {
            throw invalid();
        }
        if (field ? datasets.usedForScope(tenant, type) : datasets.usedForField(tenant, type)) {
            throw invalid();
        }
    }

    private static BizException invalid() { return error(ExampleErrorCode.DEMO_POLICY_INVALID); }
    private static BizException conflict() { return error(ExampleErrorCode.DEMO_VERSION_CONFLICT); }
    private static BizException error(ExampleErrorCode code) { return new BizException(code.getCode(), code.getMessage()); }
}
