---
doc_type: design
title: DTO 字段对账覆盖
status: adopted
domain: cross-service
last_reviewed: 2026-10-06
---

# DTO 字段对账覆盖

`pnpm -C frontend contracts:check` 对比下面的公开字段集合、服务端 Java record、前端 TypeScript 导出及存在的 SDK 副本。注册入口为 `tools/contracts/dto-fields.json`，新增或删除字段须同步调用方与本表；CI 不自动改写任何一方。Java 只取 record 组件，TypeScript 经编译器解析别名/交集，不按同名自动猜配。该检查锁定字段名称，不代替类型、可空性、嵌套形状和序列化行为测试。

RolePermissionItemResp 与其列表信封已由 perm-common 单源供服务端和 SDK 使用；AuthCheckResp/BatchAuthCheckResp 保留双副本，另由 CheckFamilyWireShapeTest 检查包括子项在内的形状。共用 perm-common 的请求与分页信封无需再造 Java 副本。

已锁家族见下表。未进入本表的用户/组织/登录菜单树、嵌套授权预览、依赖诊断等前端模型仍由各自行为测试或人工契约核对覆盖，不宣称全仓镜像均受字段对账保护；新增镜像时优先登记本检查。HTTP 路径及请求/响应类型由 HttpApiPathSnapshotTest 管理。

| DTO | 字段集合 |
|---|---|
| `RolePermissionItemResp` | canGrant, childCount, codeType, conditionCode, createdAt, dependOn, grantSource, grantedBits, id, operationCode, resourceCode, resourceName, resourceTypeCode, scopeMode |
| `TypeDefinitionResp` | createdAt, description, extra, id, isSystem, name, sortOrder, tenantId, typeCode, typeKey, typeValue |
| `SystemConfigResp` | configKey, configValue, description, id, isSystem, tenantId, updatedAt |
| `RoleResp` | createdAt, externalId, extra, id, name, parentId, roleTypeCode, roleTypeName, sortOrder, status, tenantId, updatedAt |
| `ResourceResp` | code, codeType, createdAt, extra, id, name, parentId, path, resourceTypeCode, resourceTypeName, status, tenantId, updatedAt |
| `OperationPermissionResp` | binaryBit, code, createdAt, id, inheritMask, name, resourceTypeCode, resourceTypeName, tenantId, updatedAt |
| `ConditionResp` | code, conditionRules, createdAt, description, enabled, gatewayEvaluable, id, name, source, tenantId, updatedAt |
| `ConflictRuleResp` | conflictType, createdAt, description, firstAbstractRoleId, firstOperationPermissionId, id, resourceTypeValue, secondAbstractRoleId, secondOperationPermissionId, tenantId, updatedAt |
| `BizDomainResp` | code, createdAt, description, global, id, name, tenantId |
| `DomainConfigResp` | bizDomainId, configType, extra, id, tenantId, updatedAt |
| `ServiceConfigResp` | basePath, createdAt, description, extra, id, name, serviceCode, status, tenantId, updatedAt |
| `OperationLogResp` | action, createdAt, id, ipAddress, module, operatorId, operatorName, requestId, summary, targetId, targetType, tenantId |
| `ChangeLogResp` | affectedAbstractRoleIds, affectedAbstractUserIds, changeReason, changeSource, createdAt, createdBy, diffSnapshot, entityId, entityType, id, newSnapshot, oldSnapshot, operation, requestId, tenantId |
| `TypeCreateReq` | description, extra, name, ownerRoleExternalId, ownerRoleTypeCode, sortOrder, typeCode, typeKey |
| `TypeUpdateReq` | description, descriptionClear, extra, extraClear, name, sortOrder, typeId |
| `RoleCreateReq` | externalId, extra, name, parentId, roleTypeCode, sortOrder |
| `RoleUpdateReq` | extra, extraClear, name, roleId, sortOrder, status |
| `ResourceCreateReq` | code, codeType, extra, name, parentCodeType, parentDomainCode, parentId, parentResourceCode, parentResourceTypeCode, path, resourceTypeCode, status |
| `ResourceUpdateReq` | code, codeType, extra, extraClear, name, path, resourceTypeCode, status |
| `ConditionCreateReq` | code, conditionRules, description, enabled, gatewayEvaluable, name |
| `ConditionUpdateReq` | code, conditionRules, description, descriptionClear, enabled, gatewayEvaluable, name |
| `DomainConfigReq` | configType, domainCode, extra |
| `SystemConfigReq` | configKey, configValue, description, descriptionClear |
| `OperationLogListReq` | action, module, operatorId, pageNum, pageSize, requestId, since, targetType, until |
| `AuthCheckResp` | allowed, conditionEvaluated, matchedPermissionIds, matchedRoleIds, reason |
| `BatchAuthCheckResp` | items |
| `AuthorizeReq` | clientId, codeChallenge, codeChallengeMethod, redirectUri, responseType, scope, state |
| `AuthorizationPreviewResp` | clientId, clientName, redirectUri, scopes, state |
| `ServiceCredentialResp` | createdAt, credentialId, expiresAt, id, rotatedAt, serviceCode, status |
| `ServiceCredentialCreateResp` | credentialId, expiresAt, id, secret, serviceCode, status |
| `ServiceCredentialCreateReq` | expiresAt, serviceCode |
| `ServiceCredentialUpdateReq` | expiresAt, id, status |
| `LoginLogResp` | clientId, failReason, id, ipAddress, location, loginAt, loginType, status, userAgent, username |
| `SyncStatusResp` | entityKind, lastFullGeneration, lastFullStatus, maxGeneration, scopeKey, sourceService, trackedItems, updatedAt |
| `SyncStatusListReq` | pageNum, pageSize, sourceService |
| `PlatformAccountResp` | createdAt, forceResetPwd, id, name, status, updatedAt, username |
| `PlatformLoginResp` | accessToken, account, expiresIn |
| `TenantResp` | accessState, adminUserId, code, createdAt, id, name, status, updatedAt |
| `PlatformAuditResp` | action, createdAt, id, ipAddress, operatorId, operatorName, outcome, requestId, summary, targetId, targetTenantId, targetType |
| `TenantAdminPasswordResp` | password, userStatus, username |
| `IssuedPasswordResp` | password |
| `TenantCreatedResp` | adminUsername, initialPassword, tenant |
| `PlatformAccountCreatedResp` | account, initialPassword |
