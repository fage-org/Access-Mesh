package cn.ac.fage.accessmesh.example.task.service;

import cn.ac.fage.accessmesh.example.metadata.dto.MetadataDtos.Caller;
import cn.ac.fage.accessmesh.example.task.dto.ExecutionDtos.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import java.util.UUID;

public interface DemoExecutionAppService {
    ExecutionResp submit(Caller caller,SubmitReq request);
    ExecutionResp get(Caller caller,UUID key);
    PageResp<ExecutionResp> page(Caller caller,PageReq request);
    byte[] download(Caller caller,UUID key);
    boolean runNext();
    void recover();
    void expire();
}
