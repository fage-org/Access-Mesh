package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.ConfigResp;
import cn.ac.fage.accessmesh.access.admin.service.ConfigService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置管理控制器
 * <p>
 * 提供系统配置的分页查询、详情获取、更新、删除等功能。
 * 系统配置存储系统运行时的参数，如开关配置、阈值设置等。
 * 配置项按Key-Value方式存储，支持动态更新。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

    private final ConfigService configService;

    /**
     * 构造函数注入依赖
     *
     * @param configService 配置管理服务
     */
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 分页查询系统配置列表
     * <p>
     * 支持分页查询系统配置项列表。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页配置列表结果
     */
    @PostMapping("/page")
    public R<PageResp<ConfigResp>> pageConfigs(@Valid @RequestBody PageReq pageReq) {
        return R.ok(configService.pageConfigs(pageReq));
    }

    /**
     * 获取配置详情
     * <p>
     * 根据配置ID查询配置项的完整信息。
     * </p>
     *
     * @param req ID请求，包含配置ID
     * @return 配置详情信息
     */
    @PostMapping("/detail")
    public R<ConfigResp> getConfig(@Valid @RequestBody IdReq req) {
        return R.ok(configService.getConfig(req.id()));
    }

    /**
     * 更新配置
     * <p>
     * 更新配置项的值、描述等属性。
     * 配置更新后会影响系统运行行为。
     * </p>
     *
     * @param req 配置更新请求，包含配置ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    public R<Void> updateConfig(@Valid @RequestBody ConfigUpdateReq req) {
        configService.updateConfig(req);
        return R.ok();
    }

    /**
     * 删除配置
     * <p>
     * 批量删除系统配置项。
     * </p>
     *
     * @param req ID集合请求，包含待删除的配置ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    public R<Void> deleteConfig(@Valid @RequestBody IdsReq req) {
        configService.deleteConfig(req);
        return R.ok();
    }
}