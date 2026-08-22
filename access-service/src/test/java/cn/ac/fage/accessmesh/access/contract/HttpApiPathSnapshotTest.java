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
 * （git 5f1e65dd5^：210 条）双向核对——归并后恰为 198 条，仅减少 12 条且全部
 * 有设计决策背书（11 条 /sync-task/*：T-ACCESS-005 退役；/audit-log/page：
 * T-ACCESS-007 确认零引用后删除）。
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
/api/perm/abstract-role/extra-roles/add
/api/perm/abstract-role/extra-roles/list
/api/perm/abstract-role/extra-roles/remove
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
/api/perm/role-resource-permission/add-child
/api/perm/role-resource-permission/apply-grant-plan
/api/perm/role-resource-permission/children
/api/perm/role-resource-permission/list
/api/perm/role-resource-permission/remove-child
/api/perm/role-resource-permission/revoke
/api/perm/role-resource-permission/save
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
/role/create
/role/grant-menu
/role/list
/role/my-info
/role/revoke-menu
/user-org/assign
/user-org/list
/user-org/remove
/user-org/set-primary
/user-role/assign
/user-role/list
/user-role/revoke
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

    /** 路径 → 请求体类型 | 响应类型 签名快照（类型级 DTO 契约，198 条）。 */
    private static final Set<String> EXPECTED_SIGNATURES = Set.of("""
/api/perm/abstract-role/create|Prm.RoleCreateReq|PermResult<Prm.RoleResp>
/api/perm/abstract-role/detail|Prm.IdReq|PermResult<Prm.RoleResp>
/api/perm/abstract-role/extra-roles/add|Prm.GroupRoleExtraRoleReq|PermResult<Void>
/api/perm/abstract-role/extra-roles/list|Prm.GroupRoleExtraRolesListReq|PermResult<Prm.ItemsResp<Prm.RoleSummaryResp>>
/api/perm/abstract-role/extra-roles/remove|Prm.GroupRoleExtraRoleReq|PermResult<Void>
/api/perm/abstract-role/full-sync|Prm.AbstractRoleFullSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-role/list|Prm.RoleListReq|PermResult<Prm.PaginatedResp<Prm.RoleResp>>
/api/perm/abstract-role/move|Prm.RoleMoveReq|PermResult<Void>
/api/perm/abstract-role/remove|Prm.IdsReq|PermResult<Void>
/api/perm/abstract-role/sync|Prm.AbstractRoleSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-role/tree|Prm.RoleTreeReq|PermResult<Prm.ItemsResp<Prm.RoleTreeResp>>
/api/perm/abstract-role/update|Prm.RoleUpdateReq|PermResult<Prm.RoleResp>
/api/perm/abstract-user/create|Prm.UserCreateReq|PermResult<Prm.UserResp>
/api/perm/abstract-user/detail|Prm.IdReq|PermResult<Prm.UserResp>
/api/perm/abstract-user/full-sync|Prm.AbstractUserFullSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-user/list|Prm.UserListReq|PermResult<Prm.PaginatedResp<Prm.UserResp>>
/api/perm/abstract-user/remove|Prm.IdsReq|PermResult<Void>
/api/perm/abstract-user/sync|Prm.AbstractUserSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-user/update|Prm.UserUpdateReq|PermResult<Prm.UserResp>
/api/perm/auth/batch-check|Prm.BatchAuthCheckReq|PermResult<Prm.BatchAuthCheckResp>
/api/perm/auth/check-interface|Prm.CheckInterfaceReq|PermResult<Prm.CheckInterfaceResp>
/api/perm/auth/check|Prm.AuthCheckReq|PermResult<Prm.AuthCheckResp>
/api/perm/auth/interface-snapshot|InterfaceSnapshotReq|PermResult<InterfaceSnapshotResp>
/api/perm/auth/query-permission-tree|Prm.PermissionTreeReq|PermResult<Prm.PermissionTreeResp>
/api/perm/auth/query-resources|Prm.QueryResourcesReq|PermResult<Prm.QueryResourcesResp>
/api/perm/auth/query-scopes|Prm.QueryScopesReq|PermResult<Prm.QueryScopesResp>
/api/perm/biz-domain/create|Prm.BizDomainCreateReq|PermResult<Prm.BizDomainResp>
/api/perm/biz-domain/detail|Prm.IdReq|PermResult<Prm.BizDomainResp>
/api/perm/biz-domain/list|Prm.EmptyReq|PermResult<Prm.ItemsResp<Prm.BizDomainResp>>
/api/perm/biz-domain/remove|Prm.IdsReq|PermResult<Void>
/api/perm/biz-domain/update|Prm.BizDomainUpdateReq|PermResult<Prm.BizDomainResp>
/api/perm/conflict-rule/create|Prm.ConflictRuleReq|PermResult<Prm.ConflictRuleResp>
/api/perm/conflict-rule/detail|Prm.IdReq|PermResult<Prm.ConflictRuleResp>
/api/perm/conflict-rule/detect|Prm.ConflictRuleDetectReq|PermResult<Prm.ConflictDetectResp>
/api/perm/conflict-rule/list|Prm.EmptyReq|PermResult<Prm.ItemsResp<Prm.ConflictRuleResp>>
/api/perm/conflict-rule/remove|Prm.IdsReq|PermResult<Void>
/api/perm/conflict-rule/update|Prm.ConflictRuleUpdateReq|PermResult<Prm.ConflictRuleResp>
/api/perm/domain-config/detail|Prm.DomainConfigGetReq|PermResult<Prm.DomainConfigResp>
/api/perm/domain-config/list|Prm.DomainConfigListReq|PermResult<Prm.ItemsResp<Prm.DomainConfigResp>>
/api/perm/domain-config/remove|Prm.IdsReq|PermResult<Void>
/api/perm/domain-config/save|Prm.DomainConfigReq|PermResult<Prm.DomainConfigResp>
/api/perm/log/change/list|Prm.ChangeLogListReq|PermResult<Prm.PaginatedResp<Prm.ChangeLogResp>>
/api/perm/log/operation/list|Prm.OperationLogListReq|PermResult<Prm.PaginatedResp<Prm.OperationLogResp>>
/api/perm/operation-permission/create|Prm.OperationCreateReq|PermResult<Prm.OperationPermissionResp>
/api/perm/operation-permission/detail|Prm.IdReq|PermResult<Prm.OperationPermissionResp>
/api/perm/operation-permission/list|Prm.OperationListReq|PermResult<Prm.ItemsResp<Prm.OperationPermissionResp>>
/api/perm/operation-permission/remove|Prm.IdsReq|PermResult<Void>
/api/perm/operation-permission/update|Prm.OperationUpdateReq|PermResult<Prm.OperationPermissionResp>
/api/perm/permission-condition/create|Prm.ConditionCreateReq|PermResult<Prm.ConditionResp>
/api/perm/permission-condition/detail|Prm.IdReq|PermResult<Prm.ConditionResp>
/api/perm/permission-condition/list|Prm.EmptyReq|PermResult<Prm.ItemsResp<Prm.ConditionResp>>
/api/perm/permission-condition/remove|Prm.IdsReq|PermResult<Void>
/api/perm/permission-condition/update|Prm.ConditionUpdateReq|PermResult<Prm.ConditionResp>
/api/perm/permission-view/effective-permission-codes|UserEffectivePermissionCodesReq|PermResult<UserEffectivePermissionCodesResp>
/api/perm/permission-view/effective-permissions|Prm.UserPermissionViewReq|PermResult<Prm.PermissionEffectivePermissionsResp>
/api/perm/permission-view/effective-roles|Prm.UserEffectiveRolesReq|PermResult<Prm.ItemsResp<Prm.EffectiveRoleResp>>
/api/perm/permission-view/explain|Prm.PermissionExplainReq|PermResult<Prm.PermissionExplainResp>
/api/perm/permission-view/recent-changes|Prm.PermissionRecentChangesReq|PermResult<Prm.PermissionRecentChangesResp>
/api/perm/permission-view/resource-tree|Prm.UserResourceTreeReq|PermResult<Prm.ItemsResp<Prm.ResourcePermissionTreeResp>>
/api/perm/permission-view/resource-users|Prm.ResourcePermissionViewReq|PermResult<Prm.ResourcePermissionViewResp>
/api/perm/permission-view/role-permissions|Prm.RolePermissionViewReq|PermResult<Prm.RolePermissionViewResp>
/api/perm/resource-api-mapping/create|Prm.ApiMappingAddReq|PermResult<Prm.ApiMappingResp>
/api/perm/resource-api-mapping/list|Prm.ApiMappingListReq|PermResult<Prm.ItemsResp<Prm.ApiMappingResp>>
/api/perm/resource-api-mapping/remove|Prm.IdsReq|PermResult<Void>
/api/perm/resource-api-mapping/update|Prm.ApiMappingUpdateReq|PermResult<Prm.ApiMappingResp>
/api/perm/resource-dependency/batch-sync|Prm.DependencyBatchSyncReq|PermResult<Void>
/api/perm/resource-dependency/check|Prm.ResourceDependencyCheckReq|PermResult<Prm.DependencyCycleCheckResp>
/api/perm/resource-dependency/create|Prm.ResourceDependencyCreateReq|PermResult<Prm.ResourceDependencyResp>
/api/perm/resource-dependency/graph|Prm.DependencyListReq|PermResult<Prm.ItemsResp<Prm.ResourceDependencyResp>>
/api/perm/resource-dependency/list|Prm.DependencyListReq|PermResult<Prm.ItemsResp<Prm.ResourceDependencyResp>>
/api/perm/resource-dependency/remove|Prm.IdsReq|PermResult<Void>
/api/perm/resource-dependency/update|Prm.ResourceDependencyUpdateReq|PermResult<Prm.ResourceDependencyResp>
/api/perm/resource-entity/batch-create|Prm.ResourceBatchCreateReq|PermResult<Prm.ItemsResp<Prm.ResourceResp>>
/api/perm/resource-entity/create|Prm.ResourceCreateReq|PermResult<Prm.ResourceResp>
/api/perm/resource-entity/detail|Prm.IdReq|PermResult<Prm.ResourceResp>
/api/perm/resource-entity/full-sync|Prm.ResourceEntityFullSyncReq|PermResult<SyncResultResp>
/api/perm/resource-entity/list|Prm.ResourceListReq|PermResult<Prm.PaginatedResp<Prm.ResourceResp>>
/api/perm/resource-entity/move|Prm.ResourceMoveReq|PermResult<Void>
/api/perm/resource-entity/remove|Prm.IdsReq|PermResult<Void>
/api/perm/resource-entity/sync|Prm.ResourceEntitySyncReq|PermResult<SyncResultResp>
/api/perm/resource-entity/tree|Prm.ResourceTreeReq|PermResult<Prm.ItemsResp<Prm.ResourceTreeResp>>
/api/perm/resource-entity/update|Prm.ResourceUpdateReq|PermResult<Prm.ResourceResp>
/api/perm/role-resource-permission/add-child|Prm.RolePermissionAddChildReq|PermResult<Prm.RolePermissionItemsResp>
/api/perm/role-resource-permission/apply-grant-plan|Prm.ApplyGrantPlanReq|PermResult<Prm.RolePermissionItemsResp>
/api/perm/role-resource-permission/children|Prm.RolePermissionChildrenReq|PermResult<Prm.RolePermissionItemsResp>
/api/perm/role-resource-permission/list|Prm.RolePermissionListReq|PermResult<Prm.RolePermissionItemsResp>
/api/perm/role-resource-permission/remove-child|Prm.RolePermissionRemoveChildReq|PermResult<Void>
/api/perm/role-resource-permission/revoke|Prm.BatchRevokeReq|PermResult<Void>
/api/perm/role-resource-permission/save|Prm.RoleGrantReq|PermResult<Prm.RolePermissionItemsResp>
/api/perm/service-config/apis|Prm.ServiceConfigApisReq|PermResult<Prm.ItemsResp<Prm.ApiMappingResp>>
/api/perm/service-config/detail|Prm.ServiceConfigGetReq|PermResult<Prm.ServiceConfigResp>
/api/perm/service-config/list|Prm.EmptyReq|PermResult<Prm.ItemsResp<Prm.ServiceConfigResp>>
/api/perm/service-config/remove|Prm.IdsReq|PermResult<Void>
/api/perm/service-config/save|Prm.ServiceConfigReq|PermResult<Prm.ServiceConfigResp>
/api/perm/service-config/sync|Prm.ServiceConfigSyncReq|PermResult<Prm.ServiceConfigSyncResp>
/api/perm/system-config/detail|Prm.SystemConfigGetReq|PermResult<Prm.SystemConfigResp>
/api/perm/system-config/list|Prm.EmptyReq|PermResult<Prm.ItemsResp<Prm.SystemConfigResp>>
/api/perm/system-config/save|Prm.SystemConfigReq|PermResult<Prm.SystemConfigResp>
/api/perm/type-definition/create|Prm.TypeCreateReq|PermResult<Prm.TypeDefinitionResp>
/api/perm/type-definition/detail|Prm.IdReq|PermResult<Prm.TypeDefinitionResp>
/api/perm/type-definition/list|Prm.TypeListReq|PermResult<Prm.ItemsResp<Prm.TypeDefinitionResp>>
/api/perm/type-definition/remove|Prm.IdsReq|PermResult<Void>
/api/perm/type-definition/update|Prm.TypeUpdateReq|PermResult<Prm.TypeDefinitionResp>
/api/perm/user-role/assign|Prm.UserAssignRoleReq|PermResult<Void>
/api/perm/user-role/batch-assign|Prm.UserRoleBatchAssignReq|PermResult<Void>
/api/perm/user-role/full-sync|Prm.UserRoleFullSyncReq|PermResult<SyncResultResp>
/api/perm/user-role/list|Prm.UserRoleListReq|PermResult<Prm.UserRolesResp>
/api/perm/user-role/revoke|Prm.UserRoleBatchRevokeReq|PermResult<Void>
/api/perm/user-role/sync|Prm.UserRoleSyncReq|PermResult<SyncResultResp>
/auth/captcha|-|PermResult<Adm.CaptchaResp>
/auth/login/sms|Adm.SmsLoginReq|PermResult<Adm.LoginResp>
/auth/login|Adm.LoginReq|PermResult<Adm.LoginResp>
/auth/logout|-|PermResult<Void>
/auth/oauth2/authorize|Adm.AuthorizeReq|PermResult<Adm.AuthorizeResp>
/auth/oauth2/refresh|Adm.controller.OAuth2Controller$RefreshTokenReq|PermResult<Adm.TokenResp>
/auth/oauth2/revoke|Adm.controller.OAuth2Controller$RevokeTokenReq|PermResult<Void>
/auth/oauth2/token|Adm.TokenReq|PermResult<Adm.TokenResp>
/auth/oauth2/userinfo|-|PermResult<Adm.OAuth2UserInfoResp>
/auth/user-menu|-|PermResult<Adm.UserMenuResp>
/auth/userinfo|-|PermResult<Adm.UserInfoResp>
/config/delete|Adm.IdsReq|PermResult<Void>
/config/detail|IdReq|PermResult<Adm.ConfigResp>
/config/page|PageReq|PermResult<PaginatedResult<Adm.ConfigResp>>
/config/update|Adm.ConfigUpdateReq|PermResult<Void>
/dict/data/create|Adm.DictDataCreateReq|PermResult<Long>
/dict/data/delete|IdReq|PermResult<Void>
/dict/data/list|IdReq|PermResult<List<Adm.DictDataResp>>
/dict/data/update|Adm.DictDataUpdateReq|PermResult<Void>
/dict/type/create|Adm.DictTypeCreateReq|PermResult<Long>
/dict/type/delete|Adm.IdsReq|PermResult<Void>
/dict/type/list|-|PermResult<List<Adm.DictTypeResp>>
/dict/type/page|PageReq|PermResult<PaginatedResult<Adm.DictTypeResp>>
/file/delete|Adm.IdsReq|PermResult<Void>
/file/detail|IdReq|PermResult<Adm.FileResp>
/file/download|IdReq|void
/file/page|Adm.FilePageReq|PermResult<PaginatedResult<Adm.FileResp>>
/file/upload|-|PermResult<Long>
/job/create|Adm.JobCreateReq|PermResult<Long>
/job/delete|Adm.IdsReq|PermResult<Void>
/job/detail|IdReq|PermResult<Adm.JobResp>
/job/log/page|Adm.JobLogPageReq|PermResult<PaginatedResult<Adm.JobLogResp>>
/job/page|PageReq|PermResult<PaginatedResult<Adm.JobResp>>
/job/toggle|Adm.controller.JobController$ToggleJobReq|PermResult<Void>
/job/trigger|IdReq|PermResult<Void>
/job/update|Adm.JobUpdateReq|PermResult<Void>
/login-log/page|PageReq|PermResult<PaginatedResult<Adm.LoginLogResp>>
/menu/create|Adm.MenuCreateReq|PermResult<Long>
/menu/delete|IdReq|PermResult<Void>
/menu/detail|IdReq|PermResult<Adm.MenuResp>
/menu/tree|-|PermResult<List<Adm.MenuResp>>
/menu/update|Adm.MenuUpdateReq|PermResult<Void>
/notice/create|Adm.NoticeCreateReq|PermResult<Long>
/notice/delete|Adm.IdsReq|PermResult<Void>
/notice/detail|IdReq|PermResult<Adm.NoticeResp>
/notice/my-notices|-|PermResult<List<Adm.service.NoticeService$UserNoticeItem>>
/notice/page|PageReq|PermResult<PaginatedResult<Adm.NoticeResp>>
/notice/publish|IdReq|PermResult<Void>
/notice/read|IdReq|PermResult<Void>
/notice/update|Adm.NoticeUpdateReq|PermResult<Void>
/oauth2/client/create|Adm.Oauth2ClientCreateReq|PermResult<Long>
/oauth2/client/delete|Adm.IdsReq|PermResult<Void>
/oauth2/client/detail|IdReq|PermResult<Adm.Oauth2ClientResp>
/oauth2/client/page|Adm.Oauth2ClientPageReq|PermResult<PaginatedResult<Adm.Oauth2ClientResp>>
/oauth2/client/update|Adm.Oauth2ClientUpdateReq|PermResult<Void>
/org-tree-config/create|Adm.OrgTreeConfigCreateReq|PermResult<Long>
/org-tree-config/delete|Adm.IdsReq|PermResult<Void>
/org-tree-config/detail|IdReq|PermResult<Adm.OrgTreeConfigResp>
/org-tree-config/page|PageReq|PermResult<PaginatedResult<Adm.OrgTreeConfigResp>>
/org-tree-config/set-default|IdReq|PermResult<Void>
/org-tree-config/update|Adm.OrgTreeConfigUpdateReq|PermResult<Void>
/org/create|Adm.OrgCreateReq|PermResult<Long>
/org/delete|IdReq|PermResult<Void>
/org/detail|IdReq|PermResult<Adm.OrgResp>
/org/page|Adm.OrgPageReq|PermResult<PaginatedResult<Adm.OrgResp>>
/org/tree|Adm.OrgQuery|PermResult<List<Adm.OrgResp>>
/org/update|Adm.OrgUpdateReq|PermResult<Void>
/org/users|IdReq|PermResult<List<Adm.OrgUserItemResp>>
/role/create|Adm.controller.AdminRoleController$CreateRoleReq|PermResult<Long>
/role/grant-menu|Adm.controller.AdminRoleController$RoleMenuReq|PermResult<Void>
/role/list|Adm.controller.AdminRoleController$RoleListQueryReq|PermResult<ItemsResp<Adm.RoleListItemResp>>
/role/my-info|-|PermResult<Adm.UserInfoResp>
/role/revoke-menu|Adm.controller.AdminRoleController$RoleMenuReq|PermResult<Void>
/user-org/assign|Adm.UserOrgAssignReq|PermResult<Void>
/user-org/list|IdReq|PermResult<List<Adm.UserPageItemResp$OrgBrief>>
/user-org/remove|Adm.UserOrgRemoveReq|PermResult<Void>
/user-org/set-primary|Adm.UserOrgSetPrimaryReq|PermResult<Void>
/user-role/assign|Adm.UserRoleAssignReq|PermResult<Void>
/user-role/list|Adm.UserRoleListReq|PermResult<ItemsResp<Adm.UserRoleItemResp>>
/user-role/revoke|Adm.UserRoleRevokeReq|PermResult<Void>
/user/create|Adm.UserCreateReq|PermResult<Adm.UserCreateResp>
/user/delete|Adm.IdsReq|PermResult<Void>
/user/detail|IdReq|PermResult<Adm.UserResp>
/user/enable|Adm.UserUpdateStatusReq|PermResult<Void>
/user/member-candidates|Adm.MemberCandidatesReq|PermResult<PaginatedResult<Adm.MemberCandidateItemResp>>
/user/page|Adm.UserPageReq|PermResult<PaginatedResult<Adm.UserPageItemResp>>
/user/reset-password|Adm.ResetPasswordReq|PermResult<Adm.ResetPasswordResp>
/user/update|Adm.UserUpdateReq|PermResult<Void>
/user/user-menus|IdReq|PermResult<Adm.UserInfoResp>
""".strip().split("\n"));

    /** 统一响应包装的唯一白名单：二进制文件流直出（void）。 */
    private static final Set<String> NON_WRAPPER_WHITELIST = Set.of("/file/download");

    /** 已按设计决策退役的路径前缀/路径（快照必须不含；负向防回归）。 */
    private static final List<String> RETIRED_PATHS = List.of(
        "/sync-task/list", "/sync-task/page", "/sync-task/detail", "/sync-task/delete",
        "/sync-task/due", "/sync-task/reset", "/sync-task/retry-now", "/sync-task/mark-success",
        "/sync-task/mark-failed", "/sync-task/batch-status", "/sync-task/rebuild-from-fact",
        "/audit-log/page"
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
     * 评审修复：仓库存在 admin 与 permission 同名 DTO（如两个 UserRoleListReq），
     * 仅比较简单类名时误换同名不同包 DTO 仍通过——归一化保留域标记
     * （Adm./Prm.，中间用大写下划线防小写链剥离误食），再剥离剩余小写包段。
     * </p>
     */
    private static String normalize(String typeName) {
        String t = typeName
            .replace("cn.ac.fage.accessmesh.access.admin.", "ADMIN_")
            .replace("cn.ac.fage.accessmesh.access.permission.", "PERM_");
        String prev;
        do {
            prev = t;
            t = t.replaceAll("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+\\.", "");
        } while (!prev.equals(t));
        return t.replace("ADMIN_", "Adm.").replace("PERM_", "Prm.");
    }

    @Test
    @DisplayName("全量路径快照：Controller 映射恰为 198 条，增删必须显式更新快照")
    void controllerPaths_matchSnapshot() throws Exception {
        Set<String> actual = new TreeSet<>();
        scanSignatures().forEach(s -> actual.add(s.substring(0, s.indexOf('|'))));

        assertThat(actual).as("代码路径必须与快照完全一致（新增/删除路径须更新快照并记录依据）")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_PATHS);
    }

    @Test
    @DisplayName("签名快照：路径→请求体类型|响应类型 恰为 198 条（DTO 类型级漂移检测）")
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
            if (!respType.startsWith("PermResult<")) {
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
    @DisplayName("退役接口负向断言：/sync-task/* 与 /audit-log/page 无任何 Controller 映射")
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
