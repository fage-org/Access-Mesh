package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionDetailReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionRemoveReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.service.ConditionAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限条件管理控制器
 * <p>
 * 提供权限条件的CRUD操作和查询功能。
 * 权限条件用于定义动态权限规则，如时间范围、地域限制等。
 * 条件权限可以在角色权限配置中关联，实现细粒度的访问控制。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/permission-condition")
public class ConditionController {

    private final ConditionAppService conditionAppService;

    /**
     * 构造函数注入依赖
     *
     * @param conditionAppService 条件管理服务
     */
    public ConditionController(ConditionAppService conditionAppService) {
        this.conditionAppService = conditionAppService;
    }

    /**
     * 创建权限条件
     * <p>
     * 创建新的权限条件定义，用于动态权限规则配置。
     * 条件类型包括：时间范围、地域限制、组织归属等。
     * </p>
     *
     * @param req 条件创建请求，包含条件类型、名称、规则配置
     * @return 创建成功的条件详情
     */
    @PostMapping("/create")
    public PermResult<ConditionResp> createCondition(@Valid @RequestBody ConditionCreateReq req) {
        return PermResult.success(conditionAppService.createCondition(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取权限条件详情
     * <p>
     * 根据条件编码（业务键，T-PERM-029 从内部主键切换）查询条件的完整信息，
     * 包括规则配置和评估逻辑。读取无门禁（条件规则全租户开放、非敏感）。
     * 条件不存在抛 20006。
     * </p>
     *
     * @param req 详情请求，包含条件编码
     * @return 条件详情信息
     */
    @PostMapping("/detail")
    public PermResult<ConditionResp> getCondition(@Valid @RequestBody ConditionDetailReq req) {
        return PermResult.success(conditionAppService.getCondition(TenantContextHolder.getTenantId(), req.conditionCode()));
    }

    /**
     * 查询权限条件列表
     * <p>
     * 返回租户下所有的权限条件列表，无过滤条件。
     * 用于权限配置时选择可用的条件。
     * </p>
     *
     * @param req 空请求，用于保持接口一致性
     * @return 条件列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<ConditionResp>> listConditions(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            conditionAppService.listConditions(TenantContextHolder.getTenantId())
        ));
    }

    /**
     * 删除权限条件
     * <p>
     * 按条件编码集合（业务键，T-PERM-029 从内部主键切换）批量软删除，
     * 会同时处理条件关联的数据；请求中不存在的编码静默跳过。
     * </p>
     *
     * @param req 条件编码集合请求，包含待删除的条件编码列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteCondition(@Valid @RequestBody ConditionRemoveReq req) {
        conditionAppService.deleteConditionsByCodes(TenantContextHolder.getTenantId(), req.codes(), null);
        return PermResult.success();
    }

    /**
     * 更新权限条件信息
     * <p>
     * 更新条件的名称、规则配置等属性。
     * </p>
     *
     * @param req 条件更新请求，以业务键 code 定位，包含要更新的属性值
     * @return 更新后的条件详情
     */
    @PostMapping("/update")
    public PermResult<ConditionResp> updateCondition(@Valid @RequestBody ConditionUpdateReq req) {
        return PermResult.success(conditionAppService.updateCondition(TenantContextHolder.getTenantId(), req, null));
    }
}