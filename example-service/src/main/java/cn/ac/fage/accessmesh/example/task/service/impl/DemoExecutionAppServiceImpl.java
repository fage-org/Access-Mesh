package cn.ac.fage.accessmesh.example.task.service.impl;

import cn.ac.fage.accessmesh.common.csv.BoundedCsvWriter;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.*;
import cn.ac.fage.accessmesh.example.data.security.DemoDataPermissions;
import cn.ac.fage.accessmesh.example.data.security.DemoPermissionUnavailable;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.task.domain.DemoExecutionDomainService;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.*;
import cn.ac.fage.accessmesh.example.task.entity.DemoExecution;
import cn.ac.fage.accessmesh.example.task.service.DemoExecutionAppService;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;

@Service
public class DemoExecutionAppServiceImpl implements DemoExecutionAppService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DemoExecutionAppServiceImpl.class);
    private final DemoExecutionDomainService executions;
    private final DemoDataDomainService data;
    private final DemoMetadataDomainService metadata;
    private final DemoDataPermissions permissions;
    private final JsonMapper json;
    private final TransactionTemplate write,read;
    public DemoExecutionAppServiceImpl(DemoExecutionDomainService executions,DemoDataDomainService data,
        DemoMetadataDomainService metadata,DemoDataPermissions permissions,JsonMapper json,PlatformTransactionManager transactions) {
        this.executions=executions; this.data=data; this.metadata=metadata; this.permissions=permissions; this.json=json;
        write=new TransactionTemplate(transactions); read=new TransactionTemplate(transactions);
        read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @Override public ExecutionResp submit(Caller c,SubmitReq r) {
        permissions.require(c,DemoPermission.TASK_TRIGGER,null);
        var old=executions.existing(c,r); if(old!=null) return executions.response(old);
        permissions.require(c,DemoPermission.REPORT_EXPORT,r.selection().reportCode());
        var plan=data.authorizeRead(c,r.selection());
        return write.execute(s -> {
            metadata.lockDataset(c.tenantId(),c.userId());
            var existing=executions.existing(c,r); if(existing!=null) return executions.response(existing);
            data.verify(plan); return executions.response(executions.insert(c,r));
        });
    }
    @Override public ExecutionResp get(Caller c,UUID key) {
        permissions.require(c,DemoPermission.TASK_VIEW,null); return executions.response(executions.owned(c,key));
    }
    @Override public PageResp<ExecutionResp> page(Caller c,cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.PageReq r) {
        permissions.require(c,DemoPermission.TASK_VIEW,null); return read.execute(s -> executions.page(c,r));
    }
    @Override public byte[] download(Caller c,UUID key) {
        permissions.require(c,DemoPermission.TASK_VIEW,null); return executions.download(c,key);
    }
    @Override public boolean runNext() {
        var e=write.execute(s -> executions.claim()); if(e==null) return false;
        try {
            var c=new Caller(e.getTenantId(),e.getCreatedBy(),null);
            var selection=json.readValue(e.getRequestPayload(),Selection.class);
            permissions.require(c,DemoPermission.TASK_TRIGGER,null);
            permissions.require(c,DemoPermission.REPORT_EXPORT,selection.reportCode());
            var plan=data.authorizeRead(c,selection);
            read.executeWithoutResult(s -> {
                var page=data.page(plan,1,1001);
                if(page.total()>1000) throw limit();
                e.setPolicyVersion(plan.policyVersion()); e.setCatalogVersion(plan.catalogVersion());
                e.setOutputColumns(json.writeValueAsString(plan.columns().stream().map(col -> new Column(col.id(),col.field().key(),col.field().kind().name())).toList()));
                e.setResultRowCount(page.total());
                if(page.items().isEmpty()) { e.setStatus("EMPTY"); return; }
                var csv=new BoundedCsvWriter(1000,1048576,DemoExecutionAppServiceImpl::limit);
                csv.header(plan.columns().stream().map(col -> col.field().key()).toArray(String[]::new));
                for(var row:page.items()) csv.row(plan.columns().stream().map(col -> row.get(col.field().key())).toArray());
                e.setResultBytes(csv.bytes()); e.setStatus("SUCCESS");
            });
        } catch(RuntimeException failure) { classify(e,failure); }
        // A lost commit acknowledgement leaves RUNNING or the committed terminal result. Recovery never rebuilds SUCCESS.
        try { write.executeWithoutResult(s -> executions.finish(e)); }
        catch(RuntimeException failure) {
            classify(e,failure);
            // If SUCCESS committed but its acknowledgement was lost, the RUNNING predicate keeps it intact.
            write.executeWithoutResult(s -> executions.finish(e));
        }
        return true;
    }
    @Override public void recover() { write.executeWithoutResult(s -> executions.recover()); }
    @Override public void expire() { write.executeWithoutResult(s -> executions.expire()); }
    private static void classify(DemoExecution e,RuntimeException failure) {
        e.setResultBytes(null); e.setPolicyVersion(null); e.setCatalogVersion(null); e.setOutputColumns(null); e.setResultRowCount(null);
        boolean retryable=false;
        String reason="EXECUTION_ERROR",status="FAILED";
        if(failure instanceof DemoPermissionUnavailable unavailable) {
            retryable=unavailable.retryable(); reason="PERMISSION_SERVICE_UNAVAILABLE";
        } else if(failure instanceof BizException business) {
            reason=switch(business.getErrorCode()) {
                case 30004 -> "PERMISSION_DENIED";
                case 30101 -> "OBJECT_UNAVAILABLE";
                case 30102 -> "POLICY_INVALID";
                case 30103 -> "VERSION_CONFLICT";
                case 30106 -> "LIMIT_EXCEEDED";
                case 30108 -> "RESOURCE_NOT_READY";
                default -> "EXECUTION_ERROR";
            };
            if(business.getErrorCode()==30004) status="DENIED";
        } else {
            retryable=failure instanceof TransientDataAccessException || failure instanceof RecoverableDataAccessException;
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
                if(cause instanceof java.sql.SQLException sql) {
                    String state=sql.getSQLState();
                    if("57014".equals(state)) { retryable=false; reason="LIMIT_EXCEEDED"; break; }
                    if(state!=null && (state.startsWith("08") || state.startsWith("40"))) retryable=true;
                }
                if(cause instanceof java.sql.SQLTransientConnectionException || cause instanceof java.sql.SQLRecoverableException) retryable=true;
            }
            log.warn("Snapshot execution failed ({})",failure.getClass().getSimpleName());
        }
        if(retryable && e.getAttempt()<3) status="PENDING";
        e.setStatus(status); e.setFailureReason(reason);
    }
    private static BizException limit() { return new BizException(ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getCode(),ExampleErrorCode.DEMO_LIMIT_EXCEEDED.getMessage()); }
}
