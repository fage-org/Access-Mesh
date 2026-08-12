package cn.ac.fage.accessmesh.access.admin.sync.handler;

import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抽象用户同步处理器。
 */
@Component
public class PermAbstractUserSyncHandler extends AbstractPermSyncHandler {

    private final SyncTaskFeignClient feignClient;

    public PermAbstractUserSyncHandler(SyncTaskFeignClient feignClient, ObjectMapper objectMapper) {
        super(objectMapper);
        this.feignClient = feignClient;
    }

    @Override
    public String supportedAction() {
        return SyncTaskBuilder.ACTION_ABSTRACT_USER_SYNC;
    }

    @Override
    protected PermResult<SyncResultResp> callSync(Map<String, Object> payload) {
        return feignClient.syncAbstractUser(payload);
    }

    @Override
    protected PermResult<SyncResultResp> callFullSync(Map<String, Object> payload) {
        return feignClient.fullSyncAbstractUser(payload);
    }
}
