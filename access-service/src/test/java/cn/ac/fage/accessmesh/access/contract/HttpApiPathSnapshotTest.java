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
 * add-child/remove-child，2026-08-27 设计定案，apply-grant-plan 为唯一写入口）；T-PERM-025 增 action-options。
 * T-ACCESS-037 再删 admin /config 全族（page/detail/update/delete——僵尸端点退役，
 * system_config 管理单入口收敛到 system-config 端点族）。计数不写注释（去计数化）。
 * </p>
 * <p>
 * 契约断言封闭口径（评审修复：堵住空 method 数组与 path()[0] 逃逸）：
 * POST-only 按「方法必须标注 @PostMapping」判定（未限定方法的 @RequestMapping 违规）；
 * 路径枚举覆盖映射的全部 path 值（多路径映射不漏检）。
 * 统一响应：除 /file/download（二进制文件流）外，全部返回 R 包装。
 * DTO 签名：路径 → 请求体类型 | 响应泛型类型 快照精确比对（类型级漂移检测；
 * 字段级漂移由既有分散 DTO 测试与后续 T-PERM 任务覆盖，为登记限制）。
 * </p>
 */
class HttpApiPathSnapshotTest {

    private static final String BASE_PACKAGE = "cn.ac.fage.accessmesh.access";

    /** 归并后全量路径快照（POST + JSON Body；T-ACCESS-042 起 URL 单命名空间，外部路径=服务路径 /api/access/**、/api/example/**）。 */
    private static final Set<String> EXPECTED_PATHS = Set.of("""
/api/access/abstract-role/create
/api/access/abstract-role/detail
/api/access/abstract-role/full-sync
/api/access/abstract-role/list
/api/access/abstract-role/move
/api/access/abstract-role/remove
/api/access/abstract-role/sync
/api/access/abstract-role/tree
/api/access/abstract-role/update
/api/access/abstract-user/create
/api/access/abstract-user/detail
/api/access/abstract-user/full-sync
/api/access/abstract-user/list
/api/access/abstract-user/remove
/api/access/abstract-user/sync
/api/access/abstract-user/update
/api/access/auth/batch-check
/api/access/auth/check
/api/access/auth/check-interface
/api/access/auth/interface-snapshot
/api/access/auth/query-resources
/api/access/auth/query-scopes
/api/access/biz-domain/create
/api/access/biz-domain/detail
/api/access/biz-domain/list
/api/access/biz-domain/remove
/api/access/biz-domain/update
/api/access/conflict-rule/create
/api/access/conflict-rule/detail
/api/access/conflict-rule/detect
/api/access/conflict-rule/list
/api/access/conflict-rule/remove
/api/access/conflict-rule/update
/api/access/domain-config/detail
/api/access/domain-config/list
/api/access/domain-config/remove
/api/access/domain-config/save
/api/access/log/change/list
/api/access/log/operation/action-options
/api/access/log/operation/list
/api/access/operation-permission/create
/api/access/operation-permission/detail
/api/access/operation-permission/list
/api/access/operation-permission/remove
/api/access/operation-permission/update
/api/access/permission-condition/create
/api/access/permission-condition/detail
/api/access/permission-condition/list
/api/access/permission-condition/remove
/api/access/permission-condition/update
/api/access/permission-view/effective-permission-codes
/api/access/resource-api-mapping/create
/api/access/resource-api-mapping/list
/api/access/resource-api-mapping/remove
/api/access/resource-api-mapping/update
/api/access/integration/permission-manifest/full-sync
/api/access/resource-dependency/check
/api/access/resource-dependency/declaration-status
/api/access/resource-dependency/explain
/api/access/resource-dependency/graph
/api/access/resource-dependency/list
/api/access/resource-entity/batch-create
/api/access/resource-entity/create
/api/access/resource-entity/detail
/api/access/resource-entity/full-sync
/api/access/resource-entity/list
/api/access/resource-entity/move
/api/access/resource-entity/remove
/api/access/resource-entity/sync
/api/access/resource-entity/tree
/api/access/resource-entity/update
/api/access/role-resource-permission/apply-grant-plan
/api/access/role-resource-permission/list
/api/access/role-resource-permission/preview-grant-plan
/api/access/role-resource-permission/sub-perm-allowed-types
/api/access/service-config/apis
/api/access/service-config/detail
/api/access/service-config/list
/api/access/service-config/remove
/api/access/service-config/save
/api/access/service-config/sync
/api/access/service-credential/create
/api/access/service-credential/list
/api/access/service-credential/remove
/api/access/service-credential/update
/api/access/system-config/detail
/api/access/system-config/list
/api/access/system-config/save
/api/access/type-definition/create
/api/access/type-definition/detail
/api/access/type-definition/list
/api/access/type-definition/remove
/api/access/type-definition/update
/api/access/user-role/assign
/api/access/user-role/batch-assign
/api/access/user-role/full-sync
/api/access/user-role/list
/api/access/user-role/revoke
/api/access/user-role/sync
/api/access/auth/captcha
/api/access/auth/login
/api/access/auth/login/sms
/api/access/auth/logout
/api/access/auth/oauth2/authorize
/api/access/auth/oauth2/refresh
/api/access/auth/oauth2/revoke
/api/access/auth/oauth2/token
/api/access/auth/oauth2/userinfo
/api/access/auth/user-menu
/api/access/auth/userinfo
/api/access/dict/data/create
/api/access/dict/data/delete
/api/access/dict/data/list
/api/access/dict/data/update
/api/access/dict/type/create
/api/access/dict/type/delete
/api/access/dict/type/list
/api/access/dict/type/page
/api/access/file/delete
/api/access/file/detail
/api/access/file/download
/api/access/file/page
/api/access/file/upload
/api/access/job/create
/api/access/job/delete
/api/access/job/detail
/api/access/job/log/page
/api/access/job/page
/api/access/job/toggle
/api/access/job/trigger
/api/access/job/update
/api/access/login-log/page
/api/access/menu/create
/api/access/menu/delete
/api/access/menu/detail
/api/access/menu/tree
/api/access/menu/update
/api/access/notice/create
/api/access/notice/delete
/api/access/notice/detail
/api/access/notice/my-notices
/api/access/notice/page
/api/access/notice/publish
/api/access/notice/read
/api/access/notice/update
/api/access/oauth2/client/create
/api/access/oauth2/client/delete
/api/access/oauth2/client/detail
/api/access/oauth2/client/page
/api/access/oauth2/client/update
/api/access/org-tree-config/create
/api/access/org-tree-config/delete
/api/access/org-tree-config/detail
/api/access/org-tree-config/page
/api/access/org-tree-config/set-default
/api/access/org-tree-config/update
/api/access/org/create
/api/access/org/delete
/api/access/org/detail
/api/access/org/page
/api/access/org/tree
/api/access/org/update
/api/access/org/users
/api/access/role/list
/api/access/role/my-info
/api/access/user-org/assign
/api/access/user-org/list
/api/access/user-org/remove
/api/access/user-org/set-primary
/api/access/user-role/view
/api/access/user/create
/api/access/user/delete
/api/access/user/detail
/api/access/user/enable
/api/access/user/member-candidates
/api/access/user/page
/api/access/user/reset-password
/api/access/user/update
/api/access/user/user-menus
""".strip().split("\n"));

    /** 路径 → 请求体类型 | 响应类型 签名快照（类型级 DTO 契约；条数与 Controller 扫描强制一致）。 */
    private static final Set<String> EXPECTED_SIGNATURES = Set.of("""
/api/access/abstract-role/create|perm.common.dto.req.RoleCreateReq|common.model.R<access.role.dto.resp.RoleResp>
/api/access/abstract-role/detail|perm.common.dto.req.RoleDetailReq|common.model.R<access.role.dto.resp.RoleResp>
/api/access/abstract-role/full-sync|access.sync.dto.AbstractRoleFullSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/abstract-role/list|perm.common.dto.req.RoleListReq|common.model.R<perm.common.dto.resp.PageResp<access.role.dto.resp.RoleResp>>
/api/access/abstract-role/move|access.role.dto.req.RoleMoveReq|common.model.R<Void>
/api/access/abstract-role/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/abstract-role/sync|access.sync.dto.AbstractRoleSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/abstract-role/tree|access.role.dto.req.RoleTreeReq|common.model.R<perm.common.dto.resp.ItemsResp<access.role.dto.resp.RoleTreeResp>>
/api/access/abstract-role/update|access.role.dto.req.RoleUpdateReq|common.model.R<access.role.dto.resp.RoleResp>
/api/access/abstract-user/create|access.user.dto.req.AbstractUserCreateReq|common.model.R<access.user.dto.resp.AbstractUserResp>
/api/access/abstract-user/detail|perm.common.dto.req.IdReq|common.model.R<access.user.dto.resp.AbstractUserResp>
/api/access/abstract-user/full-sync|access.sync.dto.AbstractUserFullSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/abstract-user/list|access.user.dto.req.UserListReq|common.model.R<perm.common.dto.resp.PageResp<access.user.dto.resp.AbstractUserResp>>
/api/access/abstract-user/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/abstract-user/sync|access.sync.dto.AbstractUserSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/abstract-user/update|access.user.dto.req.AbstractUserUpdateReq|common.model.R<access.user.dto.resp.AbstractUserResp>
/api/access/auth/batch-check|perm.common.dto.req.BatchAuthCheckReq|common.model.R<access.engine.dto.BatchAuthCheckResp>
/api/access/auth/check-interface|perm.common.dto.req.CheckInterfaceReq|common.model.R<access.engine.dto.CheckInterfaceResp>
/api/access/auth/check|perm.common.dto.req.AuthCheckReq|common.model.R<access.engine.dto.AuthCheckResp>
/api/access/auth/interface-snapshot|perm.common.dto.req.InterfaceSnapshotReq|common.model.R<perm.common.dto.resp.InterfaceSnapshotResp>
/api/access/auth/query-resources|perm.common.dto.req.QueryResourcesReq|common.model.R<perm.common.dto.resp.QueryResourcesResp>
/api/access/auth/query-scopes|perm.common.dto.req.QueryScopesReq|common.model.R<perm.common.dto.resp.QueryScopesResp>
/api/access/biz-domain/create|access.domain.dto.req.BizDomainCreateReq|common.model.R<access.domain.dto.resp.BizDomainResp>
/api/access/biz-domain/detail|access.domain.dto.req.BizDomainDetailReq|common.model.R<access.domain.dto.resp.BizDomainResp>
/api/access/biz-domain/list|access.domain.dto.req.BizDomainListReq|common.model.R<perm.common.dto.resp.PageResp<access.domain.dto.resp.BizDomainResp>>
/api/access/biz-domain/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/biz-domain/update|access.domain.dto.req.BizDomainUpdateReq|common.model.R<access.domain.dto.resp.BizDomainResp>
/api/access/conflict-rule/create|access.rule.dto.req.ConflictRuleReq|common.model.R<access.rule.dto.resp.ConflictRuleResp>
/api/access/conflict-rule/detail|perm.common.dto.req.IdReq|common.model.R<access.rule.dto.resp.ConflictRuleResp>
/api/access/conflict-rule/detect|access.rule.dto.req.ConflictRuleDetectReq|common.model.R<access.rule.dto.resp.ConflictDetectResp>
/api/access/conflict-rule/list|access.infrastructure.dto.EmptyReq|common.model.R<perm.common.dto.resp.ItemsResp<access.rule.dto.resp.ConflictRuleResp>>
/api/access/conflict-rule/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/conflict-rule/update|access.rule.dto.req.ConflictRuleUpdateReq|common.model.R<access.rule.dto.resp.ConflictRuleResp>
/api/access/domain-config/detail|access.domain.dto.req.DomainConfigGetReq|common.model.R<access.domain.dto.resp.DomainConfigResp>
/api/access/domain-config/list|access.domain.dto.req.DomainConfigListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.domain.dto.resp.DomainConfigResp>>
/api/access/domain-config/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/domain-config/save|access.domain.dto.req.DomainConfigReq|common.model.R<access.domain.dto.resp.DomainConfigResp>
/api/access/log/change/list|access.audit.dto.req.ChangeLogListReq|common.model.R<perm.common.dto.resp.PageResp<access.audit.dto.resp.ChangeLogResp>>
/api/access/log/operation/action-options|access.audit.dto.req.LogActionOptionsReq|common.model.R<perm.common.dto.resp.ItemsResp<String>>
/api/access/log/operation/list|access.audit.dto.req.OperationLogListReq|common.model.R<perm.common.dto.resp.PageResp<access.audit.dto.resp.OperationLogResp>>
/api/access/operation-permission/create|access.type.dto.req.OperationCreateReq|common.model.R<access.type.dto.resp.OperationPermissionResp>
/api/access/operation-permission/detail|access.type.dto.req.OperationKeyReq|common.model.R<access.type.dto.resp.OperationPermissionResp>
/api/access/operation-permission/list|perm.common.dto.req.OperationListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.type.dto.resp.OperationPermissionResp>>
/api/access/operation-permission/remove|access.type.dto.req.OperationKeysReq|common.model.R<Void>
/api/access/operation-permission/update|access.type.dto.req.OperationUpdateReq|common.model.R<access.type.dto.resp.OperationPermissionResp>
/api/access/permission-condition/create|access.rule.dto.req.ConditionCreateReq|common.model.R<access.rule.dto.resp.ConditionResp>
/api/access/permission-condition/detail|access.rule.dto.req.ConditionDetailReq|common.model.R<access.rule.dto.resp.ConditionResp>
/api/access/permission-condition/list|access.rule.dto.req.ConditionListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.rule.dto.resp.ConditionResp>>
/api/access/permission-condition/remove|access.rule.dto.req.ConditionRemoveReq|common.model.R<Void>
/api/access/permission-condition/update|access.rule.dto.req.ConditionUpdateReq|common.model.R<access.rule.dto.resp.ConditionResp>
/api/access/permission-view/effective-permission-codes|perm.common.dto.req.UserEffectivePermissionCodesReq|common.model.R<perm.common.dto.resp.UserEffectivePermissionCodesResp>
/api/access/resource-api-mapping/create|access.resource.dto.req.ApiMappingAddReq|common.model.R<access.resource.dto.resp.ApiMappingResp>
/api/access/resource-api-mapping/list|access.resource.dto.req.ApiMappingListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ApiMappingResp>>
/api/access/resource-api-mapping/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/resource-api-mapping/update|access.resource.dto.req.ApiMappingUpdateReq|common.model.R<access.resource.dto.resp.ApiMappingResp>
/api/access/integration/permission-manifest/full-sync|perm.common.dto.req.PermissionManifestReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/resource-dependency/check|access.resource.dto.req.ResourceDependencyCheckReq|common.model.R<access.resource.dto.resp.DependencyCycleCheckResp>
/api/access/resource-dependency/declaration-status|access.resource.dto.req.DependencyDeclarationStatusReq|common.model.R<access.resource.dto.resp.DependencyDeclarationStatusResp>
/api/access/resource-dependency/explain|access.resource.dto.req.AutoGrantExplainReq|common.model.R<access.resource.dto.resp.AutoGrantExplainResp>
/api/access/resource-dependency/graph|access.resource.dto.req.DependencyListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ResourceDependencyResp>>
/api/access/resource-dependency/list|access.resource.dto.req.DependencyListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ResourceDependencyResp>>
/api/access/resource-entity/batch-create|perm.common.dto.req.ResourceBatchCreateReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ResourceResp>>
/api/access/resource-entity/create|perm.common.dto.req.ResourceCreateReq|common.model.R<access.resource.dto.resp.ResourceResp>
/api/access/resource-entity/detail|perm.common.dto.req.ResourceKeyReq|common.model.R<access.resource.dto.resp.ResourceResp>
/api/access/resource-entity/full-sync|access.sync.dto.ResourceEntityFullSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/resource-entity/list|access.resource.dto.req.ResourceListReq|common.model.R<perm.common.dto.resp.PageResp<access.resource.dto.resp.ResourceResp>>
/api/access/resource-entity/move|access.resource.dto.req.ResourceMoveReq|common.model.R<Void>
/api/access/resource-entity/remove|perm.common.dto.req.ResourceKeysReq|common.model.R<Void>
/api/access/resource-entity/sync|access.sync.dto.ResourceEntitySyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/resource-entity/tree|access.resource.dto.req.ResourceTreeReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ResourceTreeResp>>
/api/access/resource-entity/update|perm.common.dto.req.ResourceUpdateReq|common.model.R<access.resource.dto.resp.ResourceResp>
/api/access/role-resource-permission/apply-grant-plan|access.grant.dto.req.ApplyGrantPlanReq|common.model.R<access.grant.dto.resp.RolePermissionItemsResp>
/api/access/role-resource-permission/list|access.grant.dto.req.RolePermissionListReq|common.model.R<access.grant.dto.resp.RolePermissionItemsResp>
/api/access/role-resource-permission/preview-grant-plan|access.grant.dto.req.PreviewGrantPlanReq|common.model.R<access.grant.dto.resp.GrantPlanPreviewResp>
/api/access/role-resource-permission/sub-perm-allowed-types|access.grant.dto.req.SubPermAllowedTypesReq|common.model.R<access.grant.dto.resp.SubPermAllowedTypesResp>
/api/access/service-config/apis|access.resource.dto.req.ServiceConfigApisReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ApiMappingResp>>
/api/access/service-config/detail|access.resource.dto.req.ServiceConfigGetReq|common.model.R<access.resource.dto.resp.ServiceConfigResp>
/api/access/service-config/list|access.infrastructure.dto.EmptyReq|common.model.R<perm.common.dto.resp.ItemsResp<access.resource.dto.resp.ServiceConfigResp>>
/api/access/service-config/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/service-config/save|access.resource.dto.req.ServiceConfigReq|common.model.R<access.resource.dto.resp.ServiceConfigResp>
/api/access/service-credential/create|access.infrastructure.credential.dto.req.ServiceCredentialCreateReq|common.model.R<access.infrastructure.credential.dto.resp.ServiceCredentialCreateResp>
/api/access/service-credential/list|access.infrastructure.credential.dto.req.ServiceCredentialListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.infrastructure.credential.dto.resp.ServiceCredentialResp>>
/api/access/service-credential/remove|common.model.IdReq|common.model.R<Void>
/api/access/service-credential/update|access.infrastructure.credential.dto.req.ServiceCredentialUpdateReq|common.model.R<access.infrastructure.credential.dto.resp.ServiceCredentialResp>
/api/access/service-config/sync|access.resource.dto.req.ServiceConfigSyncReq|common.model.R<access.resource.dto.resp.ServiceConfigSyncResp>
/api/access/system-config/detail|access.platform.dto.req.SystemConfigGetReq|common.model.R<access.platform.dto.resp.SystemConfigResp>
/api/access/system-config/list|access.platform.dto.req.SystemConfigListReq|common.model.R<perm.common.dto.resp.PageResp<access.platform.dto.resp.SystemConfigResp>>
/api/access/system-config/save|access.platform.dto.req.SystemConfigReq|common.model.R<access.platform.dto.resp.SystemConfigResp>
/api/access/type-definition/create|access.type.dto.req.TypeCreateReq|common.model.R<access.type.dto.resp.TypeDefinitionResp>
/api/access/type-definition/detail|perm.common.dto.req.IdReq|common.model.R<access.type.dto.resp.TypeDefinitionResp>
/api/access/type-definition/list|access.type.dto.req.TypeListReq|common.model.R<perm.common.dto.resp.PageResp<access.type.dto.resp.TypeDefinitionResp>>
/api/access/type-definition/remove|perm.common.dto.req.IdsReq|common.model.R<Void>
/api/access/type-definition/update|access.type.dto.req.TypeUpdateReq|common.model.R<access.type.dto.resp.TypeDefinitionResp>
/api/access/user-role/assign|perm.common.dto.req.UserAssignRoleReq|common.model.R<Void>
/api/access/user-role/batch-assign|access.role.dto.req.UserRoleBatchAssignReq|common.model.R<Void>
/api/access/user-role/full-sync|access.sync.dto.UserRoleFullSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/user-role/list|perm.common.dto.req.UserRoleListReq|common.model.R<access.role.dto.resp.UserRolesResp>
/api/access/user-role/revoke|perm.common.dto.req.UserRoleBatchRevokeReq|common.model.R<Void>
/api/access/user-role/sync|access.sync.dto.UserRoleSyncReq|common.model.R<perm.common.dto.resp.SyncResultResp>
/api/access/auth/captcha|-|common.model.R<access.auth.dto.CaptchaResp>
/api/access/auth/login/sms|access.auth.dto.SmsLoginReq|common.model.R<access.auth.dto.LoginResp>
/api/access/auth/login|access.auth.dto.LoginReq|common.model.R<access.auth.dto.LoginResp>
/api/access/auth/logout|-|common.model.R<Void>
/api/access/auth/oauth2/authorize|access.auth.dto.AuthorizeReq|common.model.R<access.auth.dto.AuthorizeResp>
/api/access/auth/oauth2/refresh|access.auth.controller.OAuth2Controller$RefreshTokenReq|common.model.R<access.auth.dto.TokenResp>
/api/access/auth/oauth2/revoke|access.auth.controller.OAuth2Controller$RevokeTokenReq|common.model.R<Void>
/api/access/auth/oauth2/token|access.auth.dto.TokenReq|common.model.R<access.auth.dto.TokenResp>
/api/access/auth/oauth2/userinfo|-|common.model.R<access.auth.dto.OAuth2UserInfoResp>
/api/access/auth/user-menu|-|common.model.R<access.menu.dto.resp.UserMenuResp>
/api/access/auth/userinfo|-|common.model.R<access.auth.dto.UserInfoResp>
/api/access/dict/data/create|access.platform.dto.req.DictDataCreateReq|common.model.R<Long>
/api/access/dict/data/delete|common.model.IdReq|common.model.R<Void>
/api/access/dict/data/list|common.model.IdReq|common.model.R<perm.common.dto.resp.ItemsResp<access.platform.dto.resp.DictDataResp>>
/api/access/dict/data/update|access.platform.dto.req.DictDataUpdateReq|common.model.R<Void>
/api/access/dict/type/create|access.platform.dto.req.DictTypeCreateReq|common.model.R<Long>
/api/access/dict/type/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/dict/type/list|-|common.model.R<perm.common.dto.resp.ItemsResp<access.platform.dto.resp.DictTypeResp>>
/api/access/dict/type/page|common.model.PageReq|common.model.R<perm.common.dto.resp.PageResp<access.platform.dto.resp.DictTypeResp>>
/api/access/file/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/file/detail|common.model.IdReq|common.model.R<access.platform.dto.resp.FileResp>
/api/access/file/download|common.model.IdReq|void
/api/access/file/page|access.platform.dto.req.FilePageReq|common.model.R<perm.common.dto.resp.PageResp<access.platform.dto.resp.FileResp>>
/api/access/file/upload|-|common.model.R<Long>
/api/access/job/create|access.platform.dto.req.JobCreateReq|common.model.R<Long>
/api/access/job/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/job/detail|common.model.IdReq|common.model.R<access.platform.dto.resp.JobResp>
/api/access/job/log/page|access.platform.dto.req.JobLogPageReq|common.model.R<perm.common.dto.resp.PageResp<access.platform.dto.resp.JobLogResp>>
/api/access/job/page|common.model.PageReq|common.model.R<perm.common.dto.resp.PageResp<access.platform.dto.resp.JobResp>>
/api/access/job/toggle|access.platform.controller.JobController$ToggleJobReq|common.model.R<Void>
/api/access/job/trigger|common.model.IdReq|common.model.R<Void>
/api/access/job/update|access.platform.dto.req.JobUpdateReq|common.model.R<Void>
/api/access/login-log/page|common.model.PageReq|common.model.R<perm.common.dto.resp.PageResp<access.audit.dto.resp.LoginLogResp>>
/api/access/menu/create|access.menu.dto.req.MenuCreateReq|common.model.R<Long>
/api/access/menu/delete|common.model.IdReq|common.model.R<Void>
/api/access/menu/detail|common.model.IdReq|common.model.R<access.menu.dto.resp.MenuResp>
/api/access/menu/tree|-|common.model.R<perm.common.dto.resp.ItemsResp<access.menu.dto.resp.MenuResp>>
/api/access/menu/update|access.menu.dto.req.MenuUpdateReq|common.model.R<Void>
/api/access/notice/create|access.platform.dto.req.NoticeCreateReq|common.model.R<Long>
/api/access/notice/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/notice/detail|common.model.IdReq|common.model.R<access.platform.dto.resp.NoticeResp>
/api/access/notice/my-notices|-|common.model.R<perm.common.dto.resp.ItemsResp<access.platform.service.NoticeAppService$UserNoticeItem>>
/api/access/notice/page|common.model.PageReq|common.model.R<perm.common.dto.resp.PageResp<access.platform.dto.resp.NoticeResp>>
/api/access/notice/publish|common.model.IdReq|common.model.R<Void>
/api/access/notice/read|common.model.IdReq|common.model.R<Void>
/api/access/notice/update|access.platform.dto.req.NoticeUpdateReq|common.model.R<Void>
/api/access/oauth2/client/create|access.auth.dto.Oauth2ClientCreateReq|common.model.R<Long>
/api/access/oauth2/client/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/oauth2/client/detail|common.model.IdReq|common.model.R<access.auth.dto.Oauth2ClientResp>
/api/access/oauth2/client/page|access.auth.dto.Oauth2ClientPageReq|common.model.R<perm.common.dto.resp.PageResp<access.auth.dto.Oauth2ClientResp>>
/api/access/oauth2/client/update|access.auth.dto.Oauth2ClientUpdateReq|common.model.R<Void>
/api/access/org-tree-config/create|access.org.dto.req.OrgTreeConfigCreateReq|common.model.R<Long>
/api/access/org-tree-config/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/org-tree-config/detail|common.model.IdReq|common.model.R<access.org.dto.resp.OrgTreeConfigResp>
/api/access/org-tree-config/page|common.model.PageReq|common.model.R<perm.common.dto.resp.PageResp<access.org.dto.resp.OrgTreeConfigResp>>
/api/access/org-tree-config/set-default|common.model.IdReq|common.model.R<Void>
/api/access/org-tree-config/update|access.org.dto.req.OrgTreeConfigUpdateReq|common.model.R<Void>
/api/access/org/create|access.org.dto.req.OrgCreateReq|common.model.R<Long>
/api/access/org/delete|common.model.IdReq|common.model.R<Void>
/api/access/org/detail|common.model.IdReq|common.model.R<access.org.dto.resp.OrgResp>
/api/access/org/page|access.org.dto.req.OrgPageReq|common.model.R<perm.common.dto.resp.PageResp<access.org.dto.resp.OrgResp>>
/api/access/org/tree|access.org.dto.req.OrgQuery|common.model.R<perm.common.dto.resp.ItemsResp<access.org.dto.resp.OrgResp>>
/api/access/org/update|access.org.dto.req.OrgUpdateReq|common.model.R<Void>
/api/access/org/users|common.model.IdReq|common.model.R<perm.common.dto.resp.ItemsResp<access.org.dto.resp.OrgUserItemResp>>
/api/access/role/list|access.role.controller.AdminRoleController$RoleListQueryReq|common.model.R<perm.common.dto.resp.ItemsResp<access.role.dto.resp.RoleListItemResp>>
/api/access/role/my-info|-|common.model.R<access.auth.dto.UserInfoResp>
/api/access/user-org/assign|access.org.dto.req.UserOrgAssignReq|common.model.R<Void>
/api/access/user-org/list|common.model.IdReq|common.model.R<perm.common.dto.resp.ItemsResp<access.user.dto.resp.UserPageItemResp$OrgBrief>>
/api/access/user-org/remove|access.org.dto.req.UserOrgRemoveReq|common.model.R<Void>
/api/access/user-org/set-primary|access.org.dto.req.UserOrgSetPrimaryReq|common.model.R<Void>
/api/access/user-role/view|access.role.dto.req.UserRoleListReq|common.model.R<perm.common.dto.resp.ItemsResp<access.role.dto.resp.UserRoleItemResp>>
/api/access/user/create|access.user.dto.req.UserCreateReq|common.model.R<access.user.dto.resp.UserCreateResp>
/api/access/user/delete|access.infrastructure.dto.IdsReq|common.model.R<Void>
/api/access/user/detail|common.model.IdReq|common.model.R<access.user.dto.resp.UserResp>
/api/access/user/enable|access.user.dto.req.UserUpdateStatusReq|common.model.R<Void>
/api/access/user/member-candidates|access.user.dto.req.MemberCandidatesReq|common.model.R<perm.common.dto.resp.PageResp<access.user.dto.resp.MemberCandidateItemResp>>
/api/access/user/page|access.user.dto.req.UserPageReq|common.model.R<perm.common.dto.resp.PageResp<access.user.dto.resp.UserPageItemResp>>
/api/access/user/reset-password|access.user.dto.req.ResetPasswordReq|common.model.R<access.user.dto.resp.ResetPasswordResp>
/api/access/user/update|access.user.dto.req.UserUpdateReq|common.model.R<Void>
/api/access/user/user-menus|common.model.IdReq|common.model.R<access.auth.dto.UserInfoResp>
""".strip().split("\n"));

    /** 统一响应包装的唯一白名单：二进制文件流直出（void）。 */
    private static final Set<String> NON_WRAPPER_WHITELIST = Set.of("/api/access/file/download");

    /** 已按设计决策退役的路径前缀/路径（快照必须不含；负向防回归）。 */
    private static final List<String> RETIRED_PATHS = List.of(
        "/api/access/resource-dependency/create",
        "/api/access/resource-dependency/update",
        "/api/access/resource-dependency/remove",
        "/api/access/resource-dependency/batch-sync",
        "/sync-task/list", "/sync-task/page", "/sync-task/detail", "/sync-task/delete",
        "/sync-task/due", "/sync-task/reset", "/sync-task/retry-now", "/sync-task/mark-success",
        "/sync-task/mark-failed", "/sync-task/batch-status", "/sync-task/rebuild-from-fact",
        "/audit-log/page",
        // T-PERM-043：GROUP_ROLE 写入口删除（含读接口 list，唯一生产者 add 从未成功写入）
        "/api/access/abstract-role/extra-roles/add",
        "/api/access/abstract-role/extra-roles/list",
        "/api/access/abstract-role/extra-roles/remove",
        // T-ADMIN-024：admin 侧角色写代理删除（无存量调用方，不留兼容层）。
        // T-ACCESS-042 起按历史裸形态断言——统一命名空间后 /api/access/user-role/assign|revoke
        // 是权限轨存活端点，新形态断言不可用；裸形态回归=家族前缀风格复活，仍须拒绝
        "/role/create",
        "/role/grant-menu",
        "/role/revoke-menu",
        "/user-role/assign",
        "/user-role/revoke",
        // T-PERM-034：role-resource-permission 旧写入口删除（2026-08-27 端点退役收口，
        // 契约终态=apply-grant-plan 唯一写入口；无存量调用方，授权页 v3.1 已走 apply-grant-plan）
        "/api/access/role-resource-permission/save",
        "/api/access/role-resource-permission/revoke",
        "/api/access/role-resource-permission/children",
        "/api/access/role-resource-permission/add-child",
        "/api/access/role-resource-permission/remove-child",
        // T-ACCESS-037：admin /config 僵尸端点退役（前端/e2e/gateway 主代码零消费；
        // system_config 管理单入口收敛到 /api/access/system-config）
        "/config/page",
        "/config/detail",
        "/config/update",
        "/config/delete"
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
     * 评审修复：仓库存在多个跨包同名 DTO（admin 与 perm-common 两个 UserRoleListReq、
     * common.model.IdReq 与 perm.common.dto.req.IdReq），仅比较简单类名
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
    @DisplayName("统一响应：除 /file/download 文件流白名单外，全部返回 R 包装")
    void allMappingsUseUnifiedResponseWrapper() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String sig : scanSignatures()) {
            String[] parts = sig.split("\\|");
            String path = parts[0];
            String respType = parts[2];
            if (NON_WRAPPER_WHITELIST.contains(path)) {
                continue;
            }
            if (!respType.startsWith("common.model.R<")) {
                violations.add(path + " -> " + respType);
            }
        }
        assertThat(violations).as("统一响应体 {code,message,data,...}（common R 包装）").isEmpty();
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
    @DisplayName("退役接口负向断言：RETIRED_PATHS 全部路径（/sync-task/*、/audit-log/page、extra-roles/*、admin 侧角色写代理 5 条、role-resource-permission 旧写入口 5 条、admin /config 4 条）无任何 Controller 映射")
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

    @Test
    @DisplayName("URL 单命名空间（T-ACCESS-042）：全部 Controller 路径必须以 /api/ 开头")
    void allPathsUnderApiNamespace() throws Exception {
        Set<String> actual = new TreeSet<>();
        scanSignatures().forEach(s -> actual.add(s.substring(0, s.indexOf('|'))));
        List<String> violations = new ArrayList<>();
        for (String path : actual) {
            if (!path.startsWith("/api/access/")) {
                violations.add(path);
            }
        }
        assertThat(violations)
            .as("旧形态负向锁：裸管理面路径（/user/**、/auth/** 等）、/api/perm/** 前缀与双前缀形态一律不得回归")
            .isEmpty();
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
