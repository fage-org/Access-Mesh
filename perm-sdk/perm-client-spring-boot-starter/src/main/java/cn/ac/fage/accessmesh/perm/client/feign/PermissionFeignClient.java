package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "permission-center", path = "/internal")
public interface PermissionFeignClient {

    @PostMapping("/check")
    PermResult<PermCheckResp> checkPermission(@RequestBody PermCheckReq req);
}
