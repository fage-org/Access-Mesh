// 角色管理 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 permission-center 的 RoleResp / RoleTreeResp / RoleSummaryResp
// 契约依据：docs/design/permission-center/api-contract.md §5.2 / §6.10.3
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

// ========== Mock 数据：5 种角色类型树 ==========

/**
 * 角色树 mock（对齐 RoleTreeResp，data.items[0].root 为根节点）。
 *
 * 类型说明（overview §角色模型 + schema permission-center.sql:103）：
 * - ORG / POSITION：由组织同步自动生成
 * - PERSONAL：由用户同步连带创建（PERSONAL_{external_id}）
 * - BASIC_ROLE / GROUP_ROLE：功能角色，角色管理页可 CRUD
 *
 * mock 返回全部 5 种类型（模拟后端全量返回），前端 hook 按本页范围
 * 过滤为仅 BASIC_ROLE / GROUP_ROLE 展示（ORG/POSITION/PERSONAL 归权限授予/用户详情）。
 * 结构：按 roleTypeCode 分组的虚拟根 → 真实角色节点。
 */
const mockRoleTree = {
  id: 0,
  tenantId: 1,
  parentId: null,
  roleTypeCode: "ROOT",
  name: "角色类型",
  externalId: null,
  status: 1,
  sortOrder: 0,
  children: [
    {
      id: 100,
      tenantId: 1,
      parentId: 0,
      roleTypeCode: "BASIC_ROLE",
      name: "基础角色",
      externalId: null,
      status: 1,
      sortOrder: 1,
      children: [
        {
          id: 101,
          tenantId: 1,
          parentId: 100,
          roleTypeCode: "BASIC_ROLE",
          name: "基础用户",
          externalId: "BASIC_201",
          status: 1,
          sortOrder: 1,
          children: []
        },
        {
          id: 102,
          tenantId: 1,
          parentId: 100,
          roleTypeCode: "BASIC_ROLE",
          name: "高级用户",
          externalId: "BASIC_202",
          status: 1,
          sortOrder: 2,
          children: []
        },
        {
          id: 103,
          tenantId: 1,
          parentId: 100,
          roleTypeCode: "BASIC_ROLE",
          name: "访客",
          externalId: "BASIC_203",
          status: 0,
          sortOrder: 3,
          children: []
        }
      ]
    },
    {
      id: 200,
      tenantId: 1,
      parentId: 0,
      roleTypeCode: "GROUP_ROLE",
      name: "分组角色",
      externalId: null,
      status: 1,
      sortOrder: 2,
      children: [
        {
          id: 201,
          tenantId: 1,
          parentId: 200,
          roleTypeCode: "GROUP_ROLE",
          name: "核心开发组",
          externalId: "GROUP_401",
          status: 1,
          sortOrder: 1,
          children: []
        },
        {
          id: 202,
          tenantId: 1,
          parentId: 200,
          roleTypeCode: "GROUP_ROLE",
          name: "运维保障组",
          externalId: "GROUP_402",
          status: 1,
          sortOrder: 2,
          children: []
        }
      ]
    },
    {
      id: 300,
      tenantId: 1,
      parentId: 0,
      roleTypeCode: "PERSONAL",
      name: "个人角色",
      externalId: null,
      status: 1,
      sortOrder: 3,
      children: [
        {
          id: 301,
          tenantId: 1,
          parentId: 300,
          roleTypeCode: "PERSONAL",
          name: "张三-专属",
          externalId: "PERSONAL_501",
          status: 1,
          sortOrder: 1,
          children: []
        }
      ]
    },
    {
      id: 400,
      tenantId: 1,
      parentId: 0,
      roleTypeCode: "ORG",
      name: "组织角色（只读）",
      externalId: null,
      status: 1,
      sortOrder: 4,
      children: [
        {
          id: 401,
          tenantId: 1,
          parentId: 400,
          roleTypeCode: "ORG",
          name: "研发中心",
          externalId: "ORG_1",
          status: 1,
          sortOrder: 1,
          children: []
        }
      ]
    },
    {
      id: 500,
      tenantId: 1,
      parentId: 0,
      roleTypeCode: "POSITION",
      name: "岗位角色（只读）",
      externalId: null,
      status: 1,
      sortOrder: 5,
      children: [
        {
          id: 501,
          tenantId: 1,
          parentId: 500,
          roleTypeCode: "POSITION",
          name: "后端开发",
          externalId: "POSITION_30",
          status: 1,
          sortOrder: 1,
          children: []
        }
      ]
    }
  ]
};

/** 分组角色额外基本角色 mock（GROUP_ROLE.id → 额外角色摘要列表） */
const mockExtraRoles: Record<
  number,
  Array<{ id: number; roleTypeCode: string; externalId: string; name: string }>
> = {
  201: [
    {
      id: 101,
      roleTypeCode: "BASIC_ROLE",
      externalId: "BASIC_201",
      name: "基础用户"
    },
    {
      id: 102,
      roleTypeCode: "BASIC_ROLE",
      externalId: "BASIC_202",
      name: "高级用户"
    }
  ],
  202: [
    {
      id: 101,
      roleTypeCode: "BASIC_ROLE",
      externalId: "BASIC_201",
      name: "基础用户"
    }
  ]
};

// ========== Mock 辅助 ==========

let _nextRoleId = 10000;
function nextRoleId() {
  return _nextRoleId++;
}

/** 在树中递归查找节点 */
function findNode(node, id) {
  if (node.id === id) return node;
  for (const child of node.children || []) {
    const found = findNode(child, id);
    if (found) return found;
  }
  return null;
}

/** 在树中递归查找父节点并插入子节点 */
function insertChild(node, parentId, newNode) {
  if (node.id === parentId) {
    node.children = node.children || [];
    node.children.push(newNode);
    return true;
  }
  for (const child of node.children || []) {
    if (insertChild(child, parentId, newNode)) return true;
  }
  return false;
}

/** 在树中递归删除节点（返回是否删除） */
function removeNode(node, id) {
  const children = node.children || [];
  const idx = children.findIndex(c => c.id === id);
  if (idx >= 0) {
    children.splice(idx, 1);
    return true;
  }
  for (const child of children) {
    if (removeNode(child, id)) return true;
  }
  return false;
}

/** 拍平树为 RoleResp 列表（用于 list 接口，排除虚拟类型根） */
function flattenToRoleList(node, acc = []) {
  for (const child of node.children || []) {
    if (child.roleTypeCode !== "ROOT") {
      // 虚拟类型根（如「基础角色」分组）不入列表，仅真实角色节点入
      if (child.externalId !== null) {
        acc.push({
          id: child.id,
          tenantId: child.tenantId,
          parentId: child.parentId,
          roleTypeCode: child.roleTypeCode,
          roleTypeName:
            ROLE_TYPE_NAME[child.roleTypeCode] || child.roleTypeCode,
          externalId: child.externalId,
          name: child.name,
          status: child.status,
          sortOrder: child.sortOrder,
          extra: child.extra ?? null,
          createdAt: "2026-06-01T08:00:00",
          updatedAt: "2026-06-20T08:00:00"
        });
      }
      flattenToRoleList(child, acc);
    }
  }
  return acc;
}

const ROLE_TYPE_NAME = {
  ORG: "组织角色",
  POSITION: "岗位角色",
  BASIC_ROLE: "基础角色",
  GROUP_ROLE: "分组角色",
  PERSONAL: "个人角色"
};

/** 按 keyword / roleTypeCode(s) 过滤 */
function filterRoles(list, { keyword, roleTypeCode, roleTypeCodes }) {
  return list.filter(r => {
    if (roleTypeCode && r.roleTypeCode !== roleTypeCode) return false;
    if (
      roleTypeCodes &&
      roleTypeCodes.length &&
      !roleTypeCodes.includes(r.roleTypeCode)
    )
      return false;
    if (
      keyword &&
      !r.name.includes(keyword) &&
      !(r.externalId || "").includes(keyword)
    )
      return false;
    return true;
  });
}

// ========== Mock 路由 ==========

export default defineFakeRoute([
  // 查询角色树
  {
    url: "/api/perm/abstract-role/tree",
    method: "post",
    response: () => ok({ items: [{ root: mockRoleTree }] })
  },
  // 分页查询角色列表
  {
    url: "/api/perm/abstract-role/list",
    method: "post",
    response: ({ body }) => {
      const {
        domainCode: _domainCode,
        roleTypeCode,
        roleTypeCodes,
        keyword,
        pageNum = 1,
        pageSize = 200,
        sort: _sort
      } = body || {};
      const all = flattenToRoleList(mockRoleTree);
      const filtered = filterRoles(all, {
        keyword,
        roleTypeCode,
        roleTypeCodes
      });
      const total = filtered.length;
      const start = (pageNum - 1) * pageSize;
      const items = filtered.slice(start, start + pageSize);
      return ok({
        items,
        total,
        pageNum,
        pageSize,
        hasNext: start + items.length < total
      });
    }
  },
  // 创建角色
  {
    url: "/api/perm/abstract-role/create",
    method: "post",
    response: ({ body }) => {
      const {
        parentId,
        roleTypeCode,
        externalId,
        name,
        sortOrder = 0,
        extra
      } = body || {};
      const newId = nextRoleId();
      const newNode = {
        id: newId,
        tenantId: 1,
        parentId: parentId ?? null,
        roleTypeCode,
        name,
        externalId: externalId ?? null,
        status: 1,
        sortOrder,
        extra: extra ?? null,
        children: []
      };
      // parentId 为空时挂到对应类型虚拟根下
      const targetParentId = parentId ?? TYPE_ROOT_ID[roleTypeCode] ?? 0;
      insertChild(mockRoleTree, targetParentId, newNode);
      return ok({
        id: newId,
        tenantId: 1,
        parentId: targetParentId,
        roleTypeCode,
        roleTypeName: ROLE_TYPE_NAME[roleTypeCode] || roleTypeCode,
        externalId: newNode.externalId,
        name,
        status: 1,
        sortOrder,
        extra: newNode.extra,
        createdAt: "2026-06-29T08:00:00",
        updatedAt: "2026-06-29T08:00:00"
      });
    }
  },
  // 更新角色
  {
    url: "/api/perm/abstract-role/update",
    method: "post",
    response: ({ body }) => {
      const { roleId, name, status, sortOrder, extra } = body || {};
      const node = findNode(mockRoleTree, roleId);
      if (!node) return { code: 404, message: "角色不存在", data: null };
      if (name !== undefined) node.name = name;
      if (status !== undefined) node.status = status;
      if (sortOrder !== undefined) node.sortOrder = sortOrder;
      if (extra !== undefined) node.extra = extra;
      return ok({
        id: node.id,
        tenantId: node.tenantId,
        parentId: node.parentId,
        roleTypeCode: node.roleTypeCode,
        roleTypeName: ROLE_TYPE_NAME[node.roleTypeCode] || node.roleTypeCode,
        externalId: node.externalId,
        name: node.name,
        status: node.status,
        sortOrder: node.sortOrder,
        extra: node.extra,
        createdAt: "2026-06-01T08:00:00",
        updatedAt: "2026-06-29T08:00:00"
      });
    }
  },
  // 移动角色
  {
    url: "/api/perm/abstract-role/move",
    method: "post",
    response: ({ body }) => {
      const { roleId, parentId } = body || {};
      const node = findNode(mockRoleTree, roleId);
      if (!node) return { code: 404, message: "角色不存在", data: null };
      removeNode(mockRoleTree, roleId);
      node.parentId = parentId ?? null;
      const targetParentId = parentId ?? TYPE_ROOT_ID[node.roleTypeCode] ?? 0;
      insertChild(mockRoleTree, targetParentId, node);
      return ok(null);
    }
  },
  // 删除角色（批量）
  {
    url: "/api/perm/abstract-role/remove",
    method: "post",
    response: ({ body }) => {
      const { ids = [] } = body || {};
      for (const id of ids) {
        removeNode(mockRoleTree, id);
        delete mockExtraRoles[id];
      }
      return ok(null);
    }
  },
  // 查询角色详情（🔧 后端现用 IdReq{id}，mock 同步）
  {
    url: "/api/perm/abstract-role/detail",
    method: "post",
    response: ({ body }) => {
      const { id } = body || {};
      const node = findNode(mockRoleTree, id);
      if (!node || node.externalId === null) {
        return { code: 404, message: "角色不存在", data: null };
      }
      return ok({
        id: node.id,
        tenantId: node.tenantId,
        parentId: node.parentId,
        roleTypeCode: node.roleTypeCode,
        roleTypeName: ROLE_TYPE_NAME[node.roleTypeCode] || node.roleTypeCode,
        externalId: node.externalId,
        name: node.name,
        status: node.status,
        sortOrder: node.sortOrder,
        extra: node.extra,
        createdAt: "2026-06-01T08:00:00",
        updatedAt: "2026-06-20T08:00:00"
      });
    }
  },
  // 查询分组角色额外基本角色
  {
    url: "/api/perm/abstract-role/extra-roles/list",
    method: "post",
    response: ({ body }) => {
      const { groupRoleExternalId } = body || {};
      // mock 用 externalId 反查分组角色 id（真后端用业务键定位）
      const groupNode = findNodeByExternalId(
        mockRoleTree,
        groupRoleExternalId,
        "GROUP_ROLE"
      );
      const items = groupNode ? mockExtraRoles[groupNode.id] || [] : [];
      return ok({ items });
    }
  },
  // 分组角色添加基本角色
  {
    url: "/api/perm/abstract-role/extra-roles/add",
    method: "post",
    response: ({ body }) => {
      const { groupRoleExternalId, basicRoleExternalId, basicRoleTypeCode } =
        body || {};
      const groupNode = findNodeByExternalId(
        mockRoleTree,
        groupRoleExternalId,
        "GROUP_ROLE"
      );
      const basicNode = findNodeByExternalId(
        mockRoleTree,
        basicRoleExternalId,
        basicRoleTypeCode
      );
      if (!groupNode || !basicNode)
        return { code: 404, message: "角色不存在", data: null };
      if (!mockExtraRoles[groupNode.id]) mockExtraRoles[groupNode.id] = [];
      const exists = mockExtraRoles[groupNode.id].some(
        r => r.id === basicNode.id
      );
      if (!exists) {
        mockExtraRoles[groupNode.id].push({
          id: basicNode.id,
          roleTypeCode: basicNode.roleTypeCode,
          externalId: basicNode.externalId,
          name: basicNode.name
        });
      }
      return ok(null);
    }
  },
  // 分组角色移除基本角色
  {
    url: "/api/perm/abstract-role/extra-roles/remove",
    method: "post",
    response: ({ body }) => {
      const { groupRoleExternalId, basicRoleExternalId } = body || {};
      const groupNode = findNodeByExternalId(
        mockRoleTree,
        groupRoleExternalId,
        "GROUP_ROLE"
      );
      if (groupNode && mockExtraRoles[groupNode.id]) {
        mockExtraRoles[groupNode.id] = mockExtraRoles[groupNode.id].filter(
          r => r.externalId !== basicRoleExternalId
        );
      }
      return ok(null);
    }
  }
]);

/** 类型 → 虚拟根 id 映射（parentId 为空时挂载点） */
const TYPE_ROOT_ID = {
  BASIC_ROLE: 100,
  GROUP_ROLE: 200,
  PERSONAL: 300,
  ORG: 400,
  POSITION: 500
};

/** 按 externalId + roleTypeCode 查找节点 */
function findNodeByExternalId(node, externalId, roleTypeCode) {
  if (node.externalId === externalId && node.roleTypeCode === roleTypeCode) {
    return node;
  }
  for (const child of node.children || []) {
    const found = findNodeByExternalId(child, externalId, roleTypeCode);
    if (found) return found;
  }
  return null;
}
