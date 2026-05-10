package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置管理控制器
 * <p>
 * 提供系统级配置的管理功能。
 * 系统配置存储全局性的配置项，如缓存策略、权限判定规则等。
 * 配置项按Key-Value方式存储，支持动态更新。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/system-config")
public class SystemConfigController {

    private final ConfigManageService configManageService;

    /**
     * 构造函数注入依赖
     *
     * @param configManageService 配置管理服务
     */
    public SystemConfigController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    /**
     * 保存系统配置
     * <p>
     * 保存或更新系统配置项。如果配置Key已存在则更新，不存在则创建。
     * </p>
     *
     * @param req 系统配置请求，包含配置Key和配置Value
     * @return 保存后的配置详情
     */
    @PostMapping("/save")
    public PermResult<SystemConfigResp> upsertSystemConfig(@Valid @RequestBody SystemConfigReq req) {
        return PermResult.success(configManageService.upsertSystemConfig(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 获取系统配置详情
     * <p>
     * 根据配置Key查询系统配置项的详细信息。
     * </p>
     *
     * @param req 配置获取请求，包含配置Key
     * @return 系统配置详情信息
     */
    @PostMapping("/detail")
    public PermResult<SystemConfigResp> getSystemConfig(@Valid @RequestBody SystemConfigGetReq req) {
        return PermResult.success(configManageService.getSystemConfig(TenantContextHolder.getTenantId(), req.configKey()));
    }

    /**
     * 查询系统配置列表
     * <p>
     * 返回租户下所有的系统配置项列表。
     * 用于查看当前配置的全局参数。
     * </p>
     *
     * @param req 空请求，用于保持接口一致性
     * @return 系统配置列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<SystemConfigResp>> listSystemConfigs(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(configManageService.listSystemConfigs(TenantContextHolder.getTenantId())));
    }
}