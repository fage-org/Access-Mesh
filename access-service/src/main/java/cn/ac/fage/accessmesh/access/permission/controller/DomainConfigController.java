package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigGetReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.service.DomainConfigAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 域配置管理控制器
 * <p>
 * 提供业务域级配置的管理功能。
 * 域配置存储特定业务域的配置项，如权限策略、缓存设置等。
 * 配置项按域编码和配置类型存储，支持动态更新。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/domain-config")
public class DomainConfigController {

    private final DomainConfigAppService domainConfigAppService;

    /**
     * 构造函数注入依赖
     *
     * @param domainConfigAppService 域配置应用服务
     */
    public DomainConfigController(DomainConfigAppService domainConfigAppService) {
        this.domainConfigAppService = domainConfigAppService;
    }

    /**
     * 保存域配置
     * <p>
     * 保存或更新域配置项。如果配置已存在则更新，不存在则创建。
     * </p>
     *
     * @param req 域配置请求，包含域编码、配置类型、配置Value
     * @return 保存后的配置详情
     */
    @PostMapping("/save")
    public R<DomainConfigResp> upsertDomainConfig(@Valid @RequestBody DomainConfigReq req) {
        return R.ok(domainConfigAppService.upsertDomainConfig(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 获取域配置详情
     * <p>
     * 根据域编码和配置类型查询域配置项的详细信息。
     * </p>
     *
     * @param req 配置获取请求，包含域编码和配置类型
     * @return 域配置详情信息
     */
    @PostMapping("/detail")
    public R<DomainConfigResp> getDomainConfig(@Valid @RequestBody DomainConfigGetReq req) {
        return R.ok(domainConfigAppService.getDomainConfig(TenantContextHolder.getTenantId(), req.domainCode(), req.configType()));
    }

    /**
     * 查询域配置列表
     * <p>
     * 返回指定域下的所有配置项列表。
     * 用于查看域级别的配置参数。
     * </p>
     *
     * @param req 配置列表查询请求，包含域编码
     * @return 域配置列表
     */
    @PostMapping("/list")
    public R<ItemsResp<DomainConfigResp>> listDomainConfigs(@Valid @RequestBody DomainConfigListReq req) {
        return R.ok(new ItemsResp<>(
            domainConfigAppService.listDomainConfigs(TenantContextHolder.getTenantId(), req.domainCode())
        ));
    }

    /**
     * 删除域配置
     * <p>
     * 批量删除域配置项。
     * </p>
     *
     * @param req ID集合请求，包含待删除的配置ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public R<Void> deleteDomainConfig(@Valid @RequestBody IdsReq req) {
        domainConfigAppService.deleteDomainConfigsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return R.ok();
    }
}