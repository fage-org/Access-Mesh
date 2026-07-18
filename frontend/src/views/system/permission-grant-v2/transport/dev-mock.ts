/**
 * V2 transport dev 内存实现（T-FE-029 骨架 + 基础单分支 fixture）。
 *
 * 隔离机制：不经全局 http client，绕开 vite-plugin-fake-server 对
 * /api/perm/role-resource-permission/* 的路由接管；不注册任何 fake route
 * 到全局 mock 路由表。
 *
 * T-FE-029 范围：仅 getRolePermissionList 提供基础单分支 fixture（供 selectRole
 * 加载快照验证 D1/D2/D3 门控与上下文切换）。
 * save/children/add-child/remove-child 为桩，抛"未实现"--
 * T-FE-035 扩展多条件分支返回 + 失败模拟。
 *
 * 类型契约复用 @/api/permission-grant（import type），不引入运行时 src/api 依赖。
 */
import type {
  RolePermissionListReq,
  RolePermissionListResp,
  RolePermissionItem,
  RolePermissionSaveReq,
  RolePermissionSaveResp,
  ChildPermissionQueryReq,
  ChildPermissionQueryResp,
  AddChildReq,
  AddChildResp,
  RemoveChildReq,
  RemoveChildResp,
  OperatorCapability,
  DomainCapability
} from "@/api/permission-grant";
import type { V2GrantTransport } from "./types";

// 基础单分支 fixture：一个 MENU VIEW 直接授权（无条件分支）
const BASELINE_ITEMS: RolePermissionItem[] = [
  {
    id: 9001,
    domainCode: "",
    resourceTypeCode: "MENU",
    resourceCode: "system",
    codeType: "MENU",
    resourceName: "系统管理",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  }
];

const DEFAULT_DOMAIN_CAPABILITY: DomainCapability = {
  supportsChildren: false,
  childResourceTypeCodes: []
};

const DEFAULT_OPERATOR_CAPABILITY: OperatorCapability = {
  canManage: true,
  grantableResourceTypeCodes: ["MENU", "BUTTON", "API", "DATA"],
  grantableOperationCodes: ["VIEW", "CREATE", "UPDATE", "DELETE", "MANAGE"]
};

function notImplemented(method: string): never {
  throw new Error(
    `V2 transport dev-mock: ${method} 未实现（T-FE-035 扩展多条件+失败模拟）`
  );
}

export function createDevMockTransport(): V2GrantTransport {
  return {
    async getRolePermissionList(
      data: RolePermissionListReq
    ): Promise<RolePermissionListResp> {
      // 基础单分支 fixture：返回固定 baseline，domainCode 用请求域补齐
      // T-FE-035 扩展多条件分支返回 + 失败模拟
      return {
        items: BASELINE_ITEMS.map(it => ({
          ...it,
          domainCode: data.domainCode
        })),
        domainCapability: DEFAULT_DOMAIN_CAPABILITY,
        operatorCapability: DEFAULT_OPERATOR_CAPABILITY
      };
    },
    async saveRolePermission(
      _data: RolePermissionSaveReq
    ): Promise<RolePermissionSaveResp> {
      notImplemented("saveRolePermission");
    },
    async getChildPermissions(
      _data: ChildPermissionQueryReq
    ): Promise<ChildPermissionQueryResp> {
      notImplemented("getChildPermissions");
    },
    async addChildPermission(_data: AddChildReq): Promise<AddChildResp> {
      notImplemented("addChildPermission");
    },
    async removeChildPermission(
      _data: RemoveChildReq
    ): Promise<RemoveChildResp> {
      notImplemented("removeChildPermission");
    }
  };
}
