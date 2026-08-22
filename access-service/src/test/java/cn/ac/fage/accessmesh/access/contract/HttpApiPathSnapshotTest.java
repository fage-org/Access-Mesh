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
/api/perm/abstract-role/create|RoleCreateReq|PermResult<RoleResp>
/api/perm/abstract-role/detail|IdReq|PermResult<RoleResp>
/api/perm/abstract-role/extra-roles/add|GroupRoleExtraRoleReq|PermResult<Void>
/api/perm/abstract-role/extra-roles/list|GroupRoleExtraRolesListReq|PermResult<ItemsResp<RoleSummaryResp>>
/api/perm/abstract-role/extra-roles/remove|GroupRoleExtraRoleReq|PermResult<Void>
/api/perm/abstract-role/full-sync|AbstractRoleFullSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-role/list|RoleListReq|PermResult<PaginatedResp<RoleResp>>
/api/perm/abstract-role/move|RoleMoveReq|PermResult<Void>
/api/perm/abstract-role/remove|IdsReq|PermResult<Void>
/api/perm/abstract-role/sync|AbstractRoleSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-role/tree|RoleTreeReq|PermResult<ItemsResp<RoleTreeResp>>
/api/perm/abstract-role/update|RoleUpdateReq|PermResult<RoleResp>
/api/perm/abstract-user/create|UserCreateReq|PermResult<UserResp>
/api/perm/abstract-user/detail|IdReq|PermResult<UserResp>
/api/perm/abstract-user/full-sync|AbstractUserFullSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-user/list|UserListReq|PermResult<PaginatedResp<UserResp>>
/api/perm/abstract-user/remove|IdsReq|PermResult<Void>
/api/perm/abstract-user/sync|AbstractUserSyncReq|PermResult<SyncResultResp>
/api/perm/abstract-user/update|UserUpdateReq|PermResult<UserResp>
/api/perm/auth/batch-check|BatchAuthCheckReq|PermResult<BatchAuthCheckResp>
/api/perm/auth/check-interface|CheckInterfaceReq|PermResult<CheckInterfaceResp>
/api/perm/auth/check|AuthCheckReq|PermResult<AuthCheckResp>
/api/perm/auth/interface-snapshot|InterfaceSnapshotReq|PermResult<InterfaceSnapshotResp>
/api/perm/auth/query-permission-tree|PermissionTreeReq|PermResult<PermissionTreeResp>
/api/perm/auth/query-resources|QueryResourcesReq|PermResult<QueryResourcesResp>
/api/perm/auth/query-scopes|QueryScopesReq|PermResult<QueryScopesResp>
/api/perm/biz-domain/create|BizDomainCreateReq|PermResult<BizDomainResp>
/api/perm/biz-domain/detail|IdReq|PermResult<BizDomainResp>
/api/perm/biz-domain/list|EmptyReq|PermResult<ItemsResp<BizDomainResp>>
/api/perm/biz-domain/remove|IdsReq|PermResult<Void>
/api/perm/biz-domain/update|BizDomainUpdateReq|PermResult<BizDomainResp>
/api/perm/conflict-rule/create|ConflictRuleReq|PermResult<ConflictRuleResp>
/api/perm/conflict-rule/detail|IdReq|PermResult<ConflictRuleResp>
/api/perm/conflict-rule/detect|ConflictRuleDetectReq|PermResult<ConflictDetectResp>
/api/perm/conflict-rule/list|EmptyReq|PermResult<ItemsResp<ConflictRuleResp>>
/api/perm/conflict-rule/remove|IdsReq|PermResult<Void>
/api/perm/conflict-rule/update|ConflictRuleUpdateReq|PermResult<ConflictRuleResp>
/api/perm/domain-config/detail|DomainConfigGetReq|PermResult<DomainConfigResp>
/api/perm/domain-config/list|DomainConfigListReq|PermResult<ItemsResp<DomainConfigResp>>
/api/perm/domain-config/remove|IdsReq|PermResult<Void>
/api/perm/domain-config/save|DomainConfigReq|PermResult<DomainConfigResp>
/api/perm/log/change/list|ChangeLogListReq|PermResult<PaginatedResp<ChangeLogResp>>
/api/perm/log/operation/list|OperationLogListReq|PermResult<PaginatedResp<OperationLogResp>>
/api/perm/operation-permission/create|OperationCreateReq|PermResult<OperationPermissionResp>
/api/perm/operation-permission/detail|IdReq|PermResult<OperationPermissionResp>
/api/perm/operation-permission/list|OperationListReq|PermResult<ItemsResp<OperationPermissionResp>>
/api/perm/operation-permission/remove|IdsReq|PermResult<Void>
/api/perm/operation-permission/update|OperationUpdateReq|PermResult<OperationPermissionResp>
/api/perm/permission-condition/create|ConditionCreateReq|PermResult<ConditionResp>
/api/perm/permission-condition/detail|IdReq|PermResult<ConditionResp>
/api/perm/permission-condition/list|EmptyReq|PermResult<ItemsResp<ConditionResp>>
/api/perm/permission-condition/remove|IdsReq|PermResult<Void>
/api/perm/permission-condition/update|ConditionUpdateReq|PermResult<ConditionResp>
/api/perm/permission-view/effective-permission-codes|UserEffectivePermissionCodesReq|PermResult<UserEffectivePermissionCodesResp>
/api/perm/permission-view/effective-permissions|UserPermissionViewReq|PermResult<PermissionEffectivePermissionsResp>
/api/perm/permission-view/effective-roles|UserEffectiveRolesReq|PermResult<ItemsResp<EffectiveRoleResp>>
/api/perm/permission-view/explain|PermissionExplainReq|PermResult<PermissionExplainResp>
/api/perm/permission-view/recent-changes|PermissionRecentChangesReq|PermResult<PermissionRecentChangesResp>
/api/perm/permission-view/resource-tree|UserResourceTreeReq|PermResult<ItemsResp<ResourcePermissionTreeResp>>
/api/perm/permission-view/resource-users|ResourcePermissionViewReq|PermResult<ResourcePermissionViewResp>
/api/perm/permission-view/role-permissions|RolePermissionViewReq|PermResult<RolePermissionViewResp>
/api/perm/resource-api-mapping/create|ApiMappingAddReq|PermResult<ApiMappingResp>
/api/perm/resource-api-mapping/list|ApiMappingListReq|PermResult<ItemsResp<ApiMappingResp>>
/api/perm/resource-api-mapping/remove|IdsReq|PermResult<Void>
/api/perm/resource-api-mapping/update|ApiMappingUpdateReq|PermResult<ApiMappingResp>
/api/perm/resource-dependency/batch-sync|DependencyBatchSyncReq|PermResult<Void>
/api/perm/resource-dependency/check|ResourceDependencyCheckReq|PermResult<DependencyCycleCheckResp>
/api/perm/resource-dependency/create|ResourceDependencyCreateReq|PermResult<ResourceDependencyResp>
/api/perm/resource-dependency/graph|DependencyListReq|PermResult<ItemsResp<ResourceDependencyResp>>
/api/perm/resource-dependency/list|DependencyListReq|PermResult<ItemsResp<ResourceDependencyResp>>
/api/perm/resource-dependency/remove|IdsReq|PermResult<Void>
/api/perm/resource-dependency/update|ResourceDependencyUpdateReq|PermResult<ResourceDependencyResp>
/api/perm/resource-entity/batch-create|ResourceBatchCreateReq|PermResult<ItemsResp<ResourceResp>>
/api/perm/resource-entity/create|ResourceCreateReq|PermResult<ResourceResp>
/api/perm/resource-entity/detail|IdReq|PermResult<ResourceResp>
/api/perm/resource-entity/full-sync|ResourceEntityFullSyncReq|PermResult<SyncResultResp>
/api/perm/resource-entity/list|ResourceListReq|PermResult<PaginatedResp<ResourceResp>>
/api/perm/resource-entity/move|ResourceMoveReq|PermResult<Void>
/api/perm/resource-entity/remove|IdsReq|PermResult<Void>
/api/perm/resource-entity/sync|ResourceEntitySyncReq|PermResult<SyncResultResp>
/api/perm/resource-entity/tree|ResourceTreeReq|PermResult<ItemsResp<ResourceTreeResp>>
/api/perm/resource-entity/update|ResourceUpdateReq|PermResult<ResourceResp>
/api/perm/role-resource-permission/add-child|RolePermissionAddChildReq|PermResult<RolePermissionItemsResp>
/api/perm/role-resource-permission/apply-grant-plan|ApplyGrantPlanReq|PermResult<RolePermissionItemsResp>
/api/perm/role-resource-permission/children|RolePermissionChildrenReq|PermResult<RolePermissionItemsResp>
/api/perm/role-resource-permission/list|RolePermissionListReq|PermResult<RolePermissionItemsResp>
/api/perm/role-resource-permission/remove-child|RolePermissionRemoveChildReq|PermResult<Void>
/api/perm/role-resource-permission/revoke|BatchRevokeReq|PermResult<Void>
/api/perm/role-resource-permission/save|RoleGrantReq|PermResult<RolePermissionItemsResp>
/api/perm/service-config/apis|ServiceConfigApisReq|PermResult<ItemsResp<ApiMappingResp>>
/api/perm/service-config/detail|ServiceConfigGetReq|PermResult<ServiceConfigResp>
/api/perm/service-config/list|EmptyReq|PermResult<ItemsResp<ServiceConfigResp>>
/api/perm/service-config/remove|IdsReq|PermResult<Void>
/api/perm/service-config/save|ServiceConfigReq|PermResult<ServiceConfigResp>
/api/perm/service-config/sync|ServiceConfigSyncReq|PermResult<ServiceConfigSyncResp>
/api/perm/system-config/detail|SystemConfigGetReq|PermResult<SystemConfigResp>
/api/perm/system-config/list|EmptyReq|PermResult<ItemsResp<SystemConfigResp>>
/api/perm/system-config/save|SystemConfigReq|PermResult<SystemConfigResp>
/api/perm/type-definition/create|TypeCreateReq|PermResult<TypeDefinitionResp>
/api/perm/type-definition/detail|IdReq|PermResult<TypeDefinitionResp>
/api/perm/type-definition/list|TypeListReq|PermResult<ItemsResp<TypeDefinitionResp>>
/api/perm/type-definition/remove|IdsReq|PermResult<Void>
/api/perm/type-definition/update|TypeUpdateReq|PermResult<TypeDefinitionResp>
/api/perm/user-role/assign|UserAssignRoleReq|PermResult<Void>
/api/perm/user-role/batch-assign|UserRoleBatchAssignReq|PermResult<Void>
/api/perm/user-role/full-sync|UserRoleFullSyncReq|PermResult<SyncResultResp>
/api/perm/user-role/list|UserRoleListReq|PermResult<UserRolesResp>
/api/perm/user-role/revoke|UserRoleBatchRevokeReq|PermResult<Void>
/api/perm/user-role/sync|UserRoleSyncReq|PermResult<SyncResultResp>
/auth/captcha|-|PermResult<CaptchaResp>
/auth/login/sms|SmsLoginReq|PermResult<LoginResp>
/auth/login|LoginReq|PermResult<LoginResp>
/auth/logout|-|PermResult<Void>
/auth/oauth2/authorize|AuthorizeReq|PermResult<AuthorizeResp>
/auth/oauth2/refresh|RefreshTokenReq|PermResult<TokenResp>
/auth/oauth2/revoke|RevokeTokenReq|PermResult<Void>
/auth/oauth2/token|TokenReq|PermResult<TokenResp>
/auth/oauth2/userinfo|-|PermResult<OAuth2UserInfoResp>
/auth/user-menu|-|PermResult<UserMenuResp>
/auth/userinfo|-|PermResult<UserInfoResp>
/config/delete|IdsReq|PermResult<Void>
/config/detail|IdReq|PermResult<ConfigResp>
/config/page|PageReq|PermResult<PaginatedResult<ConfigResp>>
/config/update|ConfigUpdateReq|PermResult<Void>
/dict/data/create|DictDataCreateReq|PermResult<Long>
/dict/data/delete|IdReq|PermResult<Void>
/dict/data/list|IdReq|PermResult<List<DictDataResp>>
/dict/data/update|DictDataUpdateReq|PermResult<Void>
/dict/type/create|DictTypeCreateReq|PermResult<Long>
/dict/type/delete|IdsReq|PermResult<Void>
/dict/type/list|-|PermResult<List<DictTypeResp>>
/dict/type/page|PageReq|PermResult<PaginatedResult<DictTypeResp>>
/file/delete|IdsReq|PermResult<Void>
/file/detail|IdReq|PermResult<FileResp>
/file/download|IdReq|void
/file/page|FilePageReq|PermResult<PaginatedResult<FileResp>>
/file/upload|-|PermResult<Long>
/job/create|JobCreateReq|PermResult<Long>
/job/delete|IdsReq|PermResult<Void>
/job/detail|IdReq|PermResult<JobResp>
/job/log/page|JobLogPageReq|PermResult<PaginatedResult<JobLogResp>>
/job/page|PageReq|PermResult<PaginatedResult<JobResp>>
/job/toggle|ToggleJobReq|PermResult<Void>
/job/trigger|IdReq|PermResult<Void>
/job/update|JobUpdateReq|PermResult<Void>
/login-log/page|PageReq|PermResult<PaginatedResult<LoginLogResp>>
/menu/create|MenuCreateReq|PermResult<Long>
/menu/delete|IdReq|PermResult<Void>
/menu/detail|IdReq|PermResult<MenuResp>
/menu/tree|-|PermResult<List<MenuResp>>
/menu/update|MenuUpdateReq|PermResult<Void>
/notice/create|NoticeCreateReq|PermResult<Long>
/notice/delete|IdsReq|PermResult<Void>
/notice/detail|IdReq|PermResult<NoticeResp>
/notice/my-notices|-|PermResult<List<NoticeService$UserNoticeItem>>
/notice/page|PageReq|PermResult<PaginatedResult<NoticeResp>>
/notice/publish|IdReq|PermResult<Void>
/notice/read|IdReq|PermResult<Void>
/notice/update|NoticeUpdateReq|PermResult<Void>
/oauth2/client/create|Oauth2ClientCreateReq|PermResult<Long>
/oauth2/client/delete|IdsReq|PermResult<Void>
/oauth2/client/detail|IdReq|PermResult<Oauth2ClientResp>
/oauth2/client/page|Oauth2ClientPageReq|PermResult<PaginatedResult<Oauth2ClientResp>>
/oauth2/client/update|Oauth2ClientUpdateReq|PermResult<Void>
/org-tree-config/create|OrgTreeConfigCreateReq|PermResult<Long>
/org-tree-config/delete|IdsReq|PermResult<Void>
/org-tree-config/detail|IdReq|PermResult<OrgTreeConfigResp>
/org-tree-config/page|PageReq|PermResult<PaginatedResult<OrgTreeConfigResp>>
/org-tree-config/set-default|IdReq|PermResult<Void>
/org-tree-config/update|OrgTreeConfigUpdateReq|PermResult<Void>
/org/create|OrgCreateReq|PermResult<Long>
/org/delete|IdReq|PermResult<Void>
/org/detail|IdReq|PermResult<OrgResp>
/org/page|OrgPageReq|PermResult<PaginatedResult<OrgResp>>
/org/tree|OrgQuery|PermResult<List<OrgResp>>
/org/update|OrgUpdateReq|PermResult<Void>
/org/users|IdReq|PermResult<List<OrgUserItemResp>>
/role/create|CreateRoleReq|PermResult<Long>
/role/grant-menu|RoleMenuReq|PermResult<Void>
/role/list|RoleListQueryReq|PermResult<ItemsResp<RoleListItemResp>>
/role/my-info|-|PermResult<UserInfoResp>
/role/revoke-menu|RoleMenuReq|PermResult<Void>
/user-org/assign|UserOrgAssignReq|PermResult<Void>
/user-org/list|IdReq|PermResult<List<UserPageItemResp$OrgBrief>>
/user-org/remove|UserOrgRemoveReq|PermResult<Void>
/user-org/set-primary|UserOrgSetPrimaryReq|PermResult<Void>
/user-role/assign|UserRoleAssignReq|PermResult<Void>
/user-role/list|UserRoleListReq|PermResult<ItemsResp<UserRoleItemResp>>
/user-role/revoke|UserRoleRevokeReq|PermResult<Void>
/user/create|UserCreateReq|PermResult<UserCreateResp>
/user/delete|IdsReq|PermResult<Void>
/user/detail|IdReq|PermResult<UserResp>
/user/enable|UserUpdateStatusReq|PermResult<Void>
/user/member-candidates|MemberCandidatesReq|PermResult<PaginatedResult<MemberCandidateItemResp>>
/user/page|UserPageReq|PermResult<PaginatedResult<UserPageItemResp>>
/user/reset-password|ResetPasswordReq|PermResult<ResetPasswordResp>
/user/update|UserUpdateReq|PermResult<Void>
/user/user-menus|IdReq|PermResult<UserInfoResp>
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

    /** 扫描 classpath 上全部 Controller 并拼装「路径|请求类型|响应类型」签名（枚举全部 path 值）。 */
    private Set<String> scanSignatures() throws Exception {
        Set<String> signatures = new TreeSet<>();
        for (Class<?> clazz : controllerClasses()) {
            RequestMapping classMapping =
                AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
            String base = "";
            if (classMapping != null && classMapping.path().length > 0) {
                base = classMapping.path()[0];
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
                        reqType = p.getType().getSimpleName();
                        break;
                    }
                }
                String respType = simpleTypeName(m.getGenericReturnType().getTypeName());
                for (String sub : merged.path()) {
                    String path = (base + sub).replace("//", "/");
                    signatures.add(path + "|" + reqType + "|" + respType);
                }
            }
        }
        return signatures;
    }

    /** 反复剥离包前缀（含泛型内部），得到全简单名形态（与快照生成口径一致）。 */
    private static String simpleTypeName(String typeName) {
        String prev;
        do {
            prev = typeName;
            typeName = typeName.replaceAll("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+\\.", "");
        } while (!prev.equals(typeName));
        return typeName;
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
