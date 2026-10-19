package cn.ac.fage.accessmesh.example.data.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.data.domain.*;
import cn.ac.fage.accessmesh.example.data.entity.DemoImportBatch;
import cn.ac.fage.accessmesh.example.data.entity.DemoRecord;
import cn.ac.fage.accessmesh.example.data.csv.DemoCsvReader.Parsed;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.example.data.dto.ImportDtos.ImportedField;
import cn.ac.fage.accessmesh.example.data.mapper.DemoImportMapper;
import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DemoImportDomainServiceImpl implements DemoImportDomainService {
    private final DemoImportMapper imports;
    private final DemoDataDomainService data;
    private final DemoMetadataDomainService metadata;
    private final JsonMapper json;
    public DemoImportDomainServiceImpl(DemoImportMapper imports,DemoDataDomainService data,DemoMetadataDomainService metadata,JsonMapper json) {
        this.imports=imports; this.data=data; this.metadata=metadata; this.json=json;
    }
    @Override public DemoImportBatch owned(long tenant,long actor,UUID key) {
        var batch=imports.find(tenant,key);
        if(batch==null || !Objects.equals(batch.getCreatedBy(),actor)) throw error(ExampleErrorCode.PERMISSION_DENIED);
        return batch;
    }
    @Override public Set<DemoField> originalFields(DemoImportBatch batch) {
        var report=metadata.getReportById(batch.getTenantId(),batch.getReportId());
        var current=metadata.getFields(batch.getTenantId(),report.getReportCode()).stream().collect(Collectors.toMap(f -> f.getId(),f -> f));
        Set<DemoField> fields=EnumSet.noneOf(DemoField.class);
        for(var stored:json.readValue(batch.getImportedFields(),ImportedField[].class)) {
            var field=current.get(stored.fieldId());
            if(field==null || !field.getEnabled() || !field.getFieldKey().equals(stored.fieldKey())) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            fields.add(DemoField.require(field.getFieldKey()));
        }
        if(fields.isEmpty()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
        return fields;
    }
    @Override public DemoImportBatch apply(DataSqlPlan plan,long actor,UUID key,String digest,Parsed input) {
        guard(plan,actor);
        var report=metadata.getReport(plan.tenantId(),plan.reportCode());
        var prior=imports.find(plan.tenantId(),key);
        if(prior!=null) {
            if(!Objects.equals(prior.getCreatedBy(),actor)) throw error(ExampleErrorCode.PERMISSION_DENIED);
            if(!prior.getReportId().equals(report.getId()) || !prior.getFileSha256().equals(digest)) throw error(ExampleErrorCode.DEMO_IMPORT_CONFLICT);
            return prior;
        }
        Set<DemoField> granted=plan.columns().stream().map(c -> c.field()).collect(Collectors.toSet());
        if(!granted.equals(new HashSet<>(input.fields()))) throw error(ExampleErrorCode.PERMISSION_DENIED);
        var batch=new DemoImportBatch(); batch.setTenantId(plan.tenantId()); batch.setReportId(report.getId());
        batch.setImportKey(key); batch.setFileSha256(digest); batch.setRecordCount((long)input.rows().size());
        batch.setPolicyVersion(plan.policyVersion()); batch.setCatalogVersion(plan.catalogVersion()); batch.setCreatedBy(actor);
        batch.setImportedFields(json.writeValueAsString(plan.columns().stream().map(c -> new ImportedField(c.id(),c.field().key())).toList()));
        imports.insert(batch);
        for(int start=0;start<input.rows().size();start+=256) {
            List<DemoRecord> chunk=new ArrayList<>();
            for(var values:input.rows().subList(start,Math.min(start+256,input.rows().size()))) chunk.add(record(plan.tenantId(),batch.getId(),actor,values));
            if(imports.insertRecords(chunk,input.fields())!=chunk.size()) throw new IllegalStateException("Incomplete import chunk");
        }
        return owned(plan.tenantId(),actor,key);
    }
    @Override public DemoImportBatch revoke(DataSqlPlan plan,long actor,UUID key) {
        guard(plan,actor);
        var batch=owned(plan.tenantId(),actor,key);
        var report=metadata.getReport(plan.tenantId(),plan.reportCode());
        if(!batch.getReportId().equals(report.getId())) throw error(ExampleErrorCode.PERMISSION_DENIED);
        var original=originalFields(batch);
        if(!original.equals(plan.columns().stream().map(c -> c.field()).collect(Collectors.toSet()))) throw error(ExampleErrorCode.PERMISSION_DENIED);
        if("REVOKED".equals(batch.getStatus())) return batch;
        imports.revokeRecords(plan.tenantId(),batch.getId(),actor);
        if(imports.revokeBatch(plan.tenantId(),batch.getId(),actor)!=1) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        return owned(plan.tenantId(),actor,key);
    }
    private void guard(DataSqlPlan plan,long actor) {
        if(plan.permission()!=DemoPermission.REPORT_EDIT || plan.rowMode()!=ScopeMode.ALL) throw error(ExampleErrorCode.PERMISSION_DENIED);
        metadata.lockDataset(plan.tenantId(),actor); data.verify(plan);
    }
    private static DemoRecord record(long tenant,long batch,long actor,Map<DemoField,Object> values) {
        var row=new DemoRecord(); row.setTenantId(tenant); row.setBatchId(batch); row.setCreatedBy(actor);
        for(var entry:values.entrySet()) switch(entry.getKey()) {
            case RECORD_CODE -> row.setRecordCode((String)entry.getValue());
            case OWNER_USER_ID -> row.setOwnerUserId((Long)entry.getValue());
            case TEAM_CODE -> row.setTeamCode((String)entry.getValue());
            case DEPARTMENT_CODE -> row.setDepartmentCode((String)entry.getValue());
            case REGION_CODE -> row.setRegionCode((String)entry.getValue());
            case SALE_DATE -> row.setSaleDate((java.time.LocalDate)entry.getValue());
            case PRODUCT_NAME -> row.setProductName((String)entry.getValue());
            case AMOUNT -> row.setAmount((java.math.BigDecimal)entry.getValue());
            case REMARK -> row.setRemark((String)entry.getValue());
        }
        return row;
    }
    private static BizException error(ExampleErrorCode e) { return new BizException(e.getCode(),e.getMessage()); }
}
