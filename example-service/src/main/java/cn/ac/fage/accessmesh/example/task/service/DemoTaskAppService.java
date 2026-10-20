package cn.ac.fage.accessmesh.example.task.service;

import cn.ac.fage.accessmesh.example.task.dto.TaskDtos.*;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.ExecutionResp;
import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
public interface DemoTaskAppService {
    TaskResp create(Caller c,CreateReq r);
    TaskResp get(Caller c,long id);
    PageResp<TaskResp> page(Caller c,PageReq r);
    TaskResp update(Caller c,UpdateReq r);
    TaskResp setEnabled(Caller c,EnabledReq r);
    ExecutionResp trigger(Caller c,TriggerReq r);
    boolean scheduleNext();
    void resetSchedule();
}
