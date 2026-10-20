package cn.ac.fage.accessmesh.example.task.service.impl;

import cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService;
import cn.ac.fage.accessmesh.example.task.domain.DemoTaskDomainService;
import cn.ac.fage.accessmesh.example.task.dto.TaskDtos.*;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.ExecutionResp;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.metadata.DemoPermission;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.data.security.DemoDataPermissions;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoTaskAppServiceImpl implements DemoTaskAppService {
    private final DemoTaskDomainService tasks;
    private final DemoMetadataDomainService metadata;
    private final DemoDataDomainService data;
    private final DemoDataPermissions permissions;
    private final TransactionTemplate write,read;
    public DemoTaskAppServiceImpl(DemoTaskDomainService tasks,DemoMetadataDomainService metadata,DemoDataDomainService data,
        DemoDataPermissions permissions,PlatformTransactionManager transactions) {
        this.tasks=tasks; this.metadata=metadata; this.data=data; this.permissions=permissions;
        write=new TransactionTemplate(transactions); read=new TransactionTemplate(transactions);
        read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @Override public TaskResp create(Caller c,CreateReq r) {
        permissions.require(c,DemoPermission.TASK_CREATE,null); permissions.require(c,DemoPermission.REPORT_EXPORT,r.selection().reportCode());
        var plan=data.authorizeRead(c,r.selection());
        return write.execute(s->{metadata.lockDataset(c.tenantId(),c.userId()); data.verify(plan); return tasks.create(c,r);});
    }
    @Override public TaskResp get(Caller c,long id) {
        permissions.require(c,DemoPermission.TASK_VIEW,null); return tasks.response(tasks.owned(c,id));
    }
    @Override public PageResp<TaskResp> page(Caller c,PageReq r) {
        permissions.require(c,DemoPermission.TASK_VIEW,null); return read.execute(s->tasks.page(c,r));
    }
    @Override public TaskResp update(Caller c,UpdateReq r) {
        permissions.require(c,DemoPermission.TASK_UPDATE,null); tasks.owned(c,r.taskId());
        permissions.require(c,DemoPermission.REPORT_EXPORT,r.selection().reportCode()); var plan=data.authorizeRead(c,r.selection());
        return write.execute(s->{metadata.lockDataset(c.tenantId(),c.userId()); data.verify(plan); return tasks.update(c,r);});
    }
    @Override public TaskResp setEnabled(Caller c,EnabledReq r) {
        permissions.require(c,DemoPermission.TASK_ENABLE,null); tasks.owned(c,r.taskId());
        return write.execute(s->{metadata.lockDataset(c.tenantId(),c.userId()); return tasks.enable(c,r);});
    }
    @Override public ExecutionResp trigger(Caller c,TriggerReq r) {
        permissions.require(c,DemoPermission.TASK_TRIGGER,null); tasks.owned(c,r.taskId());
        return write.execute(s->{metadata.lockDataset(c.tenantId(),c.userId()); return tasks.trigger(c,r);});
    }
    @Override public boolean scheduleNext() {
        var candidate=tasks.due(); if(candidate==null) return false;
        return Boolean.TRUE.equals(write.execute(s->{metadata.lockDataset(candidate.getTenantId(),candidate.getCreatedBy()); return tasks.schedule(candidate);}));
    }
    @Override public void resetSchedule() { write.executeWithoutResult(s->tasks.resetSchedule()); }
}
