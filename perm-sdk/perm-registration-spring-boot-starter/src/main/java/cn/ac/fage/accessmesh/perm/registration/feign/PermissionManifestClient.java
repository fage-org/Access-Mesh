package cn.ac.fage.accessmesh.perm.registration.feign;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "access-service", contextId = "permissionRegistration", primary = false)
public interface PermissionManifestClient {
    @PostMapping("/api/access/integration/permission-manifest/full-sync")
    R<SyncResultResp> fullSync(@RequestHeader("X-Tenant-Id") Long tenantId,
                             @RequestHeader("X-Service-Code") String serviceCode,
                             @RequestHeader("X-Credential-Id") String credentialId,
                             @RequestHeader("X-Credential-Secret") String credentialSecret,
                             @RequestBody PermissionManifestReq manifest);
}
