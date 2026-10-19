package cn.ac.fage.accessmesh.example.metadata.publication;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.perm.common.feign.PermFeignClientsConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import tools.jackson.databind.JsonNode;

/** Sends the stored single-resource wire payload; never calls user management endpoints. */
@FeignClient(name="access-service", contextId="exampleResourceSync", configuration=PermFeignClientsConfiguration.class)
public interface DemoResourceSyncClient {
    @PostMapping("/api/access/resource-entity/sync")
    R<SyncResultResp> sync(@RequestBody JsonNode payload,
        @RequestHeader("X-Credential-Id") String credentialId,
        @RequestHeader("X-Credential-Secret") String credentialSecret);
}
