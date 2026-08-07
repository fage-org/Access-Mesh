// 权限授予 Mock（T-FE-036，4.1 权限授予页 v3）。
// 对齐 permission-center 的 role-resource-permission/list（§6.4）与
// role-resource-permission/apply-grant-plan（§6.5.1，唯一写入口，🔧 T-PERM-034 后端未实现）。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。
// 资源/操作数据经 ./resource-operation 共享（跨 mock 引用同层数据，对齐 resource-dependency 先例）。
//
// 场景覆盖（S1~S7 验收驱动，主体 = 角色树 mock 中的 BASIC_ROLE）：
// - 直接授权 / 操作继承（UPDATE inheritMask 覆盖 VIEW）/ 资源继承（dept-data→role-data、
//   sys-mgmt→子菜单）/ 两段组合来源链 / ALL 虚拟行（REPORT 全量）/ 多分支并存（同键双条件）/
//   AUTO_DEP 只读 + 与 MANUAL 同格并列 / 组合位（grantedBits 含未定义位）/ 子权限（dependOn 挂载）/
//   空主体（BASIC_203 访客、GROUP_403）/ REPORT 大树（>500 节点虚拟滚动）
//
// 错误码演练（DoD-1 映射验证）：
// - 20033 同持久化键+条件冲突（creates/updates 查重，removes 生效后状态）
// - 20034 改/删 AUTO_DEP 记录
// - 20036 updates/removes 目标 id 不存在/不属于目标角色/已删除；updates∩removes 交叉
// - 20009/20010 creates 子权限挂父不存在 / 父非主权限（含子权限再带 children）
// - 20011 SUB_PERM 资源类型不允许（fail-closed：父类型未配置 → 拒绝；DATA→[DATA]、REPORT→[DATA,REPORT]）
// - 20040 ⚠️故障注入钩子：creates/updates 以 conditionCode="temp-access" 且 canGrant=true 提交
//   → 模拟授权传递校验未通过（真实 checkCanGrant 语义复杂，mock 以文档化钩子演练提示文案）
// - 20003 角色停用（BASIC_203 访客）；角色不存在 → list 空列表（契约：门禁失败返回空列表）
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import { resources, operations } from "./resource-operation";

// ========== 本地类型（对齐 api-contract §6.4 RolePermissionItemResp 14 字段） ==========

type RolePermissionItem = {
  id: number;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  resourceName: string | null;
  operationCode: string | null;
  canGrant: boolean;
  conditionCode: string | null;
  scopeMode: "INSTANCE" | "ALL";
  dependOn: number | null;
  grantSource: "MANUAL" | "AUTO_DEP";
  /** 63 位位图十进制字符串（T-PERM-028 线格式） */
  grantedBits: string;
  createdAt: string;
  childCount: number;
};

type InternalRecord = Omit<RolePermissionItem, "childCount"> & {
  roleKey: string;
  deleted: boolean;
};

type GrantRecordKey = {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string | null;
  scopeMode: "INSTANCE" | "ALL";
  conditionCode: string | null;
  canGrant?: boolean;
};

type GrantPlanCreate = {
  key: GrantRecordKey;
  parentPermissionId?: number;
  children?: GrantRecordKey[];
};

type GrantPlanUpdate = {
  id: number;
  canGrant?: boolean | null;
  conditionCode?: string | null;
};

// ========== 常量与种子 ==========

const BASE_TIME = "2026-08-01T10:30:00";
const now = () => new Date().toISOString().slice(0, 19);
const ok = <T>(data: T) => ({ code: 200, message: "success", data });
const error = (code: number, message: string) => ({
  code,
  message,
  data: null
});

/** 与 mock/permission-condition.ts 种子对齐（本地声明，避免跨 mock 环依赖） */
const KNOWN_CONDITION_CODES = new Set([
  "office-hours",
  "corp-ip-only",
  "temp-access",
  "blacklist-vpn"
]);

/** SUB_PERM 允许集（fail-closed：父资源类型未配置/不含目标类型 → 20011；`*` = 显式通配） */
const SUB_PERM_ALLOW: Record<string, string[]> = {
  DATA: ["DATA"],
  REPORT: ["DATA", "REPORT"]
};

/** 角色停用水位（对齐 role-manage mock BASIC_203 访客 status=0） */
const DISABLED_ROLE_KEYS = new Set(["BASIC_ROLE:BASIC_203"]);

let nextPermissionId = 5001;

/** 种子授权记录（roleKey = `${roleTypeCode}:${roleExternalId}`） */
const records: InternalRecord[] = [
  // ---- BASIC_201 基础用户：全场景集 ----
  // 直接授权 + 资源继承（dept-data → role-data 子孙）+ 子权限两条（多分支子权限）
  seed(1001, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "DATA",
    resourceCode: "dept-data",
    codeType: "default",
    resourceName: "部门数据",
    operationCode: "VIEW",
    canGrant: true,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2"
  }),
  seed(1002, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "DATA",
    resourceCode: "role-data",
    codeType: "default",
    resourceName: "角色数据",
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: 1001,
    grantSource: "MANUAL",
    grantedBits: "2"
  }),
  seed(1003, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "DATA",
    resourceCode: "role-data",
    codeType: "default",
    resourceName: "角色数据",
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: "office-hours",
    scopeMode: "INSTANCE",
    dependOn: 1001,
    grantSource: "MANUAL",
    grantedBits: "2"
  }),
  // 操作继承（UPDATE 覆盖 VIEW）+ 资源继承（sys-mgmt → user/role/res-op）+ 两段组合来源
  seed(1004, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "MENU",
    resourceCode: "sys-mgmt",
    codeType: "default",
    resourceName: "系统管理",
    operationCode: "UPDATE",
    canGrant: false,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "4"
  }),
  // ALL 虚拟行（REPORT 全量 UPDATE，ALL 行 UPDATE 直接 + VIEW 操作继承）
  seed(1005, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "REPORT",
    resourceCode: null,
    codeType: null,
    resourceName: null,
    operationCode: "UPDATE",
    canGrant: false,
    conditionCode: null,
    scopeMode: "ALL",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "4"
  }),
  // 多分支并存（与 1001 同分组键、不同条件 → 单元格 ⧉ 2 分支）
  seed(1006, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "DATA",
    resourceCode: "dept-data",
    codeType: "default",
    resourceName: "部门数据",
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: "office-hours",
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2"
  }),
  // AUTO_DEP 只读（与 1008 MANUAL 同格并列展示）
  seed(1007, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "API",
    resourceCode: "auth-check",
    codeType: "default",
    resourceName: "鉴权校验",
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "AUTO_DEP",
    grantedBits: "2"
  }),
  seed(1008, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "API",
    resourceCode: "auth-check",
    codeType: "default",
    resourceName: "鉴权校验",
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: "corp-ip-only",
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2"
  }),
  // 组合位（operationCode=null，grantedBits 2|4|32：VIEW+UPDATE 点亮两列，32 未定义位详情层展示）
  seed(1009, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "BUTTON",
    resourceCode: "btn-add",
    codeType: "default",
    resourceName: "新增按钮",
    operationCode: null,
    canGrant: false,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "38"
  }),
  // REPORT 实例行（与 ALL 行互斥展示）
  seed(1010, "BASIC_ROLE:BASIC_201", {
    resourceTypeCode: "REPORT",
    resourceCode: "report-g01-r01",
    codeType: "default",
    resourceName: "报表 1-1",
    operationCode: "VIEW",
    canGrant: true,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2"
  }),
  // ---- BASIC_202 高级用户：简单场景 ----
  seed(1101, "BASIC_ROLE:BASIC_202", {
    resourceTypeCode: "MENU",
    resourceCode: "user",
    codeType: "default",
    resourceName: "组织与用户",
    operationCode: "VIEW",
    canGrant: true,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2"
  })
];

function seed(
  id: number,
  roleKey: string,
  data: Omit<InternalRecord, "id" | "roleKey" | "deleted" | "createdAt">
): InternalRecord {
  return { id, roleKey, deleted: false, createdAt: BASE_TIME, ...data };
}

// ========== 工具函数 ==========

function roleKeyOf(body: { roleTypeCode?: string; roleExternalId?: string }) {
  return `${body?.roleTypeCode ?? ""}:${body?.roleExternalId ?? ""}`;
}

function aliveRecords(roleKey: string): InternalRecord[] {
  return records.filter(r => !r.deleted && r.roleKey === roleKey);
}

function toItem(
  record: InternalRecord,
  all: InternalRecord[]
): RolePermissionItem {
  return {
    id: record.id,
    resourceTypeCode: record.resourceTypeCode,
    resourceCode: record.resourceCode,
    codeType: record.codeType,
    resourceName: record.resourceName,
    operationCode: record.operationCode,
    canGrant: record.canGrant,
    conditionCode: record.conditionCode,
    scopeMode: record.scopeMode,
    dependOn: record.dependOn,
    grantSource: record.grantSource,
    grantedBits: record.grantedBits,
    createdAt: record.createdAt,
    childCount: all.filter(c => c.dependOn === record.id).length
  };
}

/** 解析操作定义（专属优先、全局回退）；返回 binaryBit 十进制字符串，未命中 null */
function resolveOperationBits(
  resourceTypeCode: string,
  operationCode: string
): string | null {
  const typed = operations.find(
    op => op.resourceTypeCode === resourceTypeCode && op.code === operationCode
  );
  const global = operations.find(
    op => op.resourceTypeCode === null && op.code === operationCode
  );
  const hit = typed ?? global;
  return hit ? String(hit.binaryBit) : null;
}

/** 操作码是否存在（任何类型专属或全局定义）——20005（不存在）与 20008（存在但不适用于当前类型）的区分依据 */
function operationCodeExists(code: string): boolean {
  return operations.some(op => op.code === code);
}

/** 资源类型是否存在（任一资源或操作定义引用） */
function isKnownResourceType(resourceTypeCode: string): boolean {
  return (
    resources.some(
      r => !r.deleted && r.resourceTypeCode === resourceTypeCode
    ) || operations.some(op => op.resourceTypeCode === resourceTypeCode)
  );
}

/** 完整持久化键（查重：(resourceType, resourceCode, codeType, grantedBits, dependOn, scopeMode) + condition） */
function persistKeyOf(k: {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  grantedBits: string;
  dependOn: number | null;
  scopeMode: string;
  conditionCode: string | null;
}): string {
  return [
    k.resourceTypeCode,
    k.resourceCode ?? "",
    k.codeType ?? "",
    k.grantedBits,
    k.dependOn ?? "",
    k.scopeMode,
    k.conditionCode ?? ""
  ].join("|");
}

// ========== 路由 ==========

export default defineFakeRoute([
  {
    url: "/api/perm/role-resource-permission/list",
    method: "post",
    response: ({ body }) => {
      const roleKey = roleKeyOf(body || {});
      const { resourceTypeCode } = body || {};
      const includeChildren = body?.includeChildren !== false;
      const all = aliveRecords(roleKey);
      // resourceTypeCode 过滤只作用于主权限（depend_on IS NULL 且类型匹配，契约 §6.4 T-PERM-040）；
      // 子权限按 depend_on 挂在其父主权限下返回，子权限自身可跨类型（不按子记录类型过滤），
      // 双重约束：depend_on ∈ 主权限集合 且属于目标角色。
      const mains = all.filter(
        r =>
          r.dependOn == null &&
          (!resourceTypeCode || r.resourceTypeCode === resourceTypeCode)
      );
      const mainIds = new Set(mains.map(r => r.id));
      const items = (
        includeChildren
          ? [
              ...mains,
              ...all.filter(r => r.dependOn != null && mainIds.has(r.dependOn))
            ]
          : mains
      ).map(r => toItem(r, all));
      return ok({ items });
    }
  },

  {
    url: "/api/perm/role-resource-permission/apply-grant-plan",
    method: "post",
    response: ({ body }) => {
      const roleKey = roleKeyOf(body || {});
      const plan = body?.plan ?? {};
      const creates: GrantPlanCreate[] = Array.isArray(plan.creates)
        ? plan.creates
        : [];
      const updates: GrantPlanUpdate[] = Array.isArray(plan.updates)
        ? plan.updates
        : [];
      const removes: number[] = Array.isArray(plan.removes) ? plan.removes : [];

      // 角色门禁（先于一切分支）
      if (DISABLED_ROLE_KEYS.has(roleKey)) {
        return error(20003, "目标角色已停用，无法授权");
      }

      const working = aliveRecords(roleKey);
      const byId = new Map(working.map(r => [r.id, r]));

      // ---- 预校验（任一不满足整批失败，不产生部分变更） ----

      // updates ∩ removes 互斥
      const updateIds = new Set(updates.map(u => u.id));
      if (removes.some(id => updateIds.has(id))) {
        return error(20036, "updates 与 removes 存在交叉 id");
      }
      // updates 重复 id
      if (updateIds.size !== updates.length) {
        return error(20036, "updates 存在重复 id");
      }
      // updates/removes 目标存在且属于目标角色
      for (const id of [...updateIds, ...removes]) {
        if (!byId.has(id)) {
          return error(20036, `目标记录不存在或已被修改（id=${id}）`);
        }
      }
      // AUTO_DEP 只读门禁
      for (const id of [...updateIds, ...removes]) {
        if (byId.get(id)!.grantSource === "AUTO_DEP") {
          return error(20034, "自动补全记录只读，不可修改或删除");
        }
      }

      // removes 生效后状态（查重基准，合法"先删后同键重加"不误判）
      const removeSet = new Set(removes);
      const surviving = working.filter(
        r =>
          !removeSet.has(r.id) &&
          !(r.dependOn != null && removeSet.has(r.dependOn))
      );

      // updates 校验：条件冲突（排除自身）+ 20040 故障注入
      for (const u of updates) {
        const target = byId.get(u.id)!;
        const nextCondition =
          u.conditionCode === undefined
            ? target.conditionCode
            : u.conditionCode === "" || u.conditionCode == null
              ? null
              : u.conditionCode;
        if (
          nextCondition != null &&
          !KNOWN_CONDITION_CODES.has(nextCondition)
        ) {
          return error(20006, `权限条件不存在（${nextCondition}）`);
        }
        const conflict = surviving.find(
          r =>
            r.id !== u.id &&
            r.grantSource === "MANUAL" &&
            persistKeyOf({ ...r, conditionCode: r.conditionCode }) ===
              persistKeyOf({ ...target, conditionCode: nextCondition })
        );
        if (conflict) {
          return error(20033, "同一权限键下该条件分支已存在");
        }
        if (u.canGrant === true && nextCondition === "temp-access") {
          return error(20040, "当前账号无权转授该权限（授权传递校验未通过）");
        }
      }

      // creates 校验
      const planKeys = new Set<string>();
      for (const create of creates) {
        const key = create.key;
        if (!key) return error(20036, "creates 缺少 key");
        // scopeMode 跨字段约束
        if (key.scopeMode === "ALL") {
          if (key.resourceCode != null || key.codeType != null) {
            return error(
              20012,
              "scopeMode=ALL 时 resourceCode/codeType 必须为 null"
            );
          }
        } else if (!key.resourceCode || !key.codeType) {
          return error(20012, "scopeMode=INSTANCE 缺少 resourceCode/codeType");
        }
        if (!isKnownResourceType(key.resourceTypeCode)) {
          return error(20007, `资源类型不存在（${key.resourceTypeCode}）`);
        }
        if (key.scopeMode === "INSTANCE") {
          const resource = resources.find(
            r =>
              !r.deleted &&
              r.resourceTypeCode === key.resourceTypeCode &&
              r.code === key.resourceCode &&
              r.codeType === key.codeType
          );
          if (!resource) {
            return error(
              20004,
              `目标资源不存在（${key.resourceTypeCode}:${key.resourceCode}）`
            );
          }
        }
        if (key.operationCode == null) {
          return error(20005, "操作码缺失（本页不支持组合位新增）");
        }
        const bits = resolveOperationBits(
          key.resourceTypeCode,
          key.operationCode
        );
        if (bits == null) {
          // 20008 演练（T-PERM-040 契约 §6.4-14③）：操作码存在但当前类型不适用（专属优先、全局回退合并后不匹配）
          return operationCodeExists(key.operationCode)
            ? error(
                20008,
                `操作权限与资源类型不匹配（${key.operationCode} 不适用于 ${key.resourceTypeCode}）`
              )
            : error(20005, `操作权限不存在（${key.operationCode}）`);
        }
        if (
          key.conditionCode != null &&
          !KNOWN_CONDITION_CODES.has(key.conditionCode)
        ) {
          return error(20006, `权限条件不存在（${key.conditionCode}）`);
        }

        // 父校验
        let parent: InternalRecord | null = null;
        if (create.parentPermissionId != null) {
          parent = byId.get(create.parentPermissionId) ?? null;
          if (!parent) {
            return error(20009, "父权限不存在，请刷新后重试");
          }
          if (parent.dependOn != null) {
            return error(20010, "父权限不是主权限，不能挂载子权限");
          }
          if (Array.isArray(create.children) && create.children.length > 0) {
            return error(20010, "子权限不得再挂载子权限");
          }
        }

        // SUB_PERM 约束（fail-closed）：子权限资源类型校验（父域 = 父记录自身 resource_type 直查）
        const parentType = parent ? parent.resourceTypeCode : null;
        if (parentType != null) {
          const allow = SUB_PERM_ALLOW[parentType];
          if (
            !allow ||
            (!allow.includes("*") && !allow.includes(key.resourceTypeCode))
          ) {
            return error(
              20011,
              "该资源类型不允许作为子权限（SUB_PERM 配置不允许）"
            );
          }
        }

        // 完整持久化键查重（removes 生效后状态；grant_source 不参与——仅 MANUAL 冲突）
        const fullKey = persistKeyOf({
          resourceTypeCode: key.resourceTypeCode,
          resourceCode: key.resourceCode,
          codeType: key.codeType,
          grantedBits: bits,
          dependOn: create.parentPermissionId ?? null,
          scopeMode: key.scopeMode,
          conditionCode: key.conditionCode ?? null
        });
        const conflict = surviving.find(
          r => r.grantSource === "MANUAL" && persistKeyOf(r) === fullKey
        );
        if (conflict || planKeys.has(fullKey)) {
          return error(20033, "同一权限键下该条件分支已存在");
        }
        planKeys.add(fullKey);

        // 20040 故障注入钩子（见文件头注释）
        if (key.canGrant === true && key.conditionCode === "temp-access") {
          return error(20040, "当前账号无权转授该权限（授权传递校验未通过）");
        }

        // children 一次性建树校验（仅主权限）
        if (Array.isArray(create.children)) {
          if (create.parentPermissionId != null) {
            return error(20010, "子权限不得再挂载子权限");
          }
          // 新父 id 未生成，children 不会与存量记录冲突（depend_on 入键）；
          // 仅查 plan 内 children 自相重复 + 字段合法性 + SUB_PERM
          const childKeys = new Set<string>();
          for (const child of create.children) {
            if (
              child.scopeMode === "INSTANCE" &&
              (!child.resourceCode || !child.codeType)
            ) {
              return error(
                20012,
                "子权限 scopeMode=INSTANCE 缺少 resourceCode/codeType"
              );
            }
            const allow = SUB_PERM_ALLOW[key.resourceTypeCode];
            if (
              !allow ||
              (!allow.includes("*") && !allow.includes(child.resourceTypeCode))
            ) {
              return error(
                20011,
                "该资源类型不允许作为子权限（SUB_PERM 配置不允许）"
              );
            }
            if (child.operationCode == null) {
              return error(20005, "子权限操作码缺失");
            }
            const childBits = resolveOperationBits(
              child.resourceTypeCode,
              child.operationCode
            );
            if (childBits == null) {
              // 20008 演练覆盖 children[] 嵌套形态（T-PERM-040：不能通过嵌套绕过）
              return operationCodeExists(child.operationCode)
                ? error(
                    20008,
                    `操作权限与资源类型不匹配（${child.operationCode} 不适用于 ${child.resourceTypeCode}）`
                  )
                : error(
                    20005,
                    `子权限操作权限不存在（${child.operationCode}）`
                  );
            }
            if (
              child.conditionCode != null &&
              !KNOWN_CONDITION_CODES.has(child.conditionCode)
            ) {
              return error(20006, `权限条件不存在（${child.conditionCode}）`);
            }
            const childKey = persistKeyOf({
              resourceTypeCode: child.resourceTypeCode,
              resourceCode: child.resourceCode,
              codeType: child.codeType,
              grantedBits: childBits,
              dependOn: null,
              scopeMode: child.scopeMode,
              conditionCode: child.conditionCode ?? null
            });
            if (childKeys.has(childKey)) {
              return error(20033, "同一权限键下该条件分支已存在");
            }
            childKeys.add(childKey);
          }
        }
      }

      // ---- 单事务执行（预校验已过，模拟原子提交；受影响行数断言为后端职责，mock 不模拟） ----

      // removes：主权限级联删子
      for (const id of removes) {
        const target = byId.get(id)!;
        target.deleted = true;
        for (const child of records.filter(
          r => !r.deleted && r.dependOn === id
        )) {
          child.deleted = true;
        }
      }

      // updates：三态应用（canGrant 缺省不改；conditionCode ""/null 清除，非空覆盖）
      for (const u of updates) {
        const target = byId.get(u.id)!;
        if (u.canGrant !== undefined && u.canGrant !== null) {
          target.canGrant = u.canGrant;
        }
        if (u.conditionCode !== undefined && u.conditionCode !== null) {
          target.conditionCode =
            u.conditionCode === "" ? null : u.conditionCode;
        }
      }

      // creates：id 分配 + children 一次性建树（服务端生成父 id 后回填 depend_on）
      for (const create of creates) {
        const key = create.key;
        const parentId = nextPermissionId++;
        const resource =
          key.scopeMode === "INSTANCE"
            ? resources.find(
                r =>
                  !r.deleted &&
                  r.resourceTypeCode === key.resourceTypeCode &&
                  r.code === key.resourceCode &&
                  r.codeType === key.codeType
              )
            : null;
        records.push({
          id: parentId,
          roleKey,
          deleted: false,
          createdAt: now(),
          resourceTypeCode: key.resourceTypeCode,
          resourceCode: key.resourceCode,
          codeType: key.codeType,
          resourceName: resource?.name ?? null,
          operationCode: key.operationCode,
          canGrant: key.canGrant ?? false,
          conditionCode: key.conditionCode ?? null,
          scopeMode: key.scopeMode,
          dependOn: create.parentPermissionId ?? null,
          grantSource: "MANUAL",
          grantedBits:
            resolveOperationBits(key.resourceTypeCode, key.operationCode!) ??
            "0"
        });
        for (const child of create.children ?? []) {
          const childResource =
            child.scopeMode === "INSTANCE"
              ? resources.find(
                  r =>
                    !r.deleted &&
                    r.resourceTypeCode === child.resourceTypeCode &&
                    r.code === child.resourceCode &&
                    r.codeType === child.codeType
                )
              : null;
          records.push({
            id: nextPermissionId++,
            roleKey,
            deleted: false,
            createdAt: now(),
            resourceTypeCode: child.resourceTypeCode,
            resourceCode: child.resourceCode,
            codeType: child.codeType,
            resourceName: childResource?.name ?? null,
            operationCode: child.operationCode,
            canGrant: child.canGrant ?? false,
            conditionCode: child.conditionCode ?? null,
            scopeMode: child.scopeMode,
            dependOn: parentId,
            grantSource: "MANUAL",
            grantedBits:
              resolveOperationBits(
                child.resourceTypeCode,
                child.operationCode!
              ) ?? "0"
          });
        }
      }

      // 响应 = 完整持久化结果（结构同 list includeChildren=true）
      const all = aliveRecords(roleKey);
      return ok({ items: all.map(r => toItem(r, all)) });
    }
  }
]);
