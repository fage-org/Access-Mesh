package cn.ac.fage.accessmesh.access.infrastructure.credential.controller;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialCreateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialListReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialUpdateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialCreateResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.ServiceCredentialAppService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务凭证管理控制器（T-PERM-070，契约总册服务凭证章）。
 * <p>
 * per-service M2M 凭证的管理面（签发/轮换/吊销）：全 POST + JSON Body；
 * create 响应回传明文 secret 仅一次。认证协议（X-Credential-Id/X-Credential-Secret
 * 头与仲裁器状态表）见 service-authentication.md §3.2。
 * </p>
 */
@RestController
@RequestMapping("/api/access/service-credential")
public class ServiceCredentialController {

    private final ServiceCredentialAppService serviceCredentialAppService;

    public ServiceCredentialController(ServiceCredentialAppService serviceCredentialAppService) {
        this.serviceCredentialAppService = serviceCredentialAppService;
    }

    /** 签发凭证（明文 secret 仅本响应回显一次）。 */
    @PostMapping("/create")
    public R<ServiceCredentialCreateResp> create(@Valid @RequestBody ServiceCredentialCreateReq req) {
        return R.ok(serviceCredentialAppService.create(TenantContextHolder.getTenantId(), req, null));
    }

    /** 更新凭证（status 启停 / expiresAt 改期）。 */
    @PostMapping("/update")
    public R<ServiceCredentialResp> update(@Valid @RequestBody ServiceCredentialUpdateReq req) {
        return R.ok(serviceCredentialAppService.update(TenantContextHolder.getTenantId(), req, null));
    }

    /** 删除凭证（软删，立即失效）。 */
    @PostMapping("/remove")
    public R<Void> remove(@Valid @RequestBody IdReq req) {
        serviceCredentialAppService.remove(TenantContextHolder.getTenantId(), req.id(), null);
        return R.ok();
    }

    /** 凭证列表（serviceCode 可选过滤）。 */
    @PostMapping("/list")
    public R<ItemsResp<ServiceCredentialResp>> list(@Valid @RequestBody ServiceCredentialListReq req) {
        return R.ok(serviceCredentialAppService.list(TenantContextHolder.getTenantId(), req));
    }
}
