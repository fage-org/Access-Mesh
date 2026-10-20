package cn.ac.fage.accessmesh.example.data.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.data.domain.DemoPolicyDomainService;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;
import cn.ac.fage.accessmesh.example.data.mapper.DemoDataMapper;
import cn.ac.fage.accessmesh.example.data.policy.PolicyCompiler;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration;
import cn.ac.fage.accessmesh.example.data.policy.PolicyConfiguration.*;
import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan;
import cn.ac.fage.accessmesh.example.data.query.DataSqlPlan.ColumnBinding;
import cn.ac.fage.accessmesh.example.data.security.DemoDataPermissions;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.DemoResourceKey;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import cn.ac.fage.accessmesh.example.metadata.entity.DemoScope;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DemoDataDomainServiceImpl implements DemoDataDomainService {
    private final DemoMetadataDomainService metadata;
    private final DemoPolicyDomainService policies;
    private final PolicyCompiler compiler;
    private final DemoDataPermissions permissions;
    private final DemoDataMapper records;
    private final JsonMapper json;
    public DemoDataDomainServiceImpl(DemoMetadataDomainService metadata,DemoPolicyDomainService policies,
        PolicyCompiler compiler,DemoDataPermissions permissions,DemoDataMapper records,JsonMapper json) {
        this.metadata=metadata; this.policies=policies; this.compiler=compiler;
        this.permissions=permissions; this.records=records; this.json=json;
    }
    @Override
    public DataSqlPlan authorizeRead(Caller caller,Selection selection) { return authorize(caller,selection,null); }
    @Override
    public DataSqlPlan authorizeSnapshot(Caller caller,Selection selection) { return authorize(caller,selection,null,true); }
    @Override
    public DataSqlPlan authorizeEdit(Caller caller,String code) { return authorize(caller,new Selection(code,null,null,null),Set.of(DemoField.REMARK)); }
    @Override
    public DataSqlPlan authorizeImport(Caller caller,String code,Set<DemoField> fields) {
        var plan=authorize(caller,new Selection(code,null,null,null),fields);
        if(plan.rowMode()!=ScopeMode.ALL) throw error(ExampleErrorCode.PERMISSION_DENIED);
        return plan;
    }

    private DataSqlPlan authorize(Caller caller,Selection selection,Set<DemoField> writeFields) {
        return authorize(caller,selection,writeFields,false);
    }
    private DataSqlPlan authorize(Caller caller,Selection selection,Set<DemoField> writeFields,boolean frozen) {
        boolean edit=writeFields!=null;
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Remote authorization must precede the data transaction");
        var report=metadata.getReport(caller.tenantId(),selection.reportCode()); requireReport(report);
        var frozenFields=frozen?metadata.getFields(caller.tenantId(),report.getReportCode()):null;
        if(frozen) verifyFrozenReferences(selection,frozenFields);
        var stored=policies.current(caller.tenantId());
        var configuration=compiler.parse(json.readTree(stored.getConfiguration()));
        var operations=edit?configuration.edit():configuration.read();
        if(!report.getFieldResourceTypeCode().equals(operations.fieldResourceTypeCode())) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
        var scopes=permissions.query(caller,report.getReportCode(),operations,edit?DemoPermission.REPORT_EDIT:DemoPermission.REPORT_READ);
        var sql=new PredicateSql();
        Map<DemoResourceKey,Long> versions=new LinkedHashMap<>();
        String rowPredicate="TRUE";
        if(scopes.rows().scopeMode()==ScopeMode.EMPTY) rowPredicate="FALSE";
        if(scopes.rows().scopeMode()==ScopeMode.INSTANCE) {
            Set<DemoResourceKey> keys=scopes.rows().items().stream().map(i -> new DemoResourceKey(operations.rowResourceTypeCode(),i.resourceCode(),DemoDataPermissions.codeType(i.codeType())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if(!configuration.bindings().keySet().containsAll(keys)) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            var local=metadata.scopes(caller.tenantId(),keys);
            if(local.size()!=keys.size()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            for(var scope:local) {
                requireScope(scope);
                versions.put(key(scope),scope.getVersion());
            }
            List<String> alternatives=new ArrayList<>();
            for(var key:keys) {
                List<String> clauses=new ArrayList<>();
                for(var predicate:configuration.bindings().get(key)) {
                    var values=predicate.source()==ValueSource.SUBJECT_ID?List.<Object>of(caller.userId()):predicate.literals();
                    clauses.add(sql.clause(predicate.field(),predicate.operator(),values));
                }
                alternatives.add("("+String.join(" AND ",clauses)+")");
            }
            rowPredicate="("+String.join(" OR ",alternatives)+")";
        }
        var currentFields=frozenFields==null?metadata.getFields(caller.tenantId(),report.getReportCode()):frozenFields;
        Set<Long> granted=new HashSet<>();
        if(scopes.fields().scopeMode()==ScopeMode.ALL) currentFields.forEach(f -> granted.add(f.getId()));
        if(scopes.fields().scopeMode()==ScopeMode.INSTANCE) {
            if(scopes.fields().items().stream().anyMatch(i -> !"default".equals(DemoDataPermissions.codeType(i.codeType())))) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            Set<String> codes=scopes.fields().items().stream().map(i -> i.resourceCode()).collect(Collectors.toSet());
            var known=metadata.fieldsByResourceCodes(caller.tenantId(),operations.fieldResourceTypeCode(),codes);
            if(known.size()!=codes.size()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            known.stream().filter(f -> f.getReportId().equals(report.getId()) && f.getDeleteFlag()==0 && f.getEnabled())
                .forEach(f -> granted.add(f.getId()));
        }
        Map<Long,DemoReportField> allowed=new LinkedHashMap<>();
        currentFields.stream().filter(f -> f.getEnabled() && granted.contains(f.getId())).forEach(f -> allowed.put(f.getId(),f));
        List<DemoReportField> selected;
        if(edit) {
            selected=allowed.values().stream().filter(f -> writeFields.contains(DemoField.require(f.getFieldKey()))).toList();
            if(selected.size()!=writeFields.size() || scopes.rows().scopeMode()==ScopeMode.EMPTY) throw error(ExampleErrorCode.PERMISSION_DENIED);
        } else if(selection.fieldIds()==null) selected=List.copyOf(allowed.values());
        else {
            if(selection.fieldIds().isEmpty() || new HashSet<>(selection.fieldIds()).size()!=selection.fieldIds().size()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            selected=selection.fieldIds().stream().map(id -> requireAllowed(allowed,id)).toList();
        }
        for(var field:selected) requirePublished(field.getPublicationStatus());
        List<String> predicates=new ArrayList<>(); predicates.add(rowPredicate);
        if(!edit && selection.filters()!=null) {
            if(selection.filters().size()>8) throw error(ExampleErrorCode.DEMO_LIMIT_EXCEEDED);
            for(var filter:selection.filters()) {
                if(filter==null || filter.operator()==null) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
                var field=requireAllowed(allowed,filter.fieldId()); requirePublished(field.getPublicationStatus());
                var definition=DemoField.require(field.getFieldKey());
                if(!configuration.fields().get(definition).filterable()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
                Operator operator;
                try { operator=Operator.valueOf(filter.operator()); } catch(IllegalArgumentException e) { throw error(ExampleErrorCode.DEMO_POLICY_INVALID); }
                List<Object> values=new ArrayList<>();
                if(operator==Operator.EQ) {
                    if(filter.values()!=null || filter.value()==null || filter.value().isNull()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
                    values.add(definition.literal(filter.value()));
                } else {
                    if(filter.value()!=null && !filter.value().isNull() || filter.values()==null || filter.values().isEmpty()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
                    if(filter.values().size()>100) throw error(ExampleErrorCode.DEMO_LIMIT_EXCEEDED);
                    filter.values().forEach(value -> values.add(definition.literal(value)));
                }
                predicates.add(sql.clause(definition,operator,values));
            }
        }
        String order="r.id";
        if(!edit && selection.sort()!=null) {
            var field=requireAllowed(allowed,selection.sort().fieldId()); requirePublished(field.getPublicationStatus());
            var definition=DemoField.require(field.getFieldKey());
            if(!configuration.fields().get(definition).sortable() || selection.sort().direction()==null || !Set.of("ASC","DESC").contains(selection.sort().direction())) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            order="r."+definition.column()+" "+selection.sort().direction()+",r.id";
        }
        var columns=selected.stream().map(f -> {
            var field=DemoField.require(f.getFieldKey()); var flags=configuration.fields().get(field);
            return new ColumnBinding(f.getId(),field,flags.filterable(),flags.sortable());
        }).toList();
        return new DataSqlPlan(caller.tenantId(),report.getDatasetId(),report.getReportCode(),report.getVersion(),stored.getVersion(),
            report.getCatalogVersion(),Map.copyOf(versions),edit?DemoPermission.REPORT_EDIT:DemoPermission.REPORT_READ,
            scopes.rows().scopeMode(),columns,String.join(" AND ",predicates),List.copyOf(sql.parameters),order);
    }

    @Override
    public void verify(DataSqlPlan plan) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Data access requires a local snapshot or write transaction");
        records.setTimeout();
        var report=metadata.getReport(plan.tenantId(),plan.reportCode());
        if(report.getVersion()!=plan.reportVersion() || report.getCatalogVersion()!=plan.catalogVersion()
                || report.getDatasetId()!=plan.datasetId() || policies.current(plan.tenantId()).getVersion()!=plan.policyVersion()) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        requireReport(report);
        var fields=metadata.getFields(plan.tenantId(),plan.reportCode()).stream().collect(Collectors.toMap(DemoReportField::getId,f -> f));
        for(var selected:plan.columns()) {
            var field=fields.get(selected.id());
            if(field==null || !field.getEnabled() || !field.getFieldKey().equals(selected.field().key())) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
            requirePublished(field.getPublicationStatus());
        }
        var scopes=metadata.scopes(plan.tenantId(),plan.scopeVersions().keySet());
        if(scopes.size()!=plan.scopeVersions().size()) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        for(var scope:scopes) {
            if(!Objects.equals(scope.getVersion(),plan.scopeVersions().get(key(scope)))) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
            requireScope(scope);
        }
    }
    @Override
    public PageResp<Map<String,Object>> page(DataSqlPlan plan,int page,int size) {
        requireRead(plan);
        verify(plan);
        if(empty(plan)) return new PageResp<>(List.of(),0,page,size,false);
        var rows=records.page(plan,size,(long)(page-1)*size).stream().map(r -> values(plan,r)).toList();
        long total=records.count(plan);
        return new PageResp<>(rows,total,page,size,(long)page*size<total);
    }
    @Override
    public Map<String,Object> get(DataSqlPlan plan,String code) {
        requireRead(plan);
        verify(plan); if(empty(plan)) return null;
        var rows=records.one(plan,code); return rows.isEmpty()?null:values(plan,rows.getFirst());
    }
    @Override
    public Map<Integer,Map<String,Object>> batch(DataSqlPlan plan,List<String> codes) {
        requireRead(plan);
        verify(plan); if(empty(plan)) return Map.of();
        Map<Integer,Map<String,Object>> result=new HashMap<>();
        for(var row:records.batch(plan,codes)) result.put(((Number)row.get("requestIndex")).intValue(),values(plan,row));
        return Map.copyOf(result);
    }
    @Override
    public int update(DataSqlPlan plan,long actor,List<Change> changes) {
        if(plan.permission()!=DemoPermission.REPORT_EDIT || plan.columns().stream().noneMatch(c -> c.field()==DemoField.REMARK)) throw error(ExampleErrorCode.PERMISSION_DENIED);
        records.setTimeout(); metadata.lockDataset(plan.tenantId(),actor); verify(plan);
        if(empty(plan)) throw error(ExampleErrorCode.PERMISSION_DENIED);
        int updated=records.update(plan,actor,changes);
        if(updated!=changes.size()) throw error(ExampleErrorCode.PERMISSION_DENIED);
        return updated;
    }
    private static Map<String,Object> values(DataSqlPlan plan,Map<String,Object> row) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(var column:plan.columns()) result.put(column.field().key(),row==null?null:row.get(column.field().key()));
        return Collections.unmodifiableMap(result);
    }
    private static boolean empty(DataSqlPlan plan) { return plan.emptyRows() || plan.columns().isEmpty(); }
    private static void requireRead(DataSqlPlan plan) { if(plan.permission()!=DemoPermission.REPORT_READ) throw error(ExampleErrorCode.PERMISSION_DENIED); }
    private static void verifyFrozenReferences(Selection selection,List<DemoReportField> fields) {
        var byId=fields.stream().collect(Collectors.toMap(DemoReportField::getId,f->f));
        Set<Long> referenced=new HashSet<>();
        if(selection.fieldIds()!=null) referenced.addAll(selection.fieldIds());
        if(selection.filters()!=null) selection.filters().forEach(f->referenced.add(f.fieldId()));
        if(selection.sort()!=null) referenced.add(selection.sort().fieldId());
        for(Long id:referenced) {
            var field=byId.get(id);
            if(field==null || !field.getEnabled()) throw error(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE);
            requirePublished(field.getPublicationStatus());
        }
    }
    private static DemoReportField requireAllowed(Map<Long,DemoReportField> allowed,Long id) {
        var field=allowed.get(id); if(field==null) throw error(ExampleErrorCode.PERMISSION_DENIED); return field;
    }
    private static void requireReport(DemoReport report) {
        if(!report.getEnabled()) throw error(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE);
        requirePublished(report.getPublicationStatus());
    }
    private static void requireScope(DemoScope scope) {
        if(!scope.getEnabled()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
        requirePublished(scope.getPublicationStatus());
    }
    private static void requirePublished(String status) { if(!"PUBLISHED".equals(status)) throw error(ExampleErrorCode.DEMO_RESOURCE_NOT_READY); }
    private static DemoResourceKey key(DemoScope s) { return new DemoResourceKey(s.getResourceTypeCode(),s.getResourceCode(),s.getCodeType()); }
    private static BizException error(ExampleErrorCode e) { return new BizException(e.getCode(),e.getMessage()); }
    private static final class PredicateSql {
        private final List<Object> parameters=new ArrayList<>();
        String clause(DemoField field,Operator operator,List<Object> values) {
            if(values.isEmpty()) throw error(ExampleErrorCode.DEMO_POLICY_INVALID);
            List<String> slots=new ArrayList<>();
            for(var value:values) {
                if(parameters.size()>=4096) throw error(ExampleErrorCode.DEMO_LIMIT_EXCEEDED);
                slots.add("#{plan.parameters["+parameters.size()+"]}"); parameters.add(value);
            }
            return "r."+field.column()+(operator==Operator.EQ?"="+slots.getFirst():" IN ("+String.join(",",slots)+")");
        }
    }
}
