// 角色管理 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 RoleResp / RoleTreeResp / RoleSummaryResp
// 契约依据：docs/design/permission-center/api-contract.md §5.2 / §6.10.3
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

// ========== Mock 数据：5 种角色类型树 ==========

/**
 * 角色树 mock（对齐 RoleTreeResp，data.items[0].root 为根节点森林）。
 *
 * 结构对齐后端 `selectValidRoleTree`（mapper，查 tenant_id + delete_flag=0 全部
 * 有效角色，含禁用——T-PERM-022 起 status 为展示字段）+ `TreeBuilder` 按 parentId 组装：
 * 根 = parentId=null 的真实角色，**无类型虚拟根**。
 *
 * 类型说明（overview §角色模型 + schema access-service.sql）：
 * - ORG / POSITION：由组织同步自动生成
 * - PERSONAL：由用户同步连带创建（PERSONAL_{external_id}）
 * - BASIC_ROLE：功能角色，角色管理页可 CRUD（首期唯一，T-PERM-043）
 * - GROUP_ROLE：写入口已删除（T-PERM-043），前端隐藏；mock 树数据保留供历史参考
 *
 * mock 返回全部 5 种类型的扁平森林（模拟后端全量返回），前端 hook 按本页范围
 * 过滤为仅 BASIC_ROLE 展示（ORG/POSITION/PERSONAL/GROUP_ROLE 归权限授予/用户详情或隐藏）。
 */
const mockRoleTree = {
  id: 0,
  tenantId: 1,
  parentId: null,
  roleTypeCode: "ROOT",
  name: "角色树",
  externalId: null,
  status: 1,
  sortOrder: 0,
  children: [
    // BASIC_ROLE：基础角色（森林根，parentId=null）
    {
      id: 101,
      tenantId: 1,
      parentId: null,
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
      parentId: null,
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
      parentId: null,
      roleTypeCode: "BASIC_ROLE",
      name: "访客",
      externalId: "BASIC_203",
      status: 0,
      sortOrder: 3,
      children: []
    },
    // GROUP_ROLE：分组角色（含一组父子嵌套示例验证树层级）
    {
      id: 201,
      tenantId: 1,
      parentId: null,
      roleTypeCode: "GROUP_ROLE",
      name: "核心开发组",
      externalId: "GROUP_401",
      status: 1,
      sortOrder: 4,
      children: [
        {
          id: 203,
          tenantId: 1,
          parentId: 201,
          roleTypeCode: "GROUP_ROLE",
          name: "核心开发-后端",
          externalId: "GROUP_403",
          status: 1,
          sortOrder: 1,
          children: []
        }
      ]
    },
    {
      id: 202,
      tenantId: 1,
      parentId: null,
      roleTypeCode: "GROUP_ROLE",
      name: "运维保障组",
      externalId: "GROUP_402",
      status: 1,
      sortOrder: 5,
      children: []
    },
    // PERSONAL：个人角色（由用户同步生成，前端过滤不展示）
    {
      id: 301,
      tenantId: 1,
      parentId: null,
      roleTypeCode: "PERSONAL",
      name: "张三-专属",
      externalId: "PERSONAL_501",
      status: 1,
      sortOrder: 6,
      children: []
    },
    // ORG：组织角色（由组织同步生成，前端过滤不展示）
    {
      id: 401,
      tenantId: 1,
      parentId: null,
      roleTypeCode: "ORG",
      name: "研发中心",
      externalId: "ORG_1",
      status: 1,
      sortOrder: 7,
      children: []
    },
    // POSITION：岗位角色（由组织同步生成，前端过滤不展示）
    {
      id: 501,
      tenantId: 1,
      parentId: null,
      roleTypeCode: "POSITION",
      name: "后端开发",
      externalId: "POSITION_30",
      status: 1,
      sortOrder: 8,
      children: []
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

/** enabledOnly=true 时递归裁掉禁用节点（ROOT 容器保留，禁用节点子树整棵不挂载） */
function filterDisabledTree(node) {
  if (node.roleTypeCode !== "ROOT" && node.status !== 1) {
    return null;
  }
  return {
    ...node,
    children: (node.children || []).map(filterDisabledTree).filter(Boolean)
  };
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

/**
 * 拍平树为 RoleResp 列表（用于 list 接口）。
 * 跳过 ROOT 容器节点，收集全部真实角色（含 externalId 为空的真实角色）。
 */
function flattenToRoleList(node, acc = []) {
  for (const child of node.children || []) {
    if (child.roleTypeCode === "ROOT") {
      // ROOT 是 mock 容器（对齐 data.items[0].root），不入列表
      flattenToRoleList(child, acc);
      continue;
    }
    acc.push({
      id: child.id,
      tenantId: child.tenantId,
      parentId: child.parentId,
      roleTypeCode: child.roleTypeCode,
      roleTypeName: ROLE_TYPE_NAME[child.roleTypeCode] || child.roleTypeCode,
      externalId: child.externalId,
      name: child.name,
      status: child.status,
      sortOrder: child.sortOrder,
      extra: child.extra ?? null,
      createdAt: "2026-06-01T08:00:00",
      updatedAt: "2026-06-20T08:00:00"
    });
    flattenToRoleList(child, acc);
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
    response: ({ body }) => {
      // T-PERM-022：enabledOnly=true 过滤启用角色（禁用节点整棵裁掉，对齐后端
      // SQL 行过滤 + TreeBuilder 孤儿不挂载语义）；默认返回全部有效角色
      const { enabledOnly } = body || {};
      const root = enabledOnly ? filterDisabledTree(mockRoleTree) : mockRoleTree;
      return ok({ items: [{ root }] });
    }
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
        // parentId 为空 = 顶层角色（对齐后端 parentId=null 语义）
        parentId: parentId ?? null,
        roleTypeCode,
        name,
        externalId: externalId ?? null,
        status: 1,
        sortOrder,
        extra: extra ?? null,
        children: []
      };
      // mock 树外层是 ROOT 容器（对齐 data.items[0].root）；parentId 为空挂到 ROOT 下作顶层
      const targetParentId = parentId ?? mockRoleTree.id;
      insertChild(mockRoleTree, targetParentId, newNode);
      return ok({
        id: newId,
        tenantId: 1,
        parentId: newNode.parentId,
        roleTypeCode,
        roleTypeName: ROLE_TYPE_NAME[roleTypeCode] || roleTypeCode,
        externalId: newNode.externalId,
        name,
        status: 1,
        sortOrder,
        extra: newNode.extra,
        createdAt: "2026-06-30T08:00:00",
        updatedAt: "2026-06-30T08:00:00"
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
      // parentId 为空 = 移到顶层（对齐后端 parentId=null 语义）
      node.parentId = parentId ?? null;
      const targetParentId = parentId ?? mockRoleTree.id;
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
  // 查询角色详情（T-PERM-022：业务键二元组定位，对齐后端 RoleDetailReq）
  {
    url: "/api/perm/abstract-role/detail",
    method: "post",
    response: ({ body }) => {
      const { roleTypeCode, roleExternalId } = body || {};
      const node = findNodeByExternalId(mockRoleTree, roleExternalId, roleTypeCode);
      // ROOT 是 mock 容器，非真实角色，视为不存在；真实角色（含空 externalId）正常返回
      if (!node || node.roleTypeCode === "ROOT") {
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
