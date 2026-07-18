/**
 * V2 transport 类型契约 -- 可变授权 role-resource-permission/* 的请求/响应接口。
 *
 * 类型复用 @/api/permission-grant（import type），保持单一类型事实源；
 * 运行时无 src/api 依赖（类型擦除），不引入第二事实源。
 *
 * 承载范围：仅 role-resource-permission/*（list/save/children/add-child/remove-child）。
 * 共享只读端点（abstract-role/tree、type-definition/list、resource-entity/tree、
 * operation-permission/list、permission-condition/list）不进 transport，由 V2 hook
 * 直接复用 @/api/permission-grant 共享函数。
 */
import type {
  RolePermissionListReq,
  RolePermissionListResp,
  RolePermissionSaveReq,
  RolePermissionSaveResp,
  ChildPermissionQueryReq,
  ChildPermissionQueryResp,
  AddChildReq,
  AddChildResp,
  RemoveChildReq,
  RemoveChildResp
} from "@/api/permission-grant";

/** V2 可变授权 transport 接口（承载 role-resource-permission/*） */
export interface V2GrantTransport {
  getRolePermissionList(
    data: RolePermissionListReq
  ): Promise<RolePermissionListResp>;
  saveRolePermission(
    data: RolePermissionSaveReq
  ): Promise<RolePermissionSaveResp>;
  getChildPermissions(
    data: ChildPermissionQueryReq
  ): Promise<ChildPermissionQueryResp>;
  addChildPermission(data: AddChildReq): Promise<AddChildResp>;
  removeChildPermission(data: RemoveChildReq): Promise<RemoveChildResp>;
}
