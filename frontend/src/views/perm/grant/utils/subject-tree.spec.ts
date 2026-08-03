/**
 * 主体树构造纯函数测试（评审问题 5：递归保留真实 children + 虚拟容器）。
 */
import { describe, it, expect } from "vitest";
import type { RoleTreeNode } from "@/api/role-manage";
import {
  buildExtraContainer,
  decideRefreshAction,
  filterVisibleTree
} from "./subject-tree";

function makeNode(over: Partial<RoleTreeNode>): RoleTreeNode {
  return {
    id: 1,
    tenantId: 1,
    parentId: null,
    roleTypeCode: "BASIC_ROLE",
    name: "n",
    externalId: "E1",
    status: 1,
    sortOrder: 0,
    children: [],
    ...over
  };
}

describe("filterVisibleTree（评审问题 5）", () => {
  it("过滤非角色类型，仅保留 BASIC_ROLE/GROUP_ROLE 且 kind=ROLE", () => {
    const roots = [
      makeNode({
        id: 101,
        roleTypeCode: "BASIC_ROLE",
        externalId: "B1",
        name: "基础"
      }),
      makeNode({
        id: 301,
        roleTypeCode: "PERSONAL",
        externalId: "P1",
        name: "个人"
      }),
      makeNode({
        id: 401,
        roleTypeCode: "ORG",
        externalId: "O1",
        name: "组织"
      }),
      makeNode({
        id: 501,
        roleTypeCode: "POSITION",
        externalId: "PO1",
        name: "岗位"
      })
    ];
    const result = filterVisibleTree(roots);
    expect(result).toHaveLength(1);
    expect(result[0].roleTypeCode).toBe("BASIC_ROLE");
    expect(result[0].kind).toBe("ROLE");
  });

  it("ROOT 容器透明下钻，不入结果", () => {
    const root = makeNode({
      id: 0,
      roleTypeCode: "ROOT",
      externalId: null,
      name: "角色树",
      children: [
        makeNode({
          id: 101,
          roleTypeCode: "BASIC_ROLE",
          externalId: "B1",
          name: "基础"
        })
      ]
    });
    const result = filterVisibleTree([root]);
    expect(result).toHaveLength(1);
    expect(result[0].externalId).toBe("B1");
  });

  it("递归保留嵌套真实 children（GROUP_401 -> GROUP_403）", () => {
    const nested = makeNode({
      id: 201,
      roleTypeCode: "GROUP_ROLE",
      externalId: "GROUP_401",
      name: "核心开发组",
      children: [
        makeNode({
          id: 203,
          roleTypeCode: "GROUP_ROLE",
          externalId: "GROUP_403",
          name: "核心开发-后端",
          children: []
        })
      ]
    });
    const result = filterVisibleTree([nested]);
    expect(result).toHaveLength(1);
    expect(result[0].children).toHaveLength(1);
    expect(result[0].children![0].externalId).toBe("GROUP_403");
    expect(result[0].children![0].kind).toBe("ROLE");
  });

  it("停用角色保留（status=0，展示层标记）", () => {
    const result = filterVisibleTree([
      makeNode({
        id: 103,
        roleTypeCode: "BASIC_ROLE",
        externalId: "B3",
        name: "访客",
        status: 0
      })
    ]);
    expect(result).toHaveLength(1);
    expect(result[0].status).toBe(0);
  });

  it("children 默认空数组（无嵌套时）", () => {
    const result = filterVisibleTree([
      makeNode({
        id: 101,
        roleTypeCode: "BASIC_ROLE",
        externalId: "B1",
        name: "基础",
        children: []
      })
    ]);
    expect(result[0].children).toEqual([]);
  });
});

describe("buildExtraContainer（评审问题 5）", () => {
  it("构造虚拟容器：EXTRA_CONTAINER 不可选，子节点 EXTRA_ROLE 带分组来源", () => {
    const container = buildExtraContainer("GROUP_401", "核心开发组", [
      { externalId: "BASIC_201", name: "基础用户" },
      { externalId: "BASIC_202", name: "高级用户" }
    ]);
    expect(container.kind).toBe("EXTRA_CONTAINER");
    expect(container.externalId).toBeNull();
    expect(container.name).toBe("关联基础角色");
    expect(container.children).toHaveLength(2);
    const child = container.children![0];
    expect(child.kind).toBe("EXTRA_ROLE");
    expect(child.externalId).toBe("BASIC_201");
    expect(child.roleTypeCode).toBe("BASIC_ROLE");
    expect(child.expandedFromGroup).toBe(true);
    expect(child.groupRoleName).toBe("核心开发组");
    expect(child.key).toBe("role:BASIC_201@GROUP_401");
  });

  it("空 basics 列表：容器存在但无子节点", () => {
    const container = buildExtraContainer("GROUP_402", "运维保障组", []);
    expect(container.kind).toBe("EXTRA_CONTAINER");
    expect(container.children).toEqual([]);
  });
});

describe("decideRefreshAction（评审问题 6）", () => {
  it("query 存在 -> preset（一次性入口指令优先）", () => {
    const action = decideRefreshAction({
      queryExternalId: "BASIC_201",
      context: { roleExternalId: "BASIC_202", roleTypeCode: "BASIC_ROLE" },
      node: { name: "其他角色" }
    });
    expect(action).toEqual({ kind: "preset", externalId: "BASIC_201" });
  });

  it("无 query + context 存在 + 角色仍在树中 -> syncName（同步改名不重载 baseline）", () => {
    const action = decideRefreshAction({
      queryExternalId: null,
      context: { roleExternalId: "BASIC_201", roleTypeCode: "BASIC_ROLE" },
      node: { name: "基础用户（新名）" }
    });
    expect(action).toEqual({
      kind: "syncName",
      displayName: "基础用户（新名）"
    });
  });

  it("无 query + context 存在 + 角色已删除 -> clearSubject", () => {
    const action = decideRefreshAction({
      queryExternalId: undefined,
      context: { roleExternalId: "BASIC_201", roleTypeCode: "BASIC_ROLE" },
      node: null
    });
    expect(action).toEqual({ kind: "clearSubject" });
  });

  it("无 query + 无 context -> none", () => {
    const action = decideRefreshAction({
      queryExternalId: null,
      context: null,
      node: null
    });
    expect(action).toEqual({ kind: "none" });
  });

  it("空字符串 query 视为无 query（router.replace 后触发）", () => {
    const action = decideRefreshAction({
      queryExternalId: "",
      context: null,
      node: null
    });
    expect(action).toEqual({ kind: "none" });
  });
});
