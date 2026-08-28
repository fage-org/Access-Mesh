package cn.ac.fage.accessmesh.access.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 契约快照测试（T-ACCESS-011 验收 3：路径 / POST-only / 统一响应 / DTO 签名）。
 * <p>
 * 快照基线：扫描全部 Controller 注解并与两份权威契约文档及「归并前代码路径」
 * （git 5f1e65dd5^）双向核对——归并后仅减少有设计决策背书的路径
 * （/sync-task/*：T-ACCESS-005 退役；/audit-log/page：
 * T-ACCESS-007 确认零引用后删除）；T-PERM-043 再删 extra-roles/*；
 * T-ADMIN-024 再删 admin 侧角色写代理（/role/create、/role/grant-menu、
 * /role/revoke-menu、/user-role/assign、/user-role/revoke，无存量调用方）；
 * T-PERM-034 再删 role-resource-permission 旧写入口（save/revoke/children/
 * add-child/remove-child，2026-08-27 设计定案，apply-grant-plan 为唯一写入口）；T-PERM-025 增 action-options。计数不写注释（去计数化）。
 * </p>
 * <p>
 * 契约断言封闭口径（评审修复：堵住空 method 数组与 path()[0] 逃逸）：
 * POST-only 按「方法必须标注 @PostMapping」判定（未限定方法的 @RequestMapping 违规）；
 * 路径枚举覆盖映射的全部 path 值（多路径映射不漏检）。
 * 统一响应：除 /file/download（二进制文件流）外，全部返回 PermResult 包装。
 * DTO 签名：路径 → 请求体类型 | 响应泛型类型 快照精确比对（类型级漂移检测；
 * 字段级漂移由既有分散 DTO 测试与后续 T-PERM 任务覆盖，为登记限制）。
 * </p>
 */
class HttpApiPathSnapshotTest {

    private static final String BASE_PACKAGE = "cn.ac.fage.accessmesh.access";

    /** 归并后全量路径快照（POST + JSON Body；外部路径经 Gateway /admin、/perm StripPrefix=1 到达）。 */
    private static final Set<String> EXPECTED_PATHS = Set.of("""
/api/perm/abstract-role/create
/api/perm/abstract-role/detail
/api/perm/abstract-role/full-sync
/api/perm/abstract-role/list
/api/perm/abstract-role/move
/api/perm/abstract-role/remove
/api/perm/abstract-role/sync
/api/perm/abstract-role/tree
/api/perm/abstract-role/update
/api/perm/abstract-user/create
/api/perm/abstract-user/detail
/api/perm/abstract-user/full-sync
/api/perm/abstract-user/list
/api/perm/abstract-user/remove
/api/perm/abstract-user/sync
/api/perm/abstract-user/update
/api/perm/auth/batch-check
/api/perm/auth/check
/api/perm/auth/check-interface
/api/perm/auth/interface-snapshot
/api/perm/auth/query-permission-tree
/api/perm/auth/query-resources
/api/perm/auth/query-scopes
/api/perm/biz-domain/create
/api/perm/biz-domain/detail
/api/perm/biz-domain/list
/api/perm/biz-domain/remove
/api/perm/biz-domain/update
/api/perm/conflict-rule/create
/api/perm/conflict-rule/detail
/api/perm/conflict-rule/detect
/api/perm/conflict-rule/list
/api/perm/conflict-rule/remove
/api/perm/conflict-rule/update
/api/perm/domain-config/detail
/api/perm/domain-config/list
/api/perm/domain-config/remove
/api/perm/domain-config/save
/api/perm/log/change/list
/api/perm/log/operation/action-options
/api/perm/log/operation/list
/api/perm/operation-permission/create
/api/perm/operation-permission/detail
/api/perm/operation-permission/list
/api/perm/operation-permission/remove
/api/perm/operation-permission/update
/api/perm/permission-condition/create
/api/perm/permission-condition/detail
/api/perm/permission-condition/list
/api/perm/permission-condition/remove
/api/perm/permission-condition/update
/api/perm/permission-view/effective-permission-codes
/api/perm/permission-view/effective-permissions
/api/perm/permission-view/effective-roles
/api/perm/permission-view/explain
/api/perm/permission-view/recent-changes
/api/perm/permission-view/resource-tree
/api/perm/permission-view/resource-users
/api/perm/permission-view/role-permissions
/api/perm/resource-api-mapping/create
/api/perm/resource-api-mapping/list
/api/perm/resource-api-mapping/remove
/api/perm/resource-api-mapping/update
/api/perm/resource-dependency/batch-sync
/api/perm/resource-dependency/check
/api/perm/resource-dependency/create
/api/perm/resource-dependency/graph
/api/perm/resource-dependency/list
/api/perm/resource-dependency/remove
/api/perm/resource-dependency/update
/api/perm/resource-entity/batch-create
/api/perm/resource-entity/create
/api/perm/resource-entity/detail
/api/perm/resource-entity/full-sync
/api/perm/resource-entity/list
/api/perm/resource-entity/move
/api/perm/resource-entity/remove
/api/perm/resource-entity/sync
/api/perm/resource-entity/tree
/api/perm/resource-entity/update
/api/perm/role-resource-permission/apply-grant-plan
/api/perm/role-resource-permission/list
/api/perm/service-config/apis
/api/perm/service-config/detail
/api/perm/service-config/list
/api/perm/service-config/remove
/api/perm/service-config/save
/api/perm/service-config/sync
/api/perm/system-config/detail
/api/perm/system-config/list
/api/perm/system-config/save
/api/perm/type-definition/create
/api/perm/type-definition/detail
/api/perm/type-definition/list
/api/perm/type-definition/remove
/api/perm/type-definition/update
/api/perm/user-role/assign
/api/perm/user-role/batch-assign
/api/perm/user-role/full-sync
/api/perm/user-role/list
/api/perm/user-role/revoke
/api/perm/user-role/sync
/auth/captcha
/auth/login
/auth/login/sms
/auth/logout
/auth/oauth2/authorize
/auth/oauth2/refresh
/auth/oauth2/revoke
/auth/oauth2/token
/auth/oauth2/userinfo
/auth/user-menu
/auth/userinfo
/config/delete
/config/detail
/config/page
/config/update
/dict/data/create
/dict/data/delete
/dict/data/list
/dict/data/update
/dict/type/create
/dict/type/delete
/dict/type/list
/dict/type/page
/file/delete
/file/detail
/file/download
/file/page
/file/upload
/job/create
/job/delete
/job/detail
/job/log/page
/job/page
/job/toggle
/job/trigger
/job/update
/login-log/page
/menu/create
/menu/delete
/menu/detail
/menu/tree
/menu/update
/notice/create
/notice/delete
/notice/detail
/notice/my-notices
/notice/page
/notice/publish
/notice/read
/notice/update
/oauth2/client/create
/oauth2/client/delete
/oauth2/client/detail
/oauth2/client/page
/oauth2/client/update
/org-tree-config/create
/org-tree-config/delete
/org-tree-config/detail
/org-tree-config/page
/org-tree-config/set-default
/org-tree-config/update
/org/create
/org/delete
/org/detail
/org/page
/org/tree
/org/update
/org/users
/role/list
/role/my-info
/user-org/assign
/user-org/list
/user-org/remove
/user-org/set-primary
/user-role/list
/user/create
/user/delete
/user/detail
/user/enable
/user/member-candidates
/user/page
/user/reset-password
/user/update
/user/user-menus
""".strip().split("\n"));

    /** 路径 → 请求体类型 | 响应类型 签名快照（类型级 DTO 契约；条数与 Controller 扫描强制一致）。 */
    private static final Set<String> EXPECTED_SIGNATURES = Set.of("""
/api/perm/abstract-role/create|access.permission.dto.req.RoleCreateReq|common.model.PermResult<access.permission.dto.resp.RoleResp>
/api/perm/abstract-role/detail|access.permission.dto.req.RoleDetailReq|common.model.PermResult<access.permission.dto.resp.RoleResp>
/api/perm/abstract-role/full-sync|access.permission.dto.req.AbstractRoleFullSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/abstract-role/list|access.permission.dto.req.RoleListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.RoleResp>>
/api/perm/abstract-role/move|access.permission.dto.req.RoleMoveReq|common.model.PermResult<Void>
/api/perm/abstract-role/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/abstract-role/sync|access.permission.dto.req.AbstractRoleSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/abstract-role/tree|access.permission.dto.req.RoleTreeReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.RoleTreeResp>>
/api/perm/abstract-role/update|access.permission.dto.req.RoleUpdateReq|common.model.PermResult<access.permission.dto.resp.RoleResp>
/api/perm/abstract-user/create|access.permission.dto.req.UserCreateReq|common.model.PermResult<access.permission.dto.resp.UserResp>
/api/perm/abstract-user/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.UserResp>
/api/perm/abstract-user/full-sync|access.permission.dto.req.AbstractUserFullSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/abstract-user/list|access.permission.dto.req.UserListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.UserResp>>
/api/perm/abstract-user/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/abstract-user/sync|access.permission.dto.req.AbstractUserSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/abstract-user/update|access.permission.dto.req.UserUpdateReq|common.model.PermResult<access.permission.dto.resp.UserResp>
/api/perm/auth/batch-check|access.permission.dto.req.BatchAuthCheckReq|common.model.PermResult<access.permission.dto.resp.BatchAuthCheckResp>
/api/perm/auth/check-interface|access.permission.dto.req.CheckInterfaceReq|common.model.PermResult<access.permission.dto.resp.CheckInterfaceResp>
/api/perm/auth/check|access.permission.dto.req.AuthCheckReq|common.model.PermResult<access.permission.dto.resp.AuthCheckResp>
/api/perm/auth/interface-snapshot|perm.common.dto.req.InterfaceSnapshotReq|common.model.PermResult<perm.common.dto.resp.InterfaceSnapshotResp>
/api/perm/auth/query-permission-tree|access.permission.dto.req.PermissionTreeReq|common.model.PermResult<access.permission.dto.resp.PermissionTreeResp>
/api/perm/auth/query-resources|access.permission.dto.req.QueryResourcesReq|common.model.PermResult<access.permission.dto.resp.QueryResourcesResp>
/api/perm/auth/query-scopes|access.permission.dto.req.QueryScopesReq|common.model.PermResult<access.permission.dto.resp.QueryScopesResp>
/api/perm/biz-domain/create|access.permission.dto.req.BizDomainCreateReq|common.model.PermResult<access.permission.dto.resp.BizDomainResp>
/api/perm/biz-domain/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.BizDomainResp>
/api/perm/biz-domain/list|access.permission.dto.req.EmptyReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.BizDomainResp>>
/api/perm/biz-domain/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/biz-domain/update|access.permission.dto.req.BizDomainUpdateReq|common.model.PermResult<access.permission.dto.resp.BizDomainResp>
/api/perm/conflict-rule/create|access.permission.dto.req.ConflictRuleReq|common.model.PermResult<access.permission.dto.resp.ConflictRuleResp>
/api/perm/conflict-rule/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.ConflictRuleResp>
/api/perm/conflict-rule/detect|access.permission.dto.req.ConflictRuleDetectReq|common.model.PermResult<access.permission.dto.resp.ConflictDetectResp>
/api/perm/conflict-rule/list|access.permission.dto.req.EmptyReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ConflictRuleResp>>
/api/perm/conflict-rule/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/conflict-rule/update|access.permission.dto.req.ConflictRuleUpdateReq|common.model.PermResult<access.permission.dto.resp.ConflictRuleResp>
/api/perm/domain-config/detail|access.permission.dto.req.DomainConfigGetReq|common.model.PermResult<access.permission.dto.resp.DomainConfigResp>
/api/perm/domain-config/list|access.permission.dto.req.DomainConfigListReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.DomainConfigResp>>
/api/perm/domain-config/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/domain-config/save|access.permission.dto.req.DomainConfigReq|common.model.PermResult<access.permission.dto.resp.DomainConfigResp>
/api/perm/log/change/list|access.permission.dto.req.ChangeLogListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.ChangeLogResp>>
/api/perm/log/operation/action-options|access.permission.dto.req.LogActionOptionsReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<String>>
/api/perm/log/operation/list|access.permission.dto.req.OperationLogListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.OperationLogResp>>
/api/perm/operation-permission/create|access.permission.dto.req.OperationCreateReq|common.model.PermResult<access.permission.dto.resp.OperationPermissionResp>
/api/perm/operation-permission/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.OperationPermissionResp>
/api/perm/operation-permission/list|access.permission.dto.req.OperationListReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.OperationPermissionResp>>
/api/perm/operation-permission/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/operation-permission/update|access.permission.dto.req.OperationUpdateReq|common.model.PermResult<access.permission.dto.resp.OperationPermissionResp>
/api/perm/permission-condition/create|access.permission.dto.req.ConditionCreateReq|common.model.PermResult<access.permission.dto.resp.ConditionResp>
/api/perm/permission-condition/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.ConditionResp>
/api/perm/permission-condition/list|access.permission.dto.req.EmptyReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ConditionResp>>
/api/perm/permission-condition/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/permission-condition/update|access.permission.dto.req.ConditionUpdateReq|common.model.PermResult<access.permission.dto.resp.ConditionResp>
/api/perm/permission-view/effective-permission-codes|perm.common.dto.req.UserEffectivePermissionCodesReq|common.model.PermResult<perm.common.dto.resp.UserEffectivePermissionCodesResp>
/api/perm/permission-view/effective-permissions|access.permission.dto.req.UserPermissionViewReq|common.model.PermResult<access.permission.dto.resp.PermissionEffectivePermissionsResp>
/api/perm/permission-view/effective-roles|access.permission.dto.req.UserEffectiveRolesReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.EffectiveRoleResp>>
/api/perm/permission-view/explain|access.permission.dto.req.PermissionExplainReq|common.model.PermResult<access.permission.dto.resp.PermissionExplainResp>
/api/perm/permission-view/recent-changes|access.permission.dto.req.PermissionRecentChangesReq|common.model.PermResult<access.permission.dto.resp.PermissionRecentChangesResp>
/api/perm/permission-view/resource-tree|access.permission.dto.req.UserResourceTreeReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ResourcePermissionTreeResp>>
/api/perm/permission-view/resource-users|access.permission.dto.req.ResourcePermissionViewReq|common.model.PermResult<access.permission.dto.resp.ResourcePermissionViewResp>
/api/perm/permission-view/role-permissions|access.permission.dto.req.RolePermissionViewReq|common.model.PermResult<access.permission.dto.resp.RolePermissionViewResp>
/api/perm/resource-api-mapping/create|access.permission.dto.req.ApiMappingAddReq|common.model.PermResult<access.permission.dto.resp.ApiMappingResp>
/api/perm/resource-api-mapping/list|access.permission.dto.req.ApiMappingListReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ApiMappingResp>>
/api/perm/resource-api-mapping/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/resource-api-mapping/update|access.permission.dto.req.ApiMappingUpdateReq|common.model.PermResult<access.permission.dto.resp.ApiMappingResp>
/api/perm/resource-dependency/batch-sync|access.permission.dto.req.DependencyBatchSyncReq|common.model.PermResult<Void>
/api/perm/resource-dependency/check|access.permission.dto.req.ResourceDependencyCheckReq|common.model.PermResult<access.permission.dto.resp.DependencyCycleCheckResp>
/api/perm/resource-dependency/create|access.permission.dto.req.ResourceDependencyCreateReq|common.model.PermResult<access.permission.dto.resp.ResourceDependencyResp>
/api/perm/resource-dependency/graph|access.permission.dto.req.DependencyListReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ResourceDependencyResp>>
/api/perm/resource-dependency/list|access.permission.dto.req.DependencyListReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ResourceDependencyResp>>
/api/perm/resource-dependency/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/resource-dependency/update|access.permission.dto.req.ResourceDependencyUpdateReq|common.model.PermResult<access.permission.dto.resp.ResourceDependencyResp>
/api/perm/resource-entity/batch-create|access.permission.dto.req.ResourceBatchCreateReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ResourceResp>>
/api/perm/resource-entity/create|access.permission.dto.req.ResourceCreateReq|common.model.PermResult<access.permission.dto.resp.ResourceResp>
/api/perm/resource-entity/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.ResourceResp>
/api/perm/resource-entity/full-sync|access.permission.dto.req.ResourceEntityFullSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/resource-entity/list|access.permission.dto.req.ResourceListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.ResourceResp>>
/api/perm/resource-entity/move|access.permission.dto.req.ResourceMoveReq|common.model.PermResult<Void>
/api/perm/resource-entity/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/resource-entity/sync|access.permission.dto.req.ResourceEntitySyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/resource-entity/tree|access.permission.dto.req.ResourceTreeReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ResourceTreeResp>>
/api/perm/resource-entity/update|access.permission.dto.req.ResourceUpdateReq|common.model.PermResult<access.permission.dto.resp.ResourceResp>
/api/perm/role-resource-permission/apply-grant-plan|access.permission.dto.req.ApplyGrantPlanReq|common.model.PermResult<access.permission.dto.resp.RolePermissionItemsResp>
/api/perm/role-resource-permission/list|access.permission.dto.req.RolePermissionListReq|common.model.PermResult<access.permission.dto.resp.RolePermissionItemsResp>
/api/perm/service-config/apis|access.permission.dto.req.ServiceConfigApisReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ApiMappingResp>>
/api/perm/service-config/detail|access.permission.dto.req.ServiceConfigGetReq|common.model.PermResult<access.permission.dto.resp.ServiceConfigResp>
/api/perm/service-config/list|access.permission.dto.req.EmptyReq|common.model.PermResult<access.permission.dto.resp.ItemsResp<access.permission.dto.resp.ServiceConfigResp>>
/api/perm/service-config/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/service-config/save|access.permission.dto.req.ServiceConfigReq|common.model.PermResult<access.permission.dto.resp.ServiceConfigResp>
/api/perm/service-config/sync|access.permission.dto.req.ServiceConfigSyncReq|common.model.PermResult<access.permission.dto.resp.ServiceConfigSyncResp>
/api/perm/system-config/detail|access.permission.dto.req.SystemConfigGetReq|common.model.PermResult<access.permission.dto.resp.SystemConfigResp>
/api/perm/system-config/list|access.permission.dto.req.SystemConfigListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.SystemConfigResp>>
/api/perm/system-config/save|access.permission.dto.req.SystemConfigReq|common.model.PermResult<access.permission.dto.resp.SystemConfigResp>
/api/perm/type-definition/create|access.permission.dto.req.TypeCreateReq|common.model.PermResult<access.permission.dto.resp.TypeDefinitionResp>
/api/perm/type-definition/detail|access.permission.dto.req.IdReq|common.model.PermResult<access.permission.dto.resp.TypeDefinitionResp>
/api/perm/type-definition/list|access.permission.dto.req.TypeListReq|common.model.PermResult<access.permission.dto.resp.PaginatedResp<access.permission.dto.resp.TypeDefinitionResp>>
/api/perm/type-definition/remove|access.permission.dto.req.IdsReq|common.model.PermResult<Void>
/api/perm/type-definition/update|access.permission.dto.req.TypeUpdateReq|common.model.PermResult<access.permission.dto.resp.TypeDefinitionResp>
/api/perm/user-role/assign|access.permission.dto.req.UserAssignRoleReq|common.model.PermResult<Void>
/api/perm/user-role/batch-assign|access.permission.dto.req.UserRoleBatchAssignReq|common.model.PermResult<Void>
/api/perm/user-role/full-sync|access.permission.dto.req.UserRoleFullSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/api/perm/user-role/list|access.permission.dto.req.UserRoleListReq|common.model.PermResult<access.permission.dto.resp.UserRolesResp>
/api/perm/user-role/revoke|access.permission.dto.req.UserRoleBatchRevokeReq|common.model.PermResult<Void>
/api/perm/user-role/sync|access.permission.dto.req.UserRoleSyncReq|common.model.PermResult<perm.common.dto.resp.SyncResultResp>
/auth/captcha|-|common.model.PermResult<access.admin.dto.auth.CaptchaResp>
/auth/login/sms|access.admin.dto.auth.SmsLoginReq|common.model.PermResult<access.admin.dto.auth.LoginResp>
/auth/login|access.admin.dto.auth.LoginReq|common.model.PermResult<access.admin.dto.auth.LoginResp>
/auth/logout|-|common.model.PermResult<Void>
/auth/oauth2/authorize|access.admin.dto.oauth2.AuthorizeReq|common.model.PermResult<access.admin.dto.oauth2.AuthorizeResp>
/auth/oauth2/refresh|access.admin.controller.OAuth2Controller$RefreshTokenReq|common.model.PermResult<access.admin.dto.oauth2.TokenResp>
/auth/oauth2/revoke|access.admin.controller.OAuth2Controller$RevokeTokenReq|common.model.PermResult<Void>
/auth/oauth2/token|access.admin.dto.oauth2.TokenReq|common.model.PermResult<access.admin.dto.oauth2.TokenResp>
/auth/oauth2/userinfo|-|common.model.PermResult<access.admin.dto.oauth2.OAuth2UserInfoResp>
/auth/user-menu|-|common.model.PermResult<access.admin.dto.auth.UserMenuResp>
/auth/userinfo|-|common.model.PermResult<access.admin.dto.auth.UserInfoResp>
/config/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/config/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.ConfigResp>
/config/page|common.model.PageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.ConfigResp>>
/config/update|access.admin.dto.req.ConfigUpdateReq|common.model.PermResult<Void>
/dict/data/create|access.admin.dto.req.DictDataCreateReq|common.model.PermResult<Long>
/dict/data/delete|common.model.IdReq|common.model.PermResult<Void>
/dict/data/list|common.model.IdReq|common.model.PermResult<List<access.admin.dto.resp.DictDataResp>>
/dict/data/update|access.admin.dto.req.DictDataUpdateReq|common.model.PermResult<Void>
/dict/type/create|access.admin.dto.req.DictTypeCreateReq|common.model.PermResult<Long>
/dict/type/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/dict/type/list|-|common.model.PermResult<List<access.admin.dto.resp.DictTypeResp>>
/dict/type/page|common.model.PageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.DictTypeResp>>
/file/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/file/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.FileResp>
/file/download|common.model.IdReq|void
/file/page|access.admin.dto.req.FilePageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.FileResp>>
/file/upload|-|common.model.PermResult<Long>
/job/create|access.admin.dto.req.JobCreateReq|common.model.PermResult<Long>
/job/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/job/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.JobResp>
/job/log/page|access.admin.dto.req.JobLogPageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.JobLogResp>>
/job/page|common.model.PageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.JobResp>>
/job/toggle|access.admin.controller.JobController$ToggleJobReq|common.model.PermResult<Void>
/job/trigger|common.model.IdReq|common.model.PermResult<Void>
/job/update|access.admin.dto.req.JobUpdateReq|common.model.PermResult<Void>
/login-log/page|common.model.PageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.LoginLogResp>>
/menu/create|access.admin.dto.req.MenuCreateReq|common.model.PermResult<Long>
/menu/delete|common.model.IdReq|common.model.PermResult<Void>
/menu/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.MenuResp>
/menu/tree|-|common.model.PermResult<List<access.admin.dto.resp.MenuResp>>
/menu/update|access.admin.dto.req.MenuUpdateReq|common.model.PermResult<Void>
/notice/create|access.admin.dto.req.NoticeCreateReq|common.model.PermResult<Long>
/notice/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/notice/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.NoticeResp>
/notice/my-notices|-|common.model.PermResult<List<access.admin.service.NoticeService$UserNoticeItem>>
/notice/page|common.model.PageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.NoticeResp>>
/notice/publish|common.model.IdReq|common.model.PermResult<Void>
/notice/read|common.model.IdReq|common.model.PermResult<Void>
/notice/update|access.admin.dto.req.NoticeUpdateReq|common.model.PermResult<Void>
/oauth2/client/create|access.admin.dto.req.Oauth2ClientCreateReq|common.model.PermResult<Long>
/oauth2/client/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/oauth2/client/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.Oauth2ClientResp>
/oauth2/client/page|access.admin.dto.req.Oauth2ClientPageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.Oauth2ClientResp>>
/oauth2/client/update|access.admin.dto.req.Oauth2ClientUpdateReq|common.model.PermResult<Void>
/org-tree-config/create|access.admin.dto.req.OrgTreeConfigCreateReq|common.model.PermResult<Long>
/org-tree-config/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/org-tree-config/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.OrgTreeConfigResp>
/org-tree-config/page|common.model.PageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.OrgTreeConfigResp>>
/org-tree-config/set-default|common.model.IdReq|common.model.PermResult<Void>
/org-tree-config/update|access.admin.dto.req.OrgTreeConfigUpdateReq|common.model.PermResult<Void>
/org/create|access.admin.dto.req.OrgCreateReq|common.model.PermResult<Long>
/org/delete|common.model.IdReq|common.model.PermResult<Void>
/org/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.OrgResp>
/org/page|access.admin.dto.req.OrgPageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.OrgResp>>
/org/tree|access.admin.dto.req.OrgQuery|common.model.PermResult<List<access.admin.dto.resp.OrgResp>>
/org/update|access.admin.dto.req.OrgUpdateReq|common.model.PermResult<Void>
/org/users|common.model.IdReq|common.model.PermResult<List<access.admin.dto.resp.OrgUserItemResp>>
/role/list|access.admin.controller.AdminRoleController$RoleListQueryReq|common.model.PermResult<perm.common.dto.resp.ItemsResp<access.admin.dto.resp.RoleListItemResp>>
/role/my-info|-|common.model.PermResult<access.admin.dto.auth.UserInfoResp>
/user-org/assign|access.admin.dto.req.UserOrgAssignReq|common.model.PermResult<Void>
/user-org/list|common.model.IdReq|common.model.PermResult<List<access.admin.dto.resp.UserPageItemResp$OrgBrief>>
/user-org/remove|access.admin.dto.req.UserOrgRemoveReq|common.model.PermResult<Void>
/user-org/set-primary|access.admin.dto.req.UserOrgSetPrimaryReq|common.model.PermResult<Void>
/user-role/list|access.admin.dto.req.UserRoleListReq|common.model.PermResult<perm.common.dto.resp.ItemsResp<access.admin.dto.resp.UserRoleItemResp>>
/user/create|access.admin.dto.req.UserCreateReq|common.model.PermResult<access.admin.dto.resp.UserCreateResp>
/user/delete|access.admin.dto.req.IdsReq|common.model.PermResult<Void>
/user/detail|common.model.IdReq|common.model.PermResult<access.admin.dto.resp.UserResp>
/user/enable|access.admin.dto.req.UserUpdateStatusReq|common.model.PermResult<Void>
/user/member-candidates|access.admin.dto.req.MemberCandidatesReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.MemberCandidateItemResp>>
/user/page|access.admin.dto.req.UserPageReq|common.model.PermResult<common.model.PaginatedResult<access.admin.dto.resp.UserPageItemResp>>
/user/reset-password|access.admin.dto.req.ResetPasswordReq|common.model.PermResult<access.admin.dto.resp.ResetPasswordResp>
/user/update|access.admin.dto.req.UserUpdateReq|common.model.PermResult<Void>
/user/user-menus|common.model.IdReq|common.model.PermResult<access.admin.dto.auth.UserInfoResp>
""".strip().split("\n"));

    /** 统一响应包装的唯一白名单：二进制文件流直出（void）。 */
    private static final Set<String> NON_WRAPPER_WHITELIST = Set.of("/file/download");

    /** 已按设计决策退役的路径前缀/路径（快照必须不含；负向防回归）。 */
    private static final List<String> RETIRED_PATHS = List.of(
        "/sync-task/list", "/sync-task/page", "/sync-task/detail", "/sync-task/delete",
        "/sync-task/due", "/sync-task/reset", "/sync-task/retry-now", "/sync-task/mark-success",
        "/sync-task/mark-failed", "/sync-task/batch-status", "/sync-task/rebuild-from-fact",
        "/audit-log/page",
        // T-PERM-043：GROUP_ROLE 写入口删除（含读接口 list，唯一生产者 add 从未成功写入）
        "/api/perm/abstract-role/extra-roles/add",
        "/api/perm/abstract-role/extra-roles/list",
        "/api/perm/abstract-role/extra-roles/remove",
        // T-ADMIN-024：admin 侧角色写代理删除（无存量调用方，不留兼容层）
        "/role/create",
        "/role/grant-menu",
        "/role/revoke-menu",
        "/user-role/assign",
        "/user-role/revoke",
        // T-PERM-034：role-resource-permission 旧写入口删除（2026-08-27 端点退役收口，
        // 契约终态=apply-grant-plan 唯一写入口；无存量调用方，授权页 v3.1 已走 apply-grant-plan）
        "/api/perm/role-resource-permission/save",
        "/api/perm/role-resource-permission/revoke",
        "/api/perm/role-resource-permission/children",
        "/api/perm/role-resource-permission/add-child",
        "/api/perm/role-resource-permission/remove-child"
    );

    /** 扫描 classpath 上全部 Controller 并拼装「路径|请求类型|响应类型」签名（类级/方法级均枚举全部 path 值）。 */
    private Set<String> scanSignatures() throws Exception {
        Set<String> signatures = new TreeSet<>();
        for (Class<?> clazz : controllerClasses()) {
            RequestMapping classMapping =
                AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
            // 评审修复：类级映射枚举全部 path 值（多路径类映射不漏检）
            Set<String> bases = new TreeSet<>();
            if (classMapping == null || classMapping.path().length == 0) {
                bases.add("");
            } else {
                for (String p : classMapping.path()) {
                    bases.add(p);
                }
            }
            for (Method m : clazz.getDeclaredMethods()) {
                RequestMapping merged =
                    AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (merged == null) {
                    continue;
                }
                String reqType = "-";
                for (Parameter p : m.getParameters()) {
                    if (p.isAnnotationPresent(RequestBody.class)) {
                        reqType = p.getType().getName();
                        break;
                    }
                }
                String respType = m.getGenericReturnType().getTypeName();
                for (String base : bases) {
                    for (String sub : merged.path()) {
                        String path = (base + sub).replace("//", "/");
                        signatures.add(path + "|" + normalize(reqType) + "|" + normalize(respType));
                    }
                }
            }
        }
        return signatures;
    }

    /**
     * 域标记消歧归一化（与快照生成口径一致）。
     * <p>
     * 评审修复：仓库存在多个跨包同名 DTO（admin/permission 两个 UserRoleListReq、
     * common.model.IdReq 与 access.permission.dto.req.IdReq），仅比较简单类名
     * 或仅二域标记时误换同名类型仍通过——归一化只剥离固定仓前缀
     * （cn.ac.fage.accessmesh.）与 java 容器/基础包，其余包路径全保留，
     * 任意跨包同名类型互替都会导致快照失败。
     * </p>
     */
    private static String normalize(String typeName) {
        return typeName
            .replace("cn.ac.fage.accessmesh.", "")
            .replace("java.util.", "")
            .replace("java.lang.", "");
    }

    @Test
    @DisplayName("全量路径快照：Controller 映射与 Controller 扫描一致，增删必须显式更新快照")
    void controllerPaths_matchSnapshot() throws Exception {
        Set<String> actual = new TreeSet<>();
        scanSignatures().forEach(s -> actual.add(s.substring(0, s.indexOf('|'))));

        assertThat(actual).as("代码路径必须与快照完全一致（新增/删除路径须更新快照并记录依据）")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_PATHS);
    }

    @Test
    @DisplayName("签名快照：路径→请求体类型|响应类型 与 Controller 扫描一致（DTO 类型级漂移检测）")
    void signatures_matchSnapshot() throws Exception {
        assertThat(scanSignatures())
            .as("请求/响应 DTO 类型变更必须显式更新签名快照并记录依据")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_SIGNATURES);
    }

    @Test
    @DisplayName("POST-only：映射方法必须显式标注 @PostMapping（未限定方法的 @RequestMapping 违规）")
    void allMappingsArePost() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : controllerClasses()) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class) == null) {
                    continue;
                }
                // 按 @PostMapping 注解身份判定：空 method 数组的 @RequestMapping 不再逃逸
                if (AnnotatedElementUtils.findMergedAnnotation(m, PostMapping.class) == null) {
                    violations.add(m.toGenericString());
                }
            }
        }
        assertThat(violations).as("对外接口统一 POST + JSON Body（必须显式 @PostMapping）").isEmpty();
    }

    @Test
    @DisplayName("统一响应：除 /file/download 文件流白名单外，全部返回 PermResult 包装")
    void allMappingsUseUnifiedResponseWrapper() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String sig : scanSignatures()) {
            String[] parts = sig.split("\\|");
            String path = parts[0];
            String respType = parts[2];
            if (NON_WRAPPER_WHITELIST.contains(path)) {
                continue;
            }
            if (!respType.startsWith("common.model.PermResult<")) {
                violations.add(path + " -> " + respType);
            }
        }
        assertThat(violations).as("统一响应体 {code,message,data,...}（common R / PermResult 包装）").isEmpty();
    }

    @Test
    @DisplayName("无 RESTful 路径参数：映射路径不含 {...} 模板变量")
    void noPathVariableTemplates() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String sig : scanSignatures()) {
            String path = sig.substring(0, sig.indexOf('|'));
            if (path.contains("{") || path.contains("}")) {
                violations.add(path);
            }
        }
        assertThat(violations).as("ID 一律放请求体，禁止 RESTful 路径参数").isEmpty();
    }

    @Test
    @DisplayName("@RequestParam 仅允许出现在文件上传方法（MultipartFile 例外）")
    void requestParamOnlyForFileUpload() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : controllerClasses()) {
            for (Method m : clazz.getDeclaredMethods()) {
                boolean hasFileParam = Arrays.stream(m.getParameters())
                    .map(Parameter::getType)
                    .anyMatch(t -> t == MultipartFile.class || t == MultipartFile[].class);
                boolean hasRequestParam = Arrays.stream(m.getParameters())
                    .anyMatch(p -> p.isAnnotationPresent(RequestParam.class));
                if (hasRequestParam && !hasFileParam) {
                    violations.add(m.toGenericString());
                }
            }
        }
        assertThat(violations).as("@RequestParam 仅文件上传例外允许").isEmpty();
    }

    @Test
    @DisplayName("退役接口负向断言：RETIRED_PATHS 全部路径（/sync-task/*、/audit-log/page、extra-roles/*、admin 侧角色写代理 5 条、role-resource-permission 旧写入口 5 条）无任何 Controller 映射")
    void retiredPaths_haveNoControllerMappings() throws Exception {
        Set<String> actual = new TreeSet<>();
        scanSignatures().forEach(s -> actual.add(s.substring(0, s.indexOf('|'))));
        List<String> resurrected = new ArrayList<>();
        for (String retired : RETIRED_PATHS) {
            if (actual.contains(retired)) {
                resurrected.add(retired);
            }
        }
        for (String path : actual) {
            if (path.startsWith("/sync-task/") && !resurrected.contains(path)) {
                resurrected.add(path);
            }
        }
        assertThat(resurrected).as("退役接口不得注册任何 Controller 映射").isEmpty();
    }

    /**
     * classpath 上的 Controller 类；排除测试类与测试夹具
     * （test-classes 目录 / 嵌套 Stub 类，如 SyncEndpointAuthIT$StubActuatorController）。
     */
    private List<Class<?>> controllerClasses() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String name = bd.getBeanClassName();
            if (name.contains("$")) {
                continue;
            }
            Class<?> clazz = Class.forName(name);
            java.net.URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && location.toString().contains("test-classes")) {
                continue;
            }
            classes.add(clazz);
        }
        return classes;
    }
}
