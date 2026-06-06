// 用户管理 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 admin-service 的 OrgResp / OrgTreeConfigResp / UserPageItemResp / OrgBrief
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

// ========== Mock 数据 ==========

const mockOrgTree = [
  {
    id: 1,
    orgName: "根组织",
    code: "root",
    parentOrgId: null,
    orgType: 1,
    status: 1,
    sort: 0,
    children: [
      {
        id: 2,
        orgName: "研发中心",
        code: "rd",
        parentOrgId: 1,
        orgType: 1,
        status: 1,
        sort: 1,
        children: [
          {
            id: 4,
            orgName: "后端组",
            code: "rd-backend",
            parentOrgId: 2,
            orgType: 1,
            status: 1,
            sort: 1,
            children: []
          },
          {
            id: 5,
            orgName: "前端组",
            code: "rd-frontend",
            parentOrgId: 2,
            orgType: 1,
            status: 1,
            sort: 2,
            children: []
          }
        ]
      },
      {
        id: 3,
        orgName: "市场部",
        code: "marketing",
        parentOrgId: 1,
        orgType: 1,
        status: 1,
        sort: 2,
        children: []
      },
      {
        id: 6,
        orgName: "财务部",
        code: "finance",
        parentOrgId: 1,
        orgType: 1,
        status: 1,
        sort: 3,
        children: []
      }
    ]
  }
];

const mockTeamTree = [
  {
    id: 7,
    orgName: "核心团队",
    code: "core-team",
    parentOrgId: null,
    orgType: 1,
    status: 1,
    sort: 0,
    children: [
      {
        id: 8,
        orgName: "产品组",
        code: "product",
        parentOrgId: 7,
        orgType: 1,
        status: 1,
        sort: 1,
        children: []
      },
      {
        id: 9,
        orgName: "设计组",
        code: "design",
        parentOrgId: 7,
        orgType: 1,
        status: 1,
        sort: 2,
        children: []
      }
    ]
  }
];

const mockUsers = [
  {
    id: 1,
    username: "zhangsan",
    name: "张三",
    phone: "13800001001",
    email: "zhangsan@example.com",
    status: 1,
    orgs: [
      { orgId: 2, orgName: "研发中心", isPrimary: true },
      { orgId: 4, orgName: "后端组", isPrimary: false }
    ],
    createdAt: "2026-01-15T08:00:00"
  },
  {
    id: 2,
    username: "lisi",
    name: "李四",
    phone: "13800001002",
    email: "lisi@example.com",
    status: 1,
    orgs: [
      { orgId: 2, orgName: "研发中心", isPrimary: true },
      { orgId: 5, orgName: "前端组", isPrimary: false }
    ],
    createdAt: "2026-02-01T08:00:00"
  },
  {
    id: 3,
    username: "wangwu",
    name: "王五",
    phone: "13800001003",
    email: "wangwu@example.com",
    status: 1,
    orgs: [
      { orgId: 2, orgName: "研发中心", isPrimary: true },
      { orgId: 4, orgName: "后端组", isPrimary: false }
    ],
    createdAt: "2026-01-20T08:00:00"
  },
  {
    id: 4,
    username: "zhaoliu",
    name: "赵六",
    phone: "13800001004",
    email: "zhaoliu@example.com",
    status: 1,
    orgs: [
      { orgId: 2, orgName: "研发中心", isPrimary: true },
      { orgId: 4, orgName: "后端组", isPrimary: false }
    ],
    createdAt: "2026-03-01T08:00:00"
  },
  {
    id: 5,
    username: "sunqi",
    name: "孙七",
    phone: "13800001005",
    email: "sunqi@example.com",
    status: 0,
    orgs: [
      { orgId: 2, orgName: "研发中心", isPrimary: true },
      { orgId: 5, orgName: "前端组", isPrimary: false }
    ],
    createdAt: "2026-01-10T08:00:00"
  },
  {
    id: 6,
    username: "zhouba",
    name: "周八",
    phone: "13800001006",
    email: "zhouba@example.com",
    status: 1,
    orgs: [{ orgId: 3, orgName: "市场部", isPrimary: true }],
    createdAt: "2026-02-15T08:00:00"
  },
  {
    id: 7,
    username: "wujiu",
    name: "吴九",
    phone: "13800001007",
    email: "wujiu@example.com",
    status: 1,
    orgs: [{ orgId: 3, orgName: "市场部", isPrimary: true }],
    createdAt: "2026-03-10T08:00:00"
  },
  {
    id: 8,
    username: "zhengshi",
    name: "郑十",
    phone: "13800001008",
    email: "zhengshi@example.com",
    status: 1,
    orgs: [{ orgId: 3, orgName: "市场部", isPrimary: true }],
    createdAt: "2026-01-05T08:00:00"
  },
  {
    id: 9,
    username: "chenyi",
    name: "陈一",
    phone: "13800001009",
    email: "chenyi@example.com",
    status: 1,
    orgs: [{ orgId: 6, orgName: "财务部", isPrimary: true }],
    createdAt: "2026-02-20T08:00:00"
  },
  {
    id: 10,
    username: "liuer",
    name: "刘二",
    phone: "13800001010",
    email: "liuer@example.com",
    status: 1,
    orgs: [{ orgId: 6, orgName: "财务部", isPrimary: true }],
    createdAt: "2026-04-01T08:00:00"
  },
  {
    id: 11,
    username: "huangsan",
    name: "黄三",
    phone: "13800001011",
    email: "huangsan@example.com",
    status: 0,
    orgs: [{ orgId: 6, orgName: "财务部", isPrimary: true }],
    createdAt: "2026-03-15T08:00:00"
  },
  {
    id: 12,
    username: "yangsi",
    name: "杨四",
    phone: "13800001012",
    email: "yangsi@example.com",
    status: 1,
    orgs: [{ orgId: 3, orgName: "市场部", isPrimary: true }],
    createdAt: "2026-05-01T08:00:00"
  },
  {
    id: 13,
    username: "wangfei",
    name: "王菲",
    phone: "13800001013",
    email: "wangfei@example.com",
    status: 1,
    orgs: [{ orgId: 8, orgName: "产品组", isPrimary: true }],
    createdAt: "2026-05-10T08:00:00"
  },
  {
    id: 14,
    username: "zhaomin",
    name: "赵敏",
    phone: "13800001014",
    email: "zhaomin@example.com",
    status: 1,
    orgs: [{ orgId: 9, orgName: "设计组", isPrimary: true }],
    createdAt: "2026-05-15T08:00:00"
  }
];

const mockUserRoles = {
  1: [
    {
      roleId: 101,
      roleName: "研发中心",
      roleTypeCode: "ORG",
      roleTypeLabel: "组织",
      targetType: "ORG",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 102,
      roleName: "后端组",
      roleTypeCode: "ORG",
      roleTypeLabel: "组织",
      targetType: "ORG",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 201,
      roleName: "基础用户",
      roleTypeCode: "BASIC_ROLE",
      roleTypeLabel: "基础角色",
      targetType: "ROLE",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 301,
      roleName: "后端开发",
      roleTypeCode: "POSITION",
      roleTypeLabel: "职位",
      targetType: "POSITION",
      relationId: 4,
      relationOrgName: "后端组",
      validFrom: "2026-01-15T00:00:00",
      validTo: null
    },
    {
      roleId: 401,
      roleName: "核心开发组",
      roleTypeCode: "GROUP_ROLE",
      roleTypeLabel: "分组角色",
      targetType: "GROUP_ROLE",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    }
  ],
  2: [
    {
      roleId: 101,
      roleName: "研发中心",
      roleTypeCode: "ORG",
      roleTypeLabel: "组织",
      targetType: "ORG",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 103,
      roleName: "前端组",
      roleTypeCode: "ORG",
      roleTypeLabel: "组织",
      targetType: "ORG",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 201,
      roleName: "基础用户",
      roleTypeCode: "BASIC_ROLE",
      roleTypeLabel: "基础角色",
      targetType: "ROLE",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    }
  ],
  3: [
    {
      roleId: 101,
      roleName: "研发中心",
      roleTypeCode: "ORG",
      roleTypeLabel: "组织",
      targetType: "ORG",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 102,
      roleName: "后端组",
      roleTypeCode: "ORG",
      roleTypeLabel: "组织",
      targetType: "ORG",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 202,
      roleName: "高级用户",
      roleTypeCode: "BASIC_ROLE",
      roleTypeLabel: "基础角色",
      targetType: "ROLE",
      relationId: null,
      relationOrgName: null,
      validFrom: null,
      validTo: null
    },
    {
      roleId: 302,
      roleName: "后端组长",
      roleTypeCode: "POSITION",
      roleTypeLabel: "职位",
      targetType: "POSITION",
      relationId: 4,
      relationOrgName: "后端组",
      validFrom: "2026-01-20T00:00:00",
      validTo: null
    }
  ]
};

// ========== Mock 辅助 ==========

/** 获取 orgId 及其所有子组织 ID（递归） */
function getDescendantOrgIds(trees, orgId) {
  const ids = new Set();
  function collect(node) {
    ids.add(node.id);
    node.children.forEach(collect);
  }
  function find(nodes) {
    for (const n of nodes) {
      if (n.id === orgId) {
        collect(n);
        return true;
      }
      if (find(n.children)) return true;
    }
    return false;
  }
  for (const tree of trees) {
    find(tree);
  }
  return ids;
}

/** 按名称过滤组织树 */
function filterTreeByName(nodes, keyword) {
  return nodes
    .filter(n => n.orgName.includes(keyword))
    .map(n => ({ ...n, children: filterTreeByName(n.children, keyword) }));
}

/** 在组织树中查找组织名称 */
function findOrgName(tree, orgId) {
  for (const node of tree) {
    if (node.id === orgId) return node.orgName;
    const found = findOrgName(node.children, orgId);
    if (found) return found;
  }
  return undefined;
}

function resolveOrgName(orgId) {
  return (
    findOrgName(mockOrgTree, orgId) ??
    findOrgName(mockTeamTree, orgId) ??
    "未知组织"
  );
}

// ========== 路由 ==========

export default defineFakeRoute([
  // POST /org-tree-config/page —— 分页（mock 返回全部）
  {
    url: "/org-tree-config/page",
    method: "post",
    response: () =>
      ok({
        items: [
          { id: 1, treeName: "默认组织树", rootOrgId: 1, isDefault: true },
          { id: 2, treeName: "团队树", rootOrgId: 7, isDefault: false }
        ],
        pagination: { total: 2, page: 1, size: 100, totalPages: 1 }
      })
  },

  // POST /org/tree
  {
    url: "/org/tree",
    method: "post",
    response: ({ body }) => {
      let tree = body?.treeConfigId === 2 ? mockTeamTree : mockOrgTree;
      if (body?.orgName) {
        tree = filterTreeByName(tree, body.orgName);
      }
      return ok(tree);
    }
  },

  // POST /user/page
  {
    url: "/user/page",
    method: "post",
    response: ({ body }) => {
      const params = body ?? {};
      let filtered = [...mockUsers];
      if (params.username) {
        filtered = filtered.filter(u => u.username.includes(params.username));
      }
      if (params.name) {
        filtered = filtered.filter(u => u.name.includes(params.name));
      }
      if (params.phone) {
        filtered = filtered.filter(u => u.phone?.includes(params.phone));
      }
      if (params.email) {
        filtered = filtered.filter(u => u.email?.includes(params.email));
      }
      if (params.status !== undefined && params.status !== null) {
        filtered = filtered.filter(u => u.status === params.status);
      }
      if (params.orgId) {
        const descIds = getDescendantOrgIds(
          [mockOrgTree, mockTeamTree],
          params.orgId
        );
        filtered = filtered.filter(u => u.orgs.some(o => descIds.has(o.orgId)));
      }
      const pageNum = params.pageNum ?? 1;
      const pageSize = params.pageSize ?? 15;
      const start = (pageNum - 1) * pageSize;
      const paged = filtered.slice(start, start + pageSize);
      return ok({
        items: paged,
        pagination: {
          total: filtered.length,
          page: pageNum,
          size: pageSize,
          totalPages: Math.ceil(filtered.length / pageSize)
        }
      });
    }
  },

  // POST /user/create —— 真实后端返回 Long；Phase 1 额外返回 initialPassword
  {
    url: "/user/create",
    method: "post",
    response: ({ body }) => {
      const data = body ?? {};
      const newId = Date.now();
      const newUser = {
        id: newId,
        username: data.username,
        name: data.name,
        phone: data.phone ?? null,
        email: data.email ?? null,
        status: 1,
        orgs: [],
        createdAt: new Date().toISOString()
      };
      if (data.orgId) {
        newUser.orgs.push({
          orgId: data.orgId,
          orgName: resolveOrgName(data.orgId),
          isPrimary: true
        });
      }
      mockUsers.unshift(newUser);
      return ok({ id: newId, initialPassword: "Pwd@123abc" });
    }
  },

  // POST /user/update
  {
    url: "/user/update",
    method: "post",
    response: ({ body }) => {
      const data = body ?? {};
      const user = mockUsers.find(u => u.id === data.id);
      if (user) {
        if (data.name !== undefined) user.name = data.name;
        if (data.phone !== undefined) user.phone = data.phone;
        if (data.email !== undefined) user.email = data.email;
      }
      return ok(null);
    }
  },

  // POST /user/delete —— IdsReq { ids }
  {
    url: "/user/delete",
    method: "post",
    response: ({ body }) => {
      const ids = body?.ids ?? [];
      for (const id of ids) {
        const idx = mockUsers.findIndex(u => u.id === id);
        if (idx !== -1) mockUsers.splice(idx, 1);
      }
      return ok(null);
    }
  },

  // POST /user-org/list —— IdReq { id }
  {
    url: "/user-org/list",
    method: "post",
    response: ({ body }) => {
      const user = mockUsers.find(u => u.id === body?.id);
      if (!user) return ok([]);
      return ok(
        user.orgs.map(o => ({
          orgId: o.orgId,
          orgName: o.orgName,
          isPrimary: o.isPrimary
        }))
      );
    }
  },

  // POST /user-org/assign
  {
    url: "/user-org/assign",
    method: "post",
    response: ({ body }) => {
      const data = body ?? {};
      const user = mockUsers.find(u => u.id === data.userId);
      if (!user) return ok(null);

      const existingOrgMap = new Map(user.orgs.map(org => [org.orgId, org]));
      (data.orgIds ?? []).forEach(orgId => {
        if (!existingOrgMap.has(orgId)) {
          existingOrgMap.set(orgId, {
            orgId,
            orgName: resolveOrgName(orgId),
            isPrimary: false
          });
        }
      });

      const nextOrgs = Array.from(existingOrgMap.values());
      const nextPrimaryId =
        data.primaryOrgId ??
        nextOrgs.find(org => org.isPrimary)?.orgId ??
        nextOrgs[0]?.orgId;
      user.orgs = nextOrgs.map(org => ({
        ...org,
        isPrimary: org.orgId === nextPrimaryId
      }));
      return ok(null);
    }
  },

  // POST /user-org/remove
  {
    url: "/user-org/remove",
    method: "post",
    response: ({ body }) => {
      const data = body ?? {};
      const user = mockUsers.find(u => u.id === data.userId);
      if (!user) return ok(null);

      const removedPrimary = user.orgs.find(
        o => o.orgId === data.orgId
      )?.isPrimary;
      user.orgs = user.orgs.filter(o => o.orgId !== data.orgId);
      if (removedPrimary && user.orgs.length > 0) {
        user.orgs = user.orgs.map((org, index) => ({
          ...org,
          isPrimary: index === 0
        }));
      }
      return ok(null);
    }
  },

  // POST /user-org/set-primary
  {
    url: "/user-org/set-primary",
    method: "post",
    response: ({ body }) => {
      const data = body ?? {};
      const user = mockUsers.find(u => u.id === data.userId);
      if (user) {
        user.orgs.forEach(o => {
          o.isPrimary = o.orgId === data.orgId;
        });
      }
      return ok(null);
    }
  },

  // POST /user-role/list —— Phase 1 mock；Phase 2 admin 代理
  {
    url: "/user-role/list",
    method: "post",
    response: ({ body }) => ok(mockUserRoles[body?.userId] ?? [])
  },

  // POST /user-role/assign
  {
    url: "/user-role/assign",
    method: "post",
    response: () => ok(null)
  },

  // POST /user-role/revoke
  {
    url: "/user-role/revoke",
    method: "post",
    response: () => ok(null)
  }
]);
