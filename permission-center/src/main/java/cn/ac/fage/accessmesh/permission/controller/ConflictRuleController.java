package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.permission.service.ConflictRuleManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限冲突规则管理控制器
 * <p>
 * 提供权限冲突规则的CRUD操作、冲突检测等功能。
 * 权限冲突规则定义了多个角色权限合并时的处理策略。
 * 例如：角色互斥规则、权限合并策略、拒绝优先规则等。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/conflict-rule")
public class ConflictRuleController {

    private final ConflictRuleManageService conflictRuleManageService;

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleManageService 冲突规则管理服务
     */
    public ConflictRuleController(ConflictRuleManageService conflictRuleManageService) {
        this.conflictRuleManageService = conflictRuleManageService;
    }

    /**
     * 创建冲突规则
     * <p>
     * 创建新的权限冲突规则，定义角色组合时的处理策略。
     * 包括角色互斥、权限合并、拒绝优先等规则类型。
     * </p>
     *
     * @param req 冲突规则创建请求，包含规则类型、角色组合、处理策略
     * @return 创建成功的规则详情
     */
    @PostMapping("/create")
    public PermResult<ConflictRuleResp> createConflictRule(@Valid @RequestBody ConflictRuleReq req) {
        return PermResult.success(conflictRuleManageService.createConflictRule(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取冲突规则详情
     * <p>
     * 根据规则ID查询规则的完整信息，包括规则配置和处理策略。
     * </p>
     *
     * @param req ID请求，包含规则ID
     * @return 规则详情信息
     */
    @PostMapping("/detail")
    public PermResult<ConflictRuleResp> getConflictRule(@Valid @RequestBody IdReq req) {
        return PermResult.success(conflictRuleManageService.getConflictRule(TenantContextHolder.getTenantId(), req.id()));
    }

    /**
     * 查询冲突规则列表
     * <p>
     * 返回租户下所有的权限冲突规则列表。
     * 用于查看当前配置的冲突处理策略。
     * </p>
     *
     * @param req 空请求，用于保持接口一致性
     * @return 规则列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<ConflictRuleResp>> listConflictRules(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            conflictRuleManageService.listConflictRules(TenantContextHolder.getTenantId())
        ));
    }

    /**
     * 删除冲突规则
     * <p>
     * 批量删除权限冲突规则。
     * </p>
     *
     * @param req ID集合请求，包含待删除的规则ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteConflictRule(@Valid @RequestBody IdsReq req) {
        conflictRuleManageService.deleteConflictRulesByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 更新冲突规则信息
     * <p>
     * 更新规则的名称、处理策略等属性。
     * </p>
     *
     * @param req 规则更新请求，包含规则ID和新属性值
     * @return 更新后的规则详情
     */
    @PostMapping("/update")
    public PermResult<ConflictRuleResp> updateConflictRule(@Valid @RequestBody ConflictRuleUpdateReq req) {
        return PermResult.success(conflictRuleManageService.updateConflictRule(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 检测权限冲突
     * <p>
     * 检测用户拥有的多个角色之间是否存在权限冲突。
     * 返回冲突检测结果和建议处理方式。
     * </p>
     *
     * @param req 冲突检测请求，包含用户ID和角色ID列表
     * @return 冲突检测结果，包含是否存在冲突、冲突详情
     */
    @PostMapping("/detect")
    public PermResult<ConflictDetectResp> detectConflictRule(@Valid @RequestBody ConflictRuleDetectReq req) {
        return PermResult.success(conflictRuleManageService.detectConflictRule(TenantContextHolder.getTenantId(), req));
    }
}