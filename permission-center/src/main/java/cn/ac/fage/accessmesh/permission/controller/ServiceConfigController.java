package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigGetReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigApisReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务配置管理控制器
 * <p>
 * 提供微服务配置的管理功能。
 * 服务配置存储微服务的API接口信息，用于Gateway进行接口级权限校验。
 * 支持从服务实例自动同步API接口列表，建立资源与API的映射关系。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/service-config")
public class ServiceConfigController {

    private final ConfigManageService configManageService;

    /**
     * 构造函数注入依赖
     *
     * @param configManageService 配置管理服务
     */
    public ServiceConfigController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    /**
     * 保存服务配置
     * <p>
     * 保存或更新服务配置信息，包括服务编码、服务名称、状态等。
     * </p>
     *
     * @param req 服务配置请求，包含服务编码、服务名称、状态
     * @return 保存后的配置详情
     */
    @PostMapping("/save")
    public PermResult<ServiceConfigResp> saveServiceConfig(@Valid @RequestBody ServiceConfigReq req) {
        return PermResult.success(configManageService.saveServiceConfig(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取服务配置详情
     * <p>
     * 根据服务编码查询服务配置的详细信息。
     * </p>
     *
     * @param req 配置获取请求，包含服务编码
     * @return 服务配置详情信息
     */
    @PostMapping("/detail")
    public PermResult<ServiceConfigResp> getServiceConfig(@Valid @RequestBody ServiceConfigGetReq req) {
        return PermResult.success(configManageService.getServiceConfig(TenantContextHolder.getTenantId(), req.serviceCode()));
    }

    /**
     * 查询服务配置列表
     * <p>
     * 返回租户下所有已注册的微服务配置列表。
     * 用于查看系统中的服务及其API权限配置情况。
     * </p>
     *
     * @param req 空请求，用于保持接口一致性
     * @return 服务配置列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<ServiceConfigResp>> listServiceConfigs(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listServiceConfigs(TenantContextHolder.getTenantId())
        ));
    }

    /**
     * 删除服务配置
     * <p>
     * 批量删除服务配置，会同时处理服务的API映射关系。
     * </p>
     *
     * @param req ID集合请求，包含待删除的服务配置ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteServiceConfig(@Valid @RequestBody IdsReq req) {
        configManageService.deleteServiceConfigsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 同步服务API接口
     * <p>
     * 从服务实例自动同步API接口列表到权限中心。
     * 服务启动时调用此接口注册其所有API，用于Gateway接口级权限校验。
     * </p>
     *
     * @param req 服务同步请求，包含服务编码、API接口列表
     * @return 同步结果，包含新增、更新、删除的接口数量
     */
    @PostMapping("/sync")
    public PermResult<ServiceConfigSyncResp> syncServiceConfig(@Valid @RequestBody ServiceConfigSyncReq req) {
        return PermResult.success(configManageService.syncServiceInterfaces(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 查询服务的API列表
     * <p>
     * 返回指定服务已注册的所有API接口列表。
     * 用于查看服务的API接口配置情况。
     * </p>
     *
     * @param req 服务API查询请求，包含服务编码
     * @return API映射列表
     */
    @PostMapping("/apis")
    public PermResult<ItemsResp<ApiMappingResp>> listServiceApis(@Valid @RequestBody ServiceConfigApisReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listServiceApis(TenantContextHolder.getTenantId(), req.serviceCode())
        ));
    }
}