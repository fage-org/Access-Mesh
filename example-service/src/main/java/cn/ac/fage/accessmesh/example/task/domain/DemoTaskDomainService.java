package cn.ac.fage.accessmesh.example.task.domain;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.task.dto.TaskDtos;
import cn.ac.fage.accessmesh.example.task.dto.TaskDtos.*;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.ExecutionResp;
import cn.ac.fage.accessmesh.example.task.entity.DemoTask;
import cn.ac.fage.accessmesh.example.task.entity.DemoExecution;
import cn.ac.fage.accessmesh.example.task.mapper.DemoTaskMapper;
import cn.ac.fage.accessmesh.example.data.dto.DataDtos.Selection;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.support.CronExpression;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;

@Service
public class DemoTaskDomainService {
    private final DemoTaskMapper mapper;
    private final DemoExecutionDomainService executions;
    private final JsonMapper json;
    public DemoTaskDomainService(DemoTaskMapper mapper,DemoExecutionDomainService executions,JsonMapper json) { this.mapper=mapper; this.executions=executions; this.json=json; }
    public DemoTask owned(Caller c,long id) { return owner(c,mapper.get(c.tenantId(),id)); }
    public DemoTask locked(Caller c,long id) { return owner(c,mapper.lock(c.tenantId(),id)); }
    public TaskResp create(Caller c,CreateReq r) {
        next(r.cron(),mapper.now());
        var task=new DemoTask(); task.setTenantId(c.tenantId()); task.setCreatedBy(c.userId());
        task.setName(r.name()); task.setCron(r.cron()); task.setSelection(json.writeValueAsString(r.selection()));
        mapper.insert(task); return response(mapper.get(c.tenantId(),task.getId()));
    }
    public TaskResp update(Caller c,UpdateReq r) {
        var task=locked(c,r.taskId()); version(task,r.expectedVersion());
        var next=next(r.cron(),mapper.now());
        task.setName(r.name()); task.setCron(r.cron()); task.setSelection(json.writeValueAsString(r.selection()));
        task.setNextScheduledTime(task.getEnabled()?next:null);
        if(mapper.update(task)!=1) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        return response(mapper.get(c.tenantId(),task.getId()));
    }
    public TaskResp enable(Caller c,EnabledReq r) {
        var task=locked(c,r.taskId()); version(task,r.expectedVersion());
        task.setEnabled(r.enabled()); task.setNextScheduledTime(r.enabled()?next(task.getCron(),mapper.now()):null);
        if(mapper.enable(task)!=1) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        return response(mapper.get(c.tenantId(),task.getId()));
    }
    public ExecutionResp trigger(Caller c,TriggerReq r) {
        var task=locked(c,r.taskId());
        var existing=executions.existingManual(c,r.taskId(),r.triggerKey());
        if(existing!=null) return executions.response(existing);
        if(!task.getEnabled()) throw error(ExampleErrorCode.DEMO_OBJECT_UNAVAILABLE);
        return executions.response(executions.enqueueTask(task,r.triggerKey(),null));
    }
    public DemoTask due() { return mapper.due(); }
    public boolean schedule(DemoTask candidate) {
        var c=new Caller(candidate.getTenantId(),candidate.getCreatedBy(),null);
        var task=locked(c,candidate.getId()); var now=mapper.now();
        if(!task.getEnabled() || task.getNextScheduledTime()!=null && task.getNextScheduledTime().isAfter(now)) return false;
        if(task.getNextScheduledTime()!=null) executions.enqueueTask(task,null,task.getNextScheduledTime());
        // One current due instant is recorded; neither restart nor a delayed poll expands a missed-time backlog.
        if(mapper.schedule(task,next(task.getCron(),now))!=1) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT);
        return task.getNextScheduledTime()!=null;
    }
    public boolean executable(DemoExecution e) {
        if(e.getTaskId()==null) return true;
        var task=mapper.get(e.getTenantId(),e.getTaskId());
        return task!=null && task.getEnabled() && task.getCreatedBy().equals(e.getCreatedBy());
    }
    public void resetSchedule() { mapper.resetSchedule(); }
    public PageResp<TaskResp> page(Caller c,PageReq r) {
        int page=r.pageNum()==null?1:r.pageNum(),size=r.pageSize()==null?20:r.pageSize();
        var items=mapper.page(c.tenantId(),c.userId(),size,(long)(page-1)*size).stream().map(this::response).toList();
        long total=mapper.count(c.tenantId(),c.userId()); return new PageResp<>(items,total,page,size,(long)page*size<total);
    }
    public TaskResp response(DemoTask task) { return new TaskResp(task.getId(),task.getName(),task.getCron(),json.readValue(task.getSelection(),Selection.class),task.getEnabled(),task.getVersion()); }
    private static DemoTask owner(Caller c,DemoTask task) {
        if(task==null || !Long.valueOf(c.userId()).equals(task.getCreatedBy())) throw error(ExampleErrorCode.PERMISSION_DENIED); return task;
    }
    private static void version(DemoTask task,long version) { if(task.getVersion()!=version) throw error(ExampleErrorCode.DEMO_VERSION_CONFLICT); }
    private static LocalDateTime next(String cron,LocalDateTime now) {
        if(!TaskDtos.validCron(cron)) throw new BizException(cn.ac.fage.accessmesh.common.enums.GlobalErrorCode.VALIDATION_FAILED.code(),"cron 必须为 UTC 六段表达式，秒字段为 0");
        var next=CronExpression.parse(cron).next(now);
        if(next==null) throw new BizException(cn.ac.fage.accessmesh.common.enums.GlobalErrorCode.VALIDATION_FAILED.code(),"cron 没有后续触发时刻"); return next;
    }
    private static BizException error(ExampleErrorCode e) { return new BizException(e.getCode(),e.getMessage()); }
}
