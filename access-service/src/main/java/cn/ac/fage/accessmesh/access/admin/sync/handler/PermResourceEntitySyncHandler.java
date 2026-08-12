package cn.ac.fage.accessmesh.access.admin.sync.handler;

import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 资源实体同步处理器。 */
@Component
public class PermResourceEntitySyncHandler extends AbstractPermSyncHandler {

    private final SyncTaskFeignClient feignClient;

    public PermResourceEntitySyncHandler(SyncTaskFeignClient feignClient, ObjectMapper objectMapper) {
        super(objectMapper);
        this.feignClient = feignClient;
    }

    @Override
    public String supportedAction() {
        return SyncTaskBuilder.ACTION_RESOURCE_ENTITY_SYNC;
    }

    @Override
    protected PermResult<SyncResultResp> callSync(Map<String, Object> payload) {
        return feignClient.syncResourceEntity(payload);
    }

    @Override
    protected PermResult<SyncResultResp> callFullSync(Map<String, Object> payload) {
        return feignClient.fullSyncResourceEntity(payload);
    }
}
