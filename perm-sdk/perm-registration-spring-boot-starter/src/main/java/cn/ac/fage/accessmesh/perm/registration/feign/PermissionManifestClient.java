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
    /** 租户/服务身份由服务端从凭证行派生，自报 X-Tenant-Id/X-Service-Code 头一律忽略——只发凭证头。 */
    @PostMapping("/api/access/integration/permission-manifest/full-sync")
    R<SyncResultResp> fullSync(@RequestHeader("X-Credential-Id") String credentialId,
                             @RequestHeader("X-Credential-Secret") String credentialSecret,
                             @RequestBody PermissionManifestReq manifest);
}
