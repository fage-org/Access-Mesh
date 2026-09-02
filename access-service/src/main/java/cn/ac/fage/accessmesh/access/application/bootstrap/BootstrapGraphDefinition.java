package cn.ac.fage.accessmesh.access.application.bootstrap;

import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;

import java.util.List;

/**
 * 空库 bootstrap 固定图定义（T-ACCESS-020，定稿见 access-service-architecture.md §14.2/§14.3/§14.4）。
 * <p>
 * 纯常量：固定租户 1、首管理员与管理用功能角色的稳定业务键、bootstrap 管理 API 清单
 * （含目标接口）、业务门禁最小集、菜单种子（T-FE-015）。幂等三状态检测与单事务创建均以本
 * 定义为唯一事实源，禁止在检测/创建两侧各自维护清单。
 * </p>
 * <p>
 * API 资源 {@code resource_entity(API).code = "{METHOD}:{外部路径}"}（外部路径含 Gateway 路由
 * 前缀 /perm、/admin——Gateway PermissionFilter 以原始请求路径匹配 resource_api_mapping.path_pattern）；
 * 目标接口 {@code POST /admin/role/my-info} 仅预建资源并预授 API:ACCESS+canGrant，不建映射
 * （映射由 E2E 真实创建，T-ACCESS-021）。
 * </p>
 */
public final class BootstrapGraphDefinition {

    private BootstrapGraphDefinition() {}

    /** 首期固定租户（类型种子即租户 1，不做租户开通） */
    public static final Long TENANT_ID = 1L;

    /** 首管理员稳定业务键 */
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_NAME = "Bootstrap Admin";

    /** 管理用功能角色稳定业务键（BASIC_ROLE / 全局域，domainCode=null） */
    public static final String ADMIN_ROLE_TYPE_CODE = "BASIC_ROLE";
    public static final String ADMIN_ROLE_EXTERNAL_ID = "bootstrap-admin";
    public static final String ADMIN_ROLE_NAME = "Bootstrap Admin";

    /** SERVICE 资源（固定图种子对象，DDL 无种子、本地投影不产出；MANAGE_API_MAPPING 已类型级，保留供未来实例级授权） */
    public static final String SERVICE_RESOURCE_TYPE_CODE = ResourceTypeCode.SERVICE;
    public static final String SERVICE_RESOURCE_CODE = "access-service";
    public static final String SERVICE_RESOURCE_NAME = "access-service";

    /**
     * resource_api_mapping.service_code（Gateway 路由 metadata 同值，快照查询按此过滤）与
     * resource_entity.owner_service_code。
     */
    public static final String API_SERVICE_CODE = "access-service";

    /**
     * 默认组织树种子（T-FE-015 设计定案：bootstrap 种默认树——/user/page 与 member-candidates
     * 均为默认树身份目录视图，无默认树配置则用户列表恒空，而树配置创建端点无页面 UI，空库首用死锁）。
     * 根组织 code 为稳定业务键（结构性检测键）；名称为展示值（容忍改名）。
     */
    public static final String DEFAULT_TREE_ROOT_ORG_CODE = "root";
    public static final String DEFAULT_TREE_ROOT_ORG_NAME = "默认组织";
    public static final String DEFAULT_TREE_NAME = "默认组织树";
    /** DDL 注释：tree_type ORG=组织树 / POSITION=职位树 */
    public static final String DEFAULT_TREE_TYPE = "ORG";

    /**
     * bootstrap 管理 API 清单条目。
     *
     * @param method         HTTP 方法
     * @param path           外部路径（Gateway 视角，含 /perm、/admin 前缀）
     * @param name           资源显示名（授权页资源树可见）
     * @param withMapping    是否预建 resource_api_mapping（目标接口 false，映射归 E2E 真实创建）
     * @param grantCanGrant  该 API 实例授权是否携带 canGrant=true（仅目标接口，授权传递用）
     */
    public record ApiRoute(String method, String path, String name,
                           boolean withMapping, boolean grantCanGrant) {}

    /**
     * 业务门禁授权条目（Gateway 层 API:ACCESS 实例授权由 {@link #apiRoutes()} 派生，不在此列）。
     *
     * @param resourceTypeCode 资源类型码
     * @param operationCode    操作码
     * @param resourceCode     实例授权的资源编码；null 表示 scopeAll
     * @param canGrant         是否可转授（bootstrap 固定图业务门禁均不可转授）
     */
    public record GrantSpec(String resourceTypeCode, String operationCode,
                            String resourceCode, boolean canGrant) {}

    /**
     * 菜单种子条目（T-FE-015，2026-08-31 设计定案：bootstrap 幂等种子 + 一次种全部 14 页）。
     *
     * @param menuType     DIR/MENU/EXTERNAL/IFRAME/HIDDEN（种子只用 DIR 与纯展示/业务 MENU）
     * @param displayName  显示名（检测容忍漂移——菜单管理页可改）
     * @param parentPath   父菜单 path（null/顶层）；种子按「先父后子」排序，创建时按 path 已建映射解析
     * @param path         前端静态路由 path（唯一期望键，uk_sys_menu_tenant_path 兜底防重）
     * @param icon         侧栏图标（检测容忍漂移；与前端静态路由同源字符串如 ep/user）
     * @param sortOrder    排序（检测容忍漂移；/auth/user-menu 按 rank 升序下发）
     * @param resourceType 资源挂接类型码；null=纯展示（全员可见，v3.5 §4.1 派生豁免）
     * @param resourceCode 资源实例编码；种子恒 null（类型级挂接，派生命中 scopeAll 授权）
     */
    public record MenuSeed(String menuType, String displayName, String parentPath,
                           String path, String icon, int sortOrder,
                           String resourceType, String resourceCode) {}

    /**
     * bootstrap 管理 API 清单（§14.3；含目标接口，计数以清单本身为准）。
     */
    public static List<ApiRoute> apiRoutes() {
        return List.of(
            new ApiRoute("POST", "/perm/api/perm/abstract-role/tree", "bootstrap:授权页角色树", true, false),
            new ApiRoute("POST", "/perm/api/perm/type-definition/list", "bootstrap:授权页类型定义列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-entity/tree", "bootstrap:授权页资源树", true, false),
            new ApiRoute("POST", "/perm/api/perm/operation-permission/list", "bootstrap:授权页操作列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/permission-condition/list", "bootstrap:授权页条件列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/role-resource-permission/list", "bootstrap:授权页既有授权查询", true, false),
            new ApiRoute("POST", "/perm/api/perm/role-resource-permission/sub-perm-allowed-types", "bootstrap:授权页子权限类型查询", true, false),
            new ApiRoute("POST", "/admin/user/create", "bootstrap:创建用户", true, false),
            new ApiRoute("POST", "/perm/api/perm/abstract-role/create", "bootstrap:创建角色", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-api-mapping/create", "bootstrap:创建API映射", true, false),
            new ApiRoute("POST", "/perm/api/perm/role-resource-permission/apply-grant-plan", "bootstrap:授权与回收", true, false),
            new ApiRoute("POST", "/perm/api/perm/user-role/assign", "bootstrap:分配角色", true, false),
            // T-FE-015：组织与用户页消费端点（Gateway 层逐端点精确注册——未映射路径
            // fail-closed 403，Phase 3 首次真实联调暴露的系统性缺口；端点级粒度对齐
            // 产品 API 级授权能力，后续联调任务按页同样扩展）。/admin/user/create 已在上方清单
            new ApiRoute("POST", "/admin/org-tree-config/page", "bootstrap:组织树配置分页", true, false),
            new ApiRoute("POST", "/admin/org/tree", "bootstrap:组织树查询", true, false),
            new ApiRoute("POST", "/admin/org/page", "bootstrap:组织分页", true, false),
            new ApiRoute("POST", "/admin/org/create", "bootstrap:创建组织", true, false),
            new ApiRoute("POST", "/admin/org/update", "bootstrap:更新组织", true, false),
            new ApiRoute("POST", "/admin/org/delete", "bootstrap:删除组织", true, false),
            new ApiRoute("POST", "/admin/org/users", "bootstrap:组织成员查询", true, false),
            new ApiRoute("POST", "/admin/user/page", "bootstrap:用户分页", true, false),
            new ApiRoute("POST", "/admin/user/update", "bootstrap:更新用户", true, false),
            new ApiRoute("POST", "/admin/user/delete", "bootstrap:删除用户", true, false),
            new ApiRoute("POST", "/admin/user/enable", "bootstrap:用户启停", true, false),
            new ApiRoute("POST", "/admin/user/reset-password", "bootstrap:重置密码", true, false),
            new ApiRoute("POST", "/admin/user/member-candidates", "bootstrap:成员候选查询", true, false),
            new ApiRoute("POST", "/admin/user-org/list", "bootstrap:用户组织查询", true, false),
            new ApiRoute("POST", "/admin/user-org/assign", "bootstrap:分配组织", true, false),
            new ApiRoute("POST", "/admin/user-org/remove", "bootstrap:移除组织关联", true, false),
            new ApiRoute("POST", "/admin/user-org/set-primary", "bootstrap:设置主组织", true, false),
            new ApiRoute("POST", "/admin/user-role/list", "bootstrap:用户角色查询", true, false),
            new ApiRoute("POST", "/perm/api/perm/user-role/revoke", "bootstrap:回收角色", true, false),
            new ApiRoute("POST", "/admin/role/list", "bootstrap:功能角色列表", true, false),
            // T-FE-016：角色管理页消费端点（tree/create 已在上方清单）——update/remove/move
            // 写路径 + detail 编辑回显（树节点无 extra 字段，编辑表单按业务键拉 detail 回填，
            // role-manage.md §8 既定路径）。list 端点本页不消费（冲突规则页 T-FE-020 届时注册）
            new ApiRoute("POST", "/perm/api/perm/abstract-role/update", "bootstrap:更新角色", true, false),
            new ApiRoute("POST", "/perm/api/perm/abstract-role/remove", "bootstrap:删除角色", true, false),
            new ApiRoute("POST", "/perm/api/perm/abstract-role/move", "bootstrap:移动角色", true, false),
            new ApiRoute("POST", "/perm/api/perm/abstract-role/detail", "bootstrap:角色详情", true, false),
            // T-FE-017：资源与操作定义页消费端点（tree、operation-permission/list 已在上方清单）。
            // resource-entity/detail 编辑回显（树节点无 extra，按业务键拉 detail）；operation detail
            // 与 resource list 本页不消费不注册（后续消费页按页注册）；remove 为批量端点（items 集合）
            new ApiRoute("POST", "/perm/api/perm/resource-entity/detail", "bootstrap:资源详情", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-entity/create", "bootstrap:创建资源", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-entity/update", "bootstrap:更新资源", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-entity/move", "bootstrap:移动资源", true, false),
            new ApiRoute("POST", "/perm/api/perm/resource-entity/remove", "bootstrap:删除资源", true, false),
            new ApiRoute("POST", "/perm/api/perm/operation-permission/create", "bootstrap:创建操作权限", true, false),
            new ApiRoute("POST", "/perm/api/perm/operation-permission/update", "bootstrap:更新操作权限", true, false),
            new ApiRoute("POST", "/perm/api/perm/operation-permission/remove", "bootstrap:删除操作权限", true, false),
            // T-FE-020：条件与冲突规则页消费端点（condition/list、type-definition/list、
            // operation-permission/list 已在上方清单）。condition/detail 页面不消费不注册；
            // abstract-role/list 为冲突规则页角色选择器消费（T-FE-016 登记的届时注册事项）；
            // 业务门禁零新增（CONFLICT_RULE 四档与 CONDITION 写三档已在图，T-PERM-029/030 预置）
            new ApiRoute("POST", "/perm/api/perm/abstract-role/list", "bootstrap:冲突规则页角色列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/permission-condition/create", "bootstrap:创建条件", true, false),
            new ApiRoute("POST", "/perm/api/perm/permission-condition/update", "bootstrap:更新条件", true, false),
            new ApiRoute("POST", "/perm/api/perm/permission-condition/remove", "bootstrap:删除条件", true, false),
            new ApiRoute("POST", "/perm/api/perm/conflict-rule/list", "bootstrap:冲突规则列表", true, false),
            new ApiRoute("POST", "/perm/api/perm/conflict-rule/create", "bootstrap:创建冲突规则", true, false),
            new ApiRoute("POST", "/perm/api/perm/conflict-rule/update", "bootstrap:更新冲突规则", true, false),
            new ApiRoute("POST", "/perm/api/perm/conflict-rule/remove", "bootstrap:删除冲突规则", true, false),
            new ApiRoute("POST", "/perm/api/perm/conflict-rule/detect", "bootstrap:冲突检测", true, false),
            // 目标接口（§14.6）：仅预建资源 + API:ACCESS+canGrant，不建映射
            new ApiRoute("POST", "/admin/role/my-info", "bootstrap:目标接口(my-info)", false, true));
    }

    /**
     * 业务门禁最小集（§14.4；ROLE:MANAGE 掩码已含 VIEW，不重复授 ROLE:VIEW）。
     */
    public static List<GrantSpec> businessGrants() {
        return List.of(
            new GrantSpec(ResourceTypeCode.USER, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.ROLE, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.ROLE, OperationCodeConstants.MANAGE, null, false),
            // T-API-001：类型级（scopeAll）——接入新服务（如 example-service）的首条 API 映射
            // 创建必须由首管理员完成，实例级会造成鸡生蛋（无正规入口补授新服务实例）
            new GrantSpec(ResourceTypeCode.SERVICE, OperationCodeConstants.MANAGE_API_MAPPING,
                null, false),
            // T-PERM-027：服务与接口映射页门禁——checkCanGrant 要求操作者先持有才能转授，
            // 固定图不持 SERVICE:VIEW/MANAGE/SYNC_INTERFACE 则空库上该页读写路径无授予起点
            // （死锁，同 DOMAIN:VIEW 先例）。死锁防护=持有解锁首管理员页面读写；三条与全部
            // 业务门禁同口径不可转授（转授链仅 API:ACCESS），实例粒度由租户后续自行收紧
            new GrantSpec(ResourceTypeCode.SERVICE, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.SERVICE, OperationCodeConstants.MANAGE, null, false),
            new GrantSpec(ResourceTypeCode.SERVICE, OperationCodeConstants.SYNC_INTERFACE, null, false),
            new GrantSpec(ResourceTypeCode.TYPE_DEFINITION, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.RESOURCE, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.OPERATION, OperationCodeConstants.VIEW, null, false),
            // T-FE-017：资源与操作定义页写门禁——固定图不持 CREATE/MANAGE 则空库上
            // 该页写路径无授予起点（死锁，同 T-PERM-027 先例）。RESOURCE 的 update/move/
            // remove 为实例级校验，类型级 scopeAll 覆盖（实例粒度由租户后续自行收紧）
            new GrantSpec(ResourceTypeCode.RESOURCE, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.RESOURCE, OperationCodeConstants.MANAGE, null, false),
            new GrantSpec(ResourceTypeCode.OPERATION, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.OPERATION, OperationCodeConstants.MANAGE, null, false),
            // T-PERM-025 审计分离：操作日志查询独立门禁——新权限码需固定图持否则无授予起点（死锁）
            new GrantSpec(ResourceTypeCode.OPERATION_LOG, OperationCodeConstants.VIEW, null, false),
            // T-PERM-032 审计分离：变更日志页查询门禁（对齐 OPERATION_LOG 先例）
            new GrantSpec(ResourceTypeCode.PERMISSION_CHANGE_LOG, OperationCodeConstants.VIEW, null, false),
            // T-PERM-026：业务域页读门禁——checkCanGrant 要求操作者先持有才能转授，
            // 固定图不持 DOMAIN:VIEW 则空库上业务域页读路径无授予起点（死锁，同 OPERATION_LOG 先例）
            new GrantSpec(ResourceTypeCode.DOMAIN, OperationCodeConstants.VIEW, null, false),
            // T-PERM-030：冲突规则页读写四档——固定图不持则空库上该页读写路径无授予起点
            // （死锁，同 DOMAIN 先例）。读取（list/detail/detect）亦有 VIEW 门禁，
            // 故 VIEW 与写三档同补；不可转授与全部业务门禁同口径
            new GrantSpec(ResourceTypeCode.CONFLICT_RULE, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.CONFLICT_RULE, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.CONFLICT_RULE, OperationCodeConstants.UPDATE, null, false),
            new GrantSpec(ResourceTypeCode.CONFLICT_RULE, OperationCodeConstants.DELETE, null, false),
            // T-PERM-030 顺带补授（T-PERM-029 遗漏）：条件页写门禁三档类型级——固定图不持则
            // 空库上条件页写路径与 CONDITION:* 转授无授予起点（死锁，同款机制）。
            // 读取无门禁（2026-08-08 产品确认条件全租户开放），故无 VIEW 条目
            new GrantSpec(ResourceTypeCode.CONDITION, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.CONDITION, OperationCodeConstants.UPDATE, null, false),
            new GrantSpec(ResourceTypeCode.CONDITION, OperationCodeConstants.DELETE, null, false),
            // T-PERM-031：资源依赖页读写五档（VIEW/CREATE/UPDATE/DELETE/SYNC）——固定图不持则
            // 空库上该页读写路径无授予起点（死锁，同 DOMAIN/CONFLICT_RULE 先例）。
            // SYNC 随四档同补：batch-sync 端点存在且门禁为 SYNC，前端 P0 未接入不改变端点门禁事实；
            // 不可转授与全部业务门禁同口径（转授链仅 API:ACCESS）
            new GrantSpec(ResourceTypeCode.DEPENDENCY, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.DEPENDENCY, OperationCodeConstants.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.DEPENDENCY, OperationCodeConstants.UPDATE, null, false),
            new GrantSpec(ResourceTypeCode.DEPENDENCY, OperationCodeConstants.DELETE, null, false),
            new GrantSpec(ResourceTypeCode.DEPENDENCY, OperationCodeConstants.SYNC, null, false),
            // T-FE-015：组织与用户页读写门禁全档——固定图不持则空库上该页读写路径无授予起点
            // （死锁，同 DEPENDENCY 先例；菜单种子挂 ORG 资源类型走 v3.5 派生同样要求先持有）。
            // ORG 系/USER 系操作码取 AdminOperationCode（admin 域门禁常量；DDL 扩展码组 L855-865 全有种子），
            // 不可转授与全部业务门禁同口径（转授链仅 API:ACCESS）
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.CREATE, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.UPDATE, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.DELETE, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.MANAGE_MEMBER, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.VIEW_POSITION, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.CREATE_POSITION, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.UPDATE_POSITION, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.DELETE_POSITION, null, false),
            new GrantSpec(ResourceTypeCode.ORG, AdminOperationCode.ASSIGN_POSITION_USER, null, false),
            // USER:CREATE 已在图（L98）；此处补页面读写全档（ENABLE/RESET_PASSWORD DDL L864-865 有种子）
            new GrantSpec(ResourceTypeCode.USER, AdminOperationCode.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.USER, AdminOperationCode.UPDATE, null, false),
            new GrantSpec(ResourceTypeCode.USER, AdminOperationCode.DELETE, null, false),
            new GrantSpec(ResourceTypeCode.USER, AdminOperationCode.ENABLE, null, false),
            new GrantSpec(ResourceTypeCode.USER, AdminOperationCode.RESET_PASSWORD, null, false),
            // T-FE-015：系统配置页读写门禁（T-PERM-024 收口 SYSTEM_CONFIG:VIEW/MANAGE）——菜单种子挂
            // SYSTEM_CONFIG 类型走派生则管理员必须先持有其操作位，否则菜单行恒不可见（死锁同款，
            // T-FE-022 联调同样受益；MANAGE 为 DDL 运行时必需码组 L895 既有种子）
            new GrantSpec(ResourceTypeCode.SYSTEM_CONFIG, OperationCodeConstants.VIEW, null, false),
            new GrantSpec(ResourceTypeCode.SYSTEM_CONFIG, OperationCodeConstants.MANAGE, null, false),
            // T-API-001：类型级 API:ACCESS + canGrant——新接入服务接口的授权必须由首管理员完成，
            // 实例级（仅清单内管理接口）会造成鸡生蛋（无正规入口给新接口授权）。
            // ACCESS 为网关接口鉴权专用操作码（api-contract/DDL 运行时种子），此处按契约字符串声明
            new GrantSpec(ResourceTypeCode.API, "ACCESS", null, true));
    }

    /**
     * Gateway 层实例级 API:ACCESS 授权（由管理 API 清单派生）。
     * ACCESS 为网关接口鉴权专用操作码（api-contract/DDL 运行时种子），OperationCodeConstants
     * 未收录该码，此处按契约字符串声明。
     */
    public static List<GrantSpec> apiAccessGrants() {
        return apiRoutes().stream()
            .map(route -> new GrantSpec(ResourceTypeCode.API, "ACCESS",
                apiResourceCode(route.method(), route.path()), route.grantCanGrant()))
            .toList();
    }

    /**
     * 菜单种子（T-FE-015，2026-08-31 设计定案：一次种全部 14 页——13 个系统子页 + welcome 首页；
     * 权限授予页不进菜单，入口为角色管理页按钮，属交互入口设计非导航收敛）。
     * <p>
     * 「长期隐藏 9 页」的 T-FE-041 导航收敛口径随本定案放开：菜单可见性改由 v3.5 §4.1 ∃op
     * 派生控制（admin 持全档可见、普通用户无授权不可见），静态路由 showLink 不再控制侧栏。
     * 资源挂接单挂页面主资源类型；权限条件页读取全租户开放（2026-08-08 产品确认）故挂纯展示；
     * 权限排查页门禁为 USER:VIEW 或 ROLE:VIEW 任一命中，单挂取 USER（派生无法表达或语义）。
     * 侧栏点击按 path 跳前端静态路由，路由注册不变、直达 URL 仍可达、后端 403 兜底维持。
     * </p>
     */
    public static List<MenuSeed> menuSeeds() {
        return List.of(
            // 首页（纯展示，全员可见）
            new MenuSeed("MENU", "首页", null, "/welcome", "ep/home-filled", 0, null, null),
            // 系统管理目录（DIR 恒候选可见，子全剪则父剪）
            new MenuSeed("DIR", "系统管理", null, "/system", "ep/setting", 10, null, null),
            new MenuSeed("MENU", "组织与用户", "/system", "/system/user", "ep/user", 1, ResourceTypeCode.ORG, null),
            new MenuSeed("MENU", "角色管理", "/system", "/system/role", "ep/user-filled", 2, ResourceTypeCode.ROLE, null),
            new MenuSeed("MENU", "类型定义", "/system", "/system/type-def", "ep/files", 3, ResourceTypeCode.TYPE_DEFINITION, null),
            new MenuSeed("MENU", "系统配置", "/system", "/system/config", "ep/tools", 4, ResourceTypeCode.SYSTEM_CONFIG, null),
            new MenuSeed("MENU", "操作日志", "/system", "/system/operation-log", "ep/document", 5, ResourceTypeCode.OPERATION_LOG, null),
            new MenuSeed("MENU", "业务域", "/system", "/system/biz-domain", "ep/office-building", 6, ResourceTypeCode.DOMAIN, null),
            new MenuSeed("MENU", "服务与接口", "/system", "/system/service-interface", "ep/connection", 7, ResourceTypeCode.SERVICE, null),
            new MenuSeed("MENU", "资源与操作", "/system", "/system/resource-operation", "ep/coins", 8, ResourceTypeCode.RESOURCE, null),
            // 权限条件：读取全租户开放 → 纯展示（菜单全员可见与读语义一致；写按钮由页面 hasPerms 门控）
            new MenuSeed("MENU", "权限条件", "/system", "/system/permission-condition", "ep/key", 9, null, null),
            new MenuSeed("MENU", "冲突规则", "/system", "/system/conflict-rule", "ep/warn-triangle-filled", 10, ResourceTypeCode.CONFLICT_RULE, null),
            new MenuSeed("MENU", "资源依赖", "/system", "/system/resource-dependency", "ep/share", 11, ResourceTypeCode.DEPENDENCY, null),
            new MenuSeed("MENU", "权限变更日志", "/system", "/system/permission-change-log", "ep/history", 12, ResourceTypeCode.PERMISSION_CHANGE_LOG, null),
            // 权限排查：页面门 = USER:VIEW 或 ROLE:VIEW 任一（T-PERM-033 定案），派生单挂取 USER
            new MenuSeed("MENU", "权限排查", "/system", "/system/permission-query", "ep/key", 13, ResourceTypeCode.USER, null));
    }

    /** 全部固定图授权（业务门禁 + 实例级 API:ACCESS；计数以 AccessBootstrapPgIT 断言为准） */
    public static List<GrantSpec> allGrants() {
        return java.util.stream.Stream.concat(businessGrants().stream(), apiAccessGrants().stream()).toList();
    }

    /** API 资源稳定业务键 */
    public static String apiResourceCode(String method, String path) {
        return method + ":" + path;
    }
}
