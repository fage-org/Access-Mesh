package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.OrgUserItemResp;
import cn.ac.fage.accessmesh.access.admin.service.OrgService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织管理控制器
 * <p>
 * 提供组织的CRUD操作、树结构查询、分页查询等功能。
 * 组织用于构建企业的组织架构，用户可以被分配到组织中。
 * 组织可以配置权限策略，实现组织级别的权限控制。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/org")
public class OrgController {

    private final OrgService orgService;

    /**
     * 构造函数注入依赖
     *
     * @param orgService 组织管理服务
     */
    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    /**
     * 创建组织
     * <p>
     * 创建新的组织节点，设置组织名称、父组织、类型等属性。
     * </p>
     *
     * @param req 组织创建请求，包含组织基本信息
     * @return 创建成功的组织ID
     */
    @PostMapping("/create")
    public R<Long> createOrg(@Valid @RequestBody OrgCreateReq req) {
        return R.ok(orgService.createOrg(req));
    }

    /**
     * 更新组织信息
     * <p>
     * 更新组织的名称、类型、状态等属性。
     * </p>
     *
     * @param req 组织更新请求，包含组织ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    public R<Void> updateOrg(@Valid @RequestBody OrgUpdateReq req) {
        orgService.updateOrg(req);
        return R.ok();
    }

    /**
     * 删除组织
     * <p>
     * 删除指定组织，会同时处理子组织和用户关联关系。
     * </p>
     *
     * @param req ID请求，包含组织ID
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    public R<Void> deleteOrg(@Valid @RequestBody IdReq req) {
        orgService.deleteOrg(req.id());
        return R.ok();
    }

    /**
     * 获取组织详情
     * <p>
     * 根据组织ID查询组织的完整信息。
     * </p>
     *
     * @param req ID请求，包含组织ID
     * @return 组织详情信息
     */
    @PostMapping("/detail")
    public R<OrgResp> getOrg(@Valid @RequestBody IdReq req) {
        return R.ok(orgService.getOrg(req.id()));
    }

    /**
     * 分页查询组织列表
     * <p>
     * 支持按组织名称、类型、状态等条件过滤，返回分页结果。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页组织列表结果
     */
    @PostMapping("/page")
    public R<PaginatedResult<OrgResp>> pageOrgs(@Valid @RequestBody OrgPageReq req) {
        return R.ok(orgService.pageOrgs(req));
    }

    /**
     * 查询组织树
     * <p>
     * 返回组织层级树结构（按树配置子树裁剪，默认树为单根），用于管理界面展示组织架构关系。
     * T-ADMIN-021：includePositions=true 返回组织+岗位一体树（岗位按调用者 VIEW_POSITION 后端裁剪）。
     * </p>
     *
     * @param query 组织查询条件（operationCode/treeConfigId/includePositions/orgName/parentOrgId）
     * @return 组织树结构（{items:[...]} 包装）
     */
    @PostMapping("/tree")
    public R<ItemsResp<OrgResp>> treeOrgs(@Valid @RequestBody OrgQuery query) {
        // P1-3：不再返回裸数组，统一 {items:[...]} 包装（复用 perm-common ItemsResp，
        // 与 /role/list、/user-role/list 同款；契约 §4.2.1）
        return R.ok(new ItemsResp<>(orgService.treeOrgs(query)));
    }

    /**
     * 查询组织/岗位下的用户列表
     * <p>
     * 查询指定组织或岗位下通过 user-org 关联的用户。
     * 用于岗位卡片展开后展示已分配用户。
     * </p>
     *
     * @param req ID请求，包含组织或岗位ID
     * @return 用户简要信息列表
     */
    @PostMapping("/users")
    public R<List<OrgUserItemResp>> listOrgUsers(@Valid @RequestBody IdReq req) {
        return R.ok(orgService.listOrgUsers(req.id()));
    }
}