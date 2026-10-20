package cn.ac.fage.accessmesh.example.task.domain;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.*;
import cn.ac.fage.accessmesh.example.task.entity.DemoExecution;
import cn.ac.fage.accessmesh.example.task.mapper.DemoExecutionMapper;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.Column;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;

@Service
public class DemoExecutionDomainService {
    private final DemoExecutionMapper mapper;
    private final JsonMapper json;
    public DemoExecutionDomainService(DemoExecutionMapper mapper,JsonMapper json) { this.mapper=mapper; this.json=json; }
    public DemoExecution existing(Caller c,SubmitReq r) {
        var old=mapper.byTrigger(c.tenantId(),r.triggerKey());
        if(old==null) return null;
        owner(c,old);
        if(!"ONCE".equals(old.getTriggerKind()) || !json.readTree(old.getRequestPayload()).equals(json.valueToTree(r.selection()))) throw error(ExampleErrorCode.DEMO_TRIGGER_CONFLICT);
        return old;
    }
    public DemoExecution insert(Caller c,SubmitReq r) {
        var e=new DemoExecution(); e.setTenantId(c.tenantId()); e.setCreatedBy(c.userId());
        e.setTriggerKind("ONCE"); e.setExecutionKey(UUID.randomUUID()); e.setTriggerKey(r.triggerKey()); e.setRequestPayload(json.writeValueAsString(r.selection()));
        mapper.insert(e); return mapper.byKey(c.tenantId(),e.getExecutionKey());
    }
    public DemoExecution existingManual(Caller c,long task,UUID key) {
        var e=mapper.byTrigger(c.tenantId(),key); if(e==null) return null; owner(c,e);
        if(!"MANUAL".equals(e.getTriggerKind()) || !Long.valueOf(task).equals(e.getTaskId())) throw error(ExampleErrorCode.DEMO_TRIGGER_CONFLICT);
        return e;
    }
    public DemoExecution enqueueTask(cn.ac.fage.accessmesh.example.task.entity.DemoTask task,UUID trigger,java.time.LocalDateTime time) {
        if(time!=null) {
            var existing=mapper.bySchedule(task.getTenantId(),task.getId(),time); if(existing!=null) return existing;
        }
        var e=new DemoExecution(); e.setTenantId(task.getTenantId()); e.setCreatedBy(task.getCreatedBy());
        e.setExecutionKey(UUID.randomUUID()); e.setTriggerKey(trigger); e.setTriggerKind(time==null?"MANUAL":"CRON");
        e.setTaskId(task.getId()); e.setTaskVersion(task.getVersion()); e.setScheduledTime(time); e.setRequestPayload(task.getSelection());
        mapper.insert(e); return mapper.byKey(e.getTenantId(),e.getExecutionKey());
    }
    public DemoExecution owned(Caller c,UUID key) { var e=mapper.byKey(c.tenantId(),key); owner(c,e); return e; }
    public byte[] download(Caller c,UUID key) {
        owned(c,key); var result=mapper.download(c.tenantId(),c.userId(),key);
        if(result==null || result.getResultBytes()==null) throw error(ExampleErrorCode.DEMO_RESULT_UNAVAILABLE); return result.getResultBytes();
    }
    public PageResp<ExecutionResp> page(Caller c,PageReq r) {
        if(r.taskId()!=null && !mapper.ownsTask(c.tenantId(),c.userId(),r.taskId())) throw error(ExampleErrorCode.PERMISSION_DENIED);
        int page=r.pageNum()==null?1:r.pageNum(),size=r.pageSize()==null?20:r.pageSize();
        var items=mapper.page(c.tenantId(),c.userId(),r.taskId(),size,(long)(page-1)*size).stream().map(this::response).toList();
        long total=mapper.count(c.tenantId(),c.userId(),r.taskId());
        return new PageResp<>(items,total,page,size,(long)page*size<total);
    }
    public DemoExecution claim() {
        var e=mapper.next(); if(e==null || mapper.claim(e)!=1) return null;
        e.setAttempt(e.getAttempt()+1); e.setStatus("RUNNING"); return e;
    }
    public boolean finish(DemoExecution e) { return mapper.finish(e)==1; }
    public void recover() { mapper.recover(); }
    public void expire() { mapper.expire(); }
    public ExecutionResp response(DemoExecution e) {
        List<Column> columns=e.getOutputColumns()==null?null:List.of(json.readValue(e.getOutputColumns(),Column[].class));
        return new ExecutionResp(e.getExecutionKey(),e.getTaskId(),e.getStatus(),e.getAttempt(),e.getScheduledTime(),
            e.getPolicyVersion(),e.getCatalogVersion(),columns,e.getResultRowCount(),e.getExpiresAt(),e.getFailureReason());
    }
    private static void owner(Caller c,DemoExecution e) {
        if(e==null || !Long.valueOf(c.userId()).equals(e.getCreatedBy())) throw error(ExampleErrorCode.PERMISSION_DENIED);
    }
    private static BizException error(ExampleErrorCode e) { return new BizException(e.getCode(),e.getMessage()); }
}
