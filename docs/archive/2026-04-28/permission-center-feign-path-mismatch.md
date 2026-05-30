# Archived / 非权威来源

> 本文档是历史问题记录，已归档。内容可能包含旧路径、旧字段或已废弃契约。
> 当前权限中心 API 以 `plan/permission-center/api-contract.md` 为准，当前实现设计以 `plan/permission-center/implementation.md` 为准。

# Problem: Permission center coupling — admin-service binds to menu-specific Feign methods

## Status: RESOLVED (commit TBD)

## Root Cause

`RoleProxyServiceImpl` calls three Feign methods that do not exist on `PermissionFeignClient`:
- `permissionFeignClient.createRole(roleName, orgId, tenantId)` ❌
- `permissionFeignClient.grantMenuToRole(roleId, menuId)` ❌
- `permissionFeignClient.revokeMenuFromRole(roleId, menuId)` ❌

The current `PermissionFeignClient` only has `checkPermission()`.

Additionally, the method names (`grantMenuToRole`) expose admin-specific "menu" concepts,
making the proxy layer non-generic. permission-center already provides generic APIs
(resource CRUD, role CRUD, batch-grant/revoke) — they just aren't wired through Feign.

## What was fixed

### 1. Shared DTOs in `perm-common`
- `DefaultOpCode` enum (VIEW, EDIT, DELETE)
- Request DTOs: `RoleCreateReq`, `ResourceCreateReq`, `ResourceUpdateReq`, `IdWithTenantReq`,
  `OperationListReq`, `RoleGrantReq`, `BatchRevokeReq`, `UserPermissionViewReq`
- Response DTOs: `UserRolesResp`, `UserPermissionViewResp`, `OperationPermissionResp`

### 2. PermissionFeignClient — complete proxy
All methods now map to **existing** permission-center endpoints (no new controller endpoints needed):
- `checkPermission` → `/internal/perm/check`
- `createRole` → `/api/role/create`
- `getUserRoles` → `/api/user/roles`
- `getUserPermissions` → `/api/permission-view/user`
- `createResource/updateResource/deleteResource` → `/api/resource/*`
- `listOperations` → `/api/operation/list`
- `batchGrant/batchRevoke` → `/api/role-permission/*`

### 3. RoleProxyService — generic API
- Renamed `grantMenuToRole` → `grantResourceToRole(tenantId, roleId, resourceId, opCode)`
- Renamed `revokeMenuFromRole` → `revokeResourceFromRole(tenantId, roleId, resourceId)`
- Implementation resolves opCode → operationPermissionId via `listOperations`, cached per tenant

### 4. MenuServiceImpl — auto-sync menu ↔ resource
- `createMenu`: syncs to permission-center as MENU resource, saves `perm_resource_id`
- `updateMenu`: updates resource if perm_resource_id exists
- `deleteMenu`: deletes resource from permission-center

### 5. RoleController — updated callers
- `grantMenu`/`revokeMenu` look up `menu.permResourceId` and call generic proxy methods
- `RoleMenuReq` now includes `tenantId`

## Remaining TODO

- `revokeResourceFromRole`: permission-center's `batch-revoke` expects permission IDs
  (from `role_resource_permission` table), not resource IDs. A lookup step is needed
  to map resourceId → permission IDs for the specific role. Currently throws
  `UnsupportedOperationException` with a clear message.

- `loadUserRolesAndPermissions`: roles and permissions fields still return empty
  because user↔permission-center identity mapping (externalId) needs to be wired
  through the user sync flow first.

## Files Modified

### Created
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/enums/DefaultOpCode.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/RoleCreateReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/ResourceCreateReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/ResourceUpdateReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/IdWithTenantReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/OperationListReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/RoleGrantReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/BatchRevokeReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/req/UserPermissionViewReq.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/resp/UserRolesResp.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/resp/UserPermissionViewResp.java`
- `perm-sdk/perm-common/src/main/java/cn/ac/fage/accessmesh/perm/common/dto/resp/OperationPermissionResp.java`

### Modified
- `perm-sdk/perm-common/pom.xml` — added spring-boot-starter-validation dependency
- `perm-sdk/perm-client-spring-boot-starter/src/main/java/.../PermissionFeignClient.java`
- `admin-service/src/main/java/.../RoleProxyService.java`
- `admin-service/src/main/java/.../RoleProxyServiceImpl.java`
- `admin-service/src/main/java/.../MenuServiceImpl.java`
- `admin-service/src/main/java/.../RoleController.java`
