package cn.ac.fage.accessmesh.admin.sync.handler;

import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 抽象角色同步处理器。 */
@Component
public class PermAbstractRoleSyncHandler extends AbstractPermSyncHandler {

    private final SyncTaskFeignClient feignClient;

    public PermAbstractRoleSyncHandler(SyncTaskFeignClient feignClient, ObjectMapper objectMapper) {
        super(objectMapper);
        this.feignClient = feignClient;
    }

    @Override
    public String supportedAction() {
        return SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC;
    }

    @Override
    protected PermResult<SyncResultResp> callSync(Map<String, Object> payload) {
        return feignClient.syncAbstractRole(payload);
    }

    @Override
    protected PermResult<SyncResultResp> callFullSync(Map<String, Object> payload) {
        return feignClient.fullSyncAbstractRole(payload);
    }
}
