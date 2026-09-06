package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgTreeConfigResp;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgTreeConfigCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgTreeConfigUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.service.OrgTreeConfigService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织树配置管理控制器
 * <p>
 * 提供组织树配置的CRUD操作、设置默认配置等功能。
 * 组织树配置用于定义不同场景下组织树的展示规则，如过滤条件、排序方式等。
 * 可配置多个组织树方案，在不同业务场景使用不同的组织树配置。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/org-tree-config")
public class OrgTreeConfigController {

    private final OrgTreeConfigService orgTreeConfigService;

    /**
     * 构造函数注入依赖
     *
     * @param orgTreeConfigService 组织树配置服务
     */
    public OrgTreeConfigController(OrgTreeConfigService orgTreeConfigService) {
        this.orgTreeConfigService = orgTreeConfigService;
    }

    /**
     * 创建组织树配置
     * <p>
     * 创建新的组织树配置方案，定义组织树的展示规则。
     * </p>
     *
     * @param config 组织树配置实体，包含配置名称、规则定义等
     * @return 创建成功的配置ID
     */
    @PostMapping("/create")
    public R<Long> createOrgTreeConfig(@Valid @RequestBody OrgTreeConfigCreateReq req) {
        return R.ok(orgTreeConfigService.createOrgTreeConfig(req));
    }

    /**
     * 更新组织树配置
     * <p>
     * 更新组织树配置的名称、规则定义等属性。
     * </p>
     *
     * @param config 组织树配置实体，包含配置ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    public R<Void> updateOrgTreeConfig(@Valid @RequestBody OrgTreeConfigUpdateReq req) {
        orgTreeConfigService.updateOrgTreeConfig(req);
        return R.ok();
    }

    /**
     * 删除组织树配置
     * <p>
     * 批量删除组织树配置方案。
     * </p>
     *
     * @param req ID集合请求，包含待删除的配置ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    public R<Void> deleteOrgTreeConfigs(@Valid @RequestBody IdsReq req) {
        orgTreeConfigService.deleteOrgTreeConfigs(req);
        return R.ok();
    }

    /**
     * 设置默认组织树配置
     * <p>
     * 将指定配置设置为默认的组织树展示方案。
     * 系统将使用默认配置展示组织树，并将该树作为用户目录/身份池。
     * 已有用户后切换默认树属于高危迁移动作，服务层需增加保护规则。
     * </p>
     *
     * @param req ID请求，包含配置ID
     * @return 操作成功结果
     */
    @PostMapping("/set-default")
    public R<Void> setDefault(@Valid @RequestBody IdReq req) {
        orgTreeConfigService.setDefault(req.id());
        return R.ok();
    }

    /**
     * 获取组织树配置详情
     * <p>
     * 根据配置ID查询组织树配置的完整信息。
     * </p>
     *
     * @param req ID请求，包含配置ID
     * @return 组织树配置详情信息
     */
    @PostMapping("/detail")
    public R<OrgTreeConfigResp> getOrgTreeConfig(@Valid @RequestBody IdReq req) {
        return R.ok(orgTreeConfigService.getOrgTreeConfig(req.id()));
    }

    /**
     * 分页查询组织树配置列表
     * <p>
     * 查询系统中所有的组织树配置方案，支持分页。
     * </p>
     *
     * @param req 分页查询请求
     * @return 分页组织树配置列表结果
     */
    @PostMapping("/page")
    public R<PaginatedResult<OrgTreeConfigResp>> pageOrgTreeConfigs(@Valid @RequestBody PageReq req) {
        return R.ok(orgTreeConfigService.pageOrgTreeConfigs(req));
    }
}
