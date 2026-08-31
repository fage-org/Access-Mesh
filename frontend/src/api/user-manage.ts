/**
 * 用户管理 API（T-FE-015 联调收口：mock 退役，直连 access-service 真实端点）
 * 经 @/utils/http 调用外部路径（Gateway：/admin StripPrefix=1 → access-service 裸路径 /org、/user 等；
 * 角色分配走 permission 域 /perm/api/perm/user-role/*）。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";

// ========== 类型定义 ==========

/** 组织树查询参数（对齐后端 OrgQuery；operationCode/treeConfigId 后端不存在——契约 §4.2.1 合规债务①，勿传） */
export type OrgQuery = {
  orgName?: string;
  /** 必填语义：后端按 orgType 分发 ORG:VIEW / ORG:VIEW_POSITION 门禁（1=普通组织 / 2=岗位） */
  orgType: number;
  status?: number;
  parentOrgId?: number;
};

/** 组织树配置（对齐后端 OrgTreeConfigResp） */
export type OrgTreeConfig = {
  id: number;
  treeName: string;
  rootOrgId: number;
  isDefault: boolean;
};

/** 组织树节点（对齐后端 OrgResp） */
export type OrgTreeNode = {
  id: number;
  orgName: string;
  code: string;
  parentOrgId: number | null;
  orgType: number; // 字典值（后端 Integer）
  status: number;
  sort: number;
  children: OrgTreeNode[];
};

export type UserItem = {
  id: number;
  username: string;
  name: string;
  phone: string | null;
  email: string | null;
  status: 0 | 1;
  orgs: {
    orgId: number;
    orgName: string;
    /** 后端为字符串线格式（type code），消费方按需转换 */
    orgType?: string;
    isPrimary: boolean;
  }[];
  createdAt: string;
};

/** 分页查询参数（对齐后端 UserPageReq，orgId 为子树成员过滤） */
export type UserPageQuery = {
  pageNum: number;
  pageSize: number;
  sort?: string;
  username?: string;
  name?: string;
  phone?: string;
  email?: string;
  status?: number;
  /** 按组织子树筛选成员 */
  orgId?: number;
};

/** 分页响应（对齐后端 PaginatedResult） */
export type PaginatedResult<T> = {
  items: T[];
  pagination: {
    total: number;
    page: number;
    size: number;
    totalPages: number;
  };
};

export type UserRoleItem = {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  roleTypeLabel: string;
  targetType: string;
  relationId: number | null;
  relationOrgName: string | null;
  validFrom: string | null;
  validTo: string | null;
};

/** 组织简要信息（对齐后端 OrgBrief，orgType 为字符串线格式） */
export type OrgBrief = {
  orgId: number;
  orgName: string;
  orgType?: string;
  isPrimary: boolean;
};

/** 组织下的用户项（对齐后端 OrgUserItemResp） */
export type OrgUserItem = {
  userId: number;
  username: string;
  name: string;
  avatar?: string;
  isPrimary: boolean;
};

/** 创建用户响应（对齐后端 UserCreateResp——初始密码由后端生成，仅本次返回明文） */
export type CreateUserResult = {
  id: number;
  /** 系统生成的随机初始密码 */
  initialPassword: string;
};

// ========== 组织 CRUD 类型 ==========

/** 组织创建请求（对齐后端 OrgCreateReq） */
export type OrgCreateReq = {
  orgName: string;
  code: string;
  orgType: number;
  parentOrgId?: number | null;
  status?: number;
  sort?: number;
};

/**
 * 组织更新请求（对齐后端 OrgUpdateReq——无 orgType 字段，组织类型不可改；
 * 编辑表单应禁用类型选择）
 */
export type OrgUpdateReq = {
  id: number;
  orgName?: string;
  code?: string;
  parentOrgId?: number | null;
  status?: number;
  sort?: number;
};

/** 组织分页查询参数 */
export type OrgPageQuery = {
  pageNum: number;
  pageSize: number;
  orgName?: string;
  orgType?: number;
  status?: number;
  /** 按选中组织子树筛选（用于岗位 Tab） */
  orgId?: number;
};

/** 组织分页项（平铺；后端分页项即 OrgResp，无 parentOrgName——父组织名由调用方本地映射） */
export type OrgPageItem = {
  id: number;
  orgName: string;
  code: string;
  parentOrgId: number | null;
  orgType: number;
  status: number;
  sort: number;
};

// ========== 功能角色类型 ==========

/** 功能角色列表项（仅 BASIC_ROLE / GROUP_ROLE / PERSONAL，不含 ORG / POSITION） */
export type RoleItem = {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  roleTypeLabel?: string;
};

// ========== 成员候选类型（member-candidates，T-FE-015 接入） ==========

/** 成员候选查询参数（对齐后端 MemberCandidatesReq） */
export type MemberCandidatesQuery = {
  /** 目标组织（岗位）ID，必填 */
  targetOrgId: number;
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
};

/** 成员候选项（对齐后端 MemberCandidateItemResp；alreadyAssignment 后端恒 false，占用过滤由调用方本地完成） */
export type MemberCandidateItem = {
  id: number;
  username: string;
  name: string;
  avatar?: string;
  primaryOrgName?: string | null;
  alreadyAssignment: boolean;
};

// ========== API 函数 ==========

/** 获取可用组织树配置列表（POST /admin/org-tree-config/page，分页取前 100 条） */
export const getOrgTreeConfigs = async (): Promise<OrgTreeConfig[]> => {
  const res = await http.request<PermResult<PaginatedResult<OrgTreeConfig>>>(
    "post",
    "/admin/org-tree-config/page",
    { data: { pageNum: 1, pageSize: 100 } }
  );
  return unwrap(res).items;
};

/** 获取组织树（POST /admin/org/tree） */
export const getOrgTree = async (params: OrgQuery): Promise<OrgTreeNode[]> => {
  const res = await http.request<PermResult<OrgTreeNode[]>>(
    "post",
    "/admin/org/tree",
    { data: params }
  );
  return unwrap(res);
};

/** 获取用户分页列表（POST /admin/user/page） */
export const getUserPage = async (
  params: UserPageQuery
): Promise<PaginatedResult<UserItem>> => {
  const res = await http.request<PermResult<PaginatedResult<UserItem>>>(
    "post",
    "/admin/user/page",
    { data: params }
  );
  return unwrap(res);
};

/** 创建用户（POST /admin/user/create；orgId 非空时后端同事务一步入组，primaryOrg 缺省 true） */
export const createUser = async (data: {
  username: string;
  name: string;
  phone?: string;
  email?: string;
  /** 所属组织ID（可选；需属默认组织树） */
  orgId?: number;
}): Promise<CreateUserResult> => {
  const res = await http.request<PermResult<CreateUserResult>>(
    "post",
    "/admin/user/create",
    { data }
  );
  return unwrap(res);
};

/** 更新用户（POST /admin/user/update；改己豁免门禁） */
export const updateUser = async (data: {
  id: number;
  name?: string;
  phone?: string | null;
  email?: string | null;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/user/update", { data })
  );
};

/** 删除用户（POST /admin/user/delete，IdsReq 批量） */
export const deleteUser = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/user/delete", {
      data: { ids }
    })
  );
};

// ========== /admin/user-org API ==========

/** 查询用户所属组织（POST /admin/user-org/list，IdReq.id=userId） */
export const getUserOrgs = async (userId: number): Promise<OrgBrief[]> => {
  const res = await http.request<PermResult<OrgBrief[]>>(
    "post",
    "/admin/user-org/list",
    { data: { id: userId } }
  );
  return unwrap(res);
};

/** 分配组织（POST /admin/user-org/assign；普通组织 ORG:MANAGE_MEMBER / 岗位 ORG:ASSIGN_POSITION_USER） */
export const assignUserOrgs = async (data: {
  userId: number;
  orgIds: number[];
  primaryOrgId?: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/user-org/assign", {
      data
    })
  );
};

/** 移除组织关联（POST /admin/user-org/remove；默认树走 USER:UPDATE、非默认树走成员关系操作码） */
export const removeUserOrg = async (data: {
  userId: number;
  orgId: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/user-org/remove", {
      data
    })
  );
};

/** 设置主组织（POST /admin/user-org/set-primary；仅默认组织树内主归属） */
export const setPrimaryOrg = async (data: {
  userId: number;
  orgId: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/admin/user-org/set-primary",
      {
        data
      }
    )
  );
};

// ========== 用户角色（admin 聚合读 + permission 域写；admin 侧写端点已随 T-ADMIN-024 退役） ==========

/** 获取用户角色列表（POST /admin/user-role/list，全类型角色含 ORG/POSITION 投影） */
export const getUserRoles = async (userId: number): Promise<UserRoleItem[]> => {
  const res = await http.request<PermResult<{ items: UserRoleItem[] }>>(
    "post",
    "/admin/user-role/list",
    { data: { userId } }
  );
  return unwrap(res).items;
};

/**
 * 分配功能角色（POST /perm/api/perm/user-role/assign，items[] 批量、业务键标识；
 * permission 域外部前缀为 /perm 非 /admin——与 bootstrap 映射注册及全部 perm 域页面一致）。
 * 门禁 ROLE:MANAGE（目标角色实例）；domainCode 功能角色可空。
 */
export const assignRole = async (data: {
  userId: number;
  roleTypeCode: string;
  roleExternalId: string;
  validFrom?: string;
  validTo?: string;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/user-role/assign",
      {
        data: {
          items: [
            {
              // 本地管理页用户为 LOCAL_USER 主体（user_type 种子：USER=外部人员 /
              // LOCAL_USER=本地访问主体，T-ORG-001 external_id=主体 ID 字符串化）
              subjectTypeCode: "LOCAL_USER",
              subjectExternalId: String(data.userId),
              roleTypeCode: data.roleTypeCode,
              roleExternalId: data.roleExternalId,
              validFrom: data.validFrom,
              validTo: data.validTo
            }
          ]
        }
      }
    )
  );
};

/** 回收功能角色（POST /perm/api/perm/user-role/revoke，items[] 批量、业务键标识） */
export const revokeRole = async (data: {
  userId: number;
  roleTypeCode: string;
  roleExternalId: string;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/user-role/revoke",
      {
        data: {
          items: [
            {
              subjectTypeCode: "LOCAL_USER",
              subjectExternalId: String(data.userId),
              roleTypeCode: data.roleTypeCode,
              roleExternalId: data.roleExternalId
            }
          ]
        }
      }
    )
  );
};

// ========== /admin/org CRUD API ==========

/** 创建组织（POST /admin/org/create；响应 data 为裸 Long——新组织 ID） */
export const createOrg = async (data: OrgCreateReq): Promise<number> => {
  const res = await http.request<PermResult<number>>(
    "post",
    "/admin/org/create",
    { data }
  );
  return unwrap(res);
};

/** 更新组织（POST /admin/org/update，仅传变更字段；类型不可改） */
export const updateOrg = async (data: OrgUpdateReq): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/org/update", { data })
  );
};

/** 删除组织（POST /admin/org/delete，IdReq） */
export const deleteOrg = async (id: number): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/org/delete", {
      data: { id }
    })
  );
};

/** 组织分页列表（POST /admin/org/page，平铺不含 children） */
export const getOrgPage = async (
  params: OrgPageQuery
): Promise<PaginatedResult<OrgPageItem>> => {
  const res = await http.request<PermResult<PaginatedResult<OrgPageItem>>>(
    "post",
    "/admin/org/page",
    { data: params }
  );
  return unwrap(res);
};

// ========== /admin/user 启停 & 重置密码 & 成员候选 ==========

/** 批量启用/停用用户（POST /admin/user/enable，IdsReq + status；启停合一端点） */
export const enableUsers = async (data: {
  ids: number[];
  status: 0 | 1;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/admin/user/enable", {
      data
    })
  );
};

/** 重置用户密码（POST /admin/user/reset-password；newPassword 可选（8-32 位），不传则系统生成；改己豁免） */
export const resetUserPassword = async (data: {
  userId: number;
  newPassword?: string;
}): Promise<{ newPassword: string }> => {
  const res = await http.request<PermResult<{ newPassword: string }>>(
    "post",
    "/admin/user/reset-password",
    { data }
  );
  return unwrap(res);
};

/**
 * 成员候选查询（POST /admin/user/member-candidates；语义=默认树身份目录候选，
 * 区别于成员列表 /user/page。门禁 ORG:UPDATE@targetOrgId。
 * alreadyAssignment 后端恒 false——已在目标组织的过滤由调用方本地完成）
 */
export const getMemberCandidates = async (
  params: MemberCandidatesQuery
): Promise<PaginatedResult<MemberCandidateItem>> => {
  const res = await http.request<
    PermResult<PaginatedResult<MemberCandidateItem>>
  >("post", "/admin/user/member-candidates", { data: params });
  return unwrap(res);
};

// ========== /admin/role API（仅功能角色） ==========

/** 获取功能角色列表（POST /admin/role/list，默认仅 BASIC_ROLE / GROUP_ROLE / PERSONAL） */
export const getRoleList = async (): Promise<RoleItem[]> => {
  const res = await http.request<PermResult<{ items: RoleItem[] }>>(
    "post",
    "/admin/role/list",
    {
      data: {}
    }
  );
  return unwrap(res).items;
};

// ========== /admin/org/users 查询组织下用户 ==========

/** 组织下的用户（POST /admin/org/users，IdReq.id=orgId） */
export const getOrgUsers = async (orgId: number): Promise<OrgUserItem[]> => {
  const res = await http.request<PermResult<OrgUserItem[]>>(
    "post",
    "/admin/org/users",
    { data: { id: orgId } }
  );
  return unwrap(res);
};
