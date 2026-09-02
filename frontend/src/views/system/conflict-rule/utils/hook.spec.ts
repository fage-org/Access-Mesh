/**
 * 冲突规则页引用数据装载回归（外部评审收口）：
 * 1) 角色分页循环——旧实现只取首页（pageSize=200 即后端单页上限），超页角色静默截断；
 * 2) 三路请求独立成败——旧实现 Promise.all 一损俱损，单路失败清空全部选择器。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const getRoleList = vi.fn();
const getOperationList = vi.fn();
const getTypeDefList = vi.fn();

// 阻断 hook/types 模块级的 store/router/element-plus 链（同 role/types.spec.ts 范式）
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));
vi.mock("@/utils/auth", () => ({ hasPerms: () => true }));
vi.mock("@/utils/message", () => ({ message: vi.fn() }));

vi.mock("@/api/role-manage", () => ({
  getRoleList: (...args: unknown[]) => getRoleList(...args)
}));
vi.mock("@/api/resource-operation", () => ({
  getOperationList: (...args: unknown[]) => getOperationList(...args)
}));
vi.mock("@/api/type-def", () => ({
  getTypeDefList: (...args: unknown[]) => getTypeDefList(...args),
  TYPE_KEY: { RESOURCE_TYPE: "resource_type" }
}));

import { loadAllRoles, loadConflictRefData } from "./hook";

function role(id: number) {
  return { id, name: `role-${id}`, sortOrder: id } as any;
}

describe("冲突规则页引用数据装载", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loadAllRoles 按 hasNext 循环拉全分页（旧实现只取首页必失败）", async () => {
    getRoleList
      .mockResolvedValueOnce({
        items: Array.from({ length: 200 }, (_, i) => role(i + 1)),
        total: 201,
        pageNum: 1,
        pageSize: 200,
        hasNext: true
      })
      .mockResolvedValueOnce({
        items: [role(201)],
        total: 201,
        pageNum: 2,
        pageSize: 200,
        hasNext: false
      });

    const roles = await loadAllRoles();
    expect(roles).toHaveLength(201);
    expect(getRoleList).toHaveBeenCalledTimes(2);
    expect(getRoleList).toHaveBeenLastCalledWith({
      roleTypeCodes: ["BASIC_ROLE", "GROUP_ROLE"],
      pageNum: 2,
      pageSize: 200
    });
    // 第 201 个角色必须可入选（旧实现首页截断后丢失）
    expect(roles.some(r => r.id === 201)).toBe(true);
  });

  it("单路失败不清空其余成功路（旧实现 Promise.all 一损俱损必失败）", async () => {
    getRoleList.mockResolvedValue({
      items: [role(1)],
      total: 1,
      pageNum: 1,
      pageSize: 200,
      hasNext: false
    });
    getOperationList.mockRejectedValue(new Error("403 无操作查看权限"));
    getTypeDefList.mockResolvedValue({
      items: [{ id: 9, typeKey: "resource_type", typeValue: 1, name: "菜单" }]
    });

    const { roles, operations, typeDefs } = await loadConflictRefData();
    expect(roles).toHaveLength(1);
    expect(operations).toEqual([]);
    expect(typeDefs).toHaveLength(1);
    expect(typeDefs[0].typeValue).toBe(1);
  });
});
