// 资源依赖 Mock（T-FE-011）。
// 对齐 access-service 权限域的 resource-dependency 管理接口；
// 资源依赖 CRUD + 依赖图 + 循环检测。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。
//
// 种子 ID 对齐（名称/类型映射由前端 hook 加载 resource-operation mock 建立）：
// - 资源：201-204 MENU / 211-212 BUTTON / 221-222 API / 231-232 DATA（见 mock/resource-operation.ts）
// - 操作位：CREATE=1 VIEW=2 UPDATE=4 DELETE=8 MANAGE=16（见 mock/resource-operation.ts CRUD_OPS）
//
// 对齐后端 DependencyAppServiceImpl（T-PERM-031 收口，2026-08-30）：
// - create/update 用业务键（sourceResourceTypeCode+sourceResourceCode+...），mock 内部解析为资源 ID
// - 错误码与后端同码：autoGrant=true 20048（最先预检）→ 资源不存在 20004 → 自依赖 20044 →
//   未知操作码 20005（fail-closed，不再静默丢弃）→ 等价重复 20054；update 未知 id 20019
// - update 全量替换（Q3=B）：资源对可改，完整字段覆盖；maintainSource/ownerServiceCode 来源归属不变
// - check 循环检测：DFS 从 target 反查是否能到达 source（对齐后端 hasDependencyCycle.canReach）
// - graph 返回扁平依赖列表（对齐后端 graph，前端建 nodes/edges）
// - list 仅按 resourceEntityId 过滤（对齐后端 DependencyListReq；全量不分页定案）
// - Resp 补静态字段（类型/名称/updatedAt/maintainSource/ownerServiceCode），操作位字符串线格式
//   （63 位 bigint 位值列，T-PERM-028 同款）；operationCodes 数组不反解
// - remove 幂等跳过幽灵 id，响应 data=null 无行数（对齐后端）
// - batch-sync 端点 P0 标 TODO（Q5=B），不实现
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import {
  resources,
  operations,
  type InternalResource
} from "./resource-operation";

// ========== 本地类型（对齐后端 DTO，api-contract.md §5.6） ==========

type ResourceDependencyResp = {
  id: number;
  tenantId: number;
  resourceEntityId: number;
  sourceResourceCode: string;
  sourceResourceTypeCode: string | null;
  sourceResourceName: string | null;
  dependsOnResourceEntityId: number;
  depResourceCode: string;
  targetResourceTypeCode: string | null;
  targetResourceName: string | null;
  /** 源操作位（字符串线格式，null=任意操作触发） */
  sourceOperationBits: string | null;
  /** 要求操作位（字符串线格式） */
  requiredOperationBits: string;
  autoGrant: boolean;
  description: string | null;
  ownerServiceCode: string | null;
  maintainSource: string | null;
  createdAt: string;
  updatedAt: string;
};

/** 内部存储形态：bits 用十进制字符串（BigInt 位或后 toString，全程不收窄为
 *  Number——2^53 以上低位会在序列化前丢失，违背字符串线格式目的） */
type InternalDep = {
  id: number;
  tenantId: number;
  resourceEntityId: number;
  sourceResourceCode: string;
  dependsOnResourceEntityId: number;
  depResourceCode: string;
  sourceOperationBits: string | null;
  requiredOperationBits: string;
  autoGrant: boolean;
  description: string | null;
  ownerServiceCode: string | null;
  maintainSource: string;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
};

// 资源/操作状态共享自 mock/resource-operation.ts（P2 修复：跨页 CRUD 一致）。
// resources 为动态数组（软删 deleted 标记），operations 为动态数组（splice 物理移除）。
// resolveResourceId/codesToBits 遍历动态状态，跨页新增资源/操作即时可见。

// ========== 常量与种子 ==========

const now = () => new Date().toISOString().slice(0, 19).replace("T", " ");
const BASE_TIME = "2026-06-20 10:00:00";
const ok = <T>(data: T) => ({ code: 200, message: "success", data });
const error = (code: number, message: string) => ({
  code,
  message,
  data: null
});

let nextId = 805;

const deps: InternalDep[] = [
  {
    id: 801,
    tenantId: 1,
    resourceEntityId: 203,
    sourceResourceCode: "role",
    dependsOnResourceEntityId: 221,
    depResourceCode: "auth-check",
    sourceOperationBits: "2",
    requiredOperationBits: "2",
    autoGrant: false,
    description: "访问角色管理需先通过鉴权校验",
    ownerServiceCode: null,
    maintainSource: "ADMIN_UI",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 802,
    tenantId: 1,
    resourceEntityId: 204,
    sourceResourceCode: "res-op",
    dependsOnResourceEntityId: 222,
    depResourceCode: "res-tree",
    sourceOperationBits: "2",
    requiredOperationBits: "2",
    autoGrant: false,
    description: "资源与操作页需资源树查询接口",
    ownerServiceCode: null,
    maintainSource: "ADMIN_UI",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 803,
    tenantId: 1,
    resourceEntityId: 202,
    sourceResourceCode: "user",
    dependsOnResourceEntityId: 231,
    depResourceCode: "dept-data",
    sourceOperationBits: "2",
    requiredOperationBits: "2",
    autoGrant: false,
    description: "组织与用户页依赖部门数据",
    ownerServiceCode: null,
    maintainSource: "ADMIN_UI",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  },
  {
    id: 804,
    tenantId: 1,
    resourceEntityId: 212,
    sourceResourceCode: "btn-edit",
    dependsOnResourceEntityId: 232,
    depResourceCode: "role-data",
    sourceOperationBits: "4",
    requiredOperationBits: "2",
    autoGrant: false,
    description: "编辑按钮依赖角色数据查看（手动补全）",
    ownerServiceCode: null,
    maintainSource: "ADMIN_UI",
    createdAt: BASE_TIME,
    updatedAt: BASE_TIME,
    deleted: false
  }
];

// ========== 工具函数 ==========

/** 序列化：补类型/名称（引用资源活状态）、操作位转字符串线格式 */
function clone(d: InternalDep): ResourceDependencyResp {
  const src = getResourceRef(d.resourceEntityId);
  const tgt = getResourceRef(d.dependsOnResourceEntityId);
  return {
    id: d.id,
    tenantId: d.tenantId,
    resourceEntityId: d.resourceEntityId,
    sourceResourceCode: d.sourceResourceCode,
    sourceResourceTypeCode: src?.resourceTypeCode ?? null,
    sourceResourceName: src?.name ?? null,
    dependsOnResourceEntityId: d.dependsOnResourceEntityId,
    depResourceCode: d.depResourceCode,
    targetResourceTypeCode: tgt?.resourceTypeCode ?? null,
    targetResourceName: tgt?.name ?? null,
    sourceOperationBits: d.sourceOperationBits,
    requiredOperationBits: d.requiredOperationBits,
    autoGrant: d.autoGrant,
    description: d.description,
    ownerServiceCode: d.ownerServiceCode,
    maintainSource: d.maintainSource,
    createdAt: d.createdAt,
    updatedAt: d.updatedAt
  };
}

/** 按业务键解析资源 ID（对齐后端 typeResolutionService.resolveResourceId）。
 *  遍历动态 resources 数组（过滤 deleted），跨页新增资源即时可见。 */
function resolveResourceId(
  resourceTypeCode: string,
  resourceCode: string,
  codeType?: string | null
): number | null {
  const ref = resources.find(
    r =>
      !r.deleted &&
      r.resourceTypeCode === resourceTypeCode &&
      r.code === resourceCode &&
      (codeType == null || r.codeType === codeType)
  );
  return ref ? ref.id : null;
}

/** 按资源 ID 反查资源引用（遍历动态 resources 数组） */
function getResourceRef(id: number): InternalResource | undefined {
  return resources.find(r => !r.deleted && r.id === id);
}

/** 收集解析不到的操作码（对齐后端 fail-closed 20005：静默丢弃会写出语义错误规则） */
function collectUnknownCodes(
  codes: string[] | null | undefined,
  resourceTypeCode: string
): string[] {
  if (!codes || codes.length === 0) return [];
  const unknown: string[] = [];
  for (const c of codes) {
    if (!c || !c.trim()) continue;
    const op = operations.find(
      o => o.resourceTypeCode === resourceTypeCode && o.code === c
    );
    if (!op) unknown.push(c);
  }
  return unknown;
}

/** 操作码列表 -> 操作位掩码（对齐后端 resolveOperationBits；调用前已经 20005 校验）。
 *  按资源类型解析（当前类型 + 全局操作 resourceTypeCode=null），空列表返回 null（=任意操作触发）。
 *  BigInt 位或，兼容 63 位 bigint 列（JS |= 截断 32 位，P1 修复）。 */
function codesToBits(
  codes: string[] | null | undefined,
  resourceTypeCode: string
): string | null {
  if (!codes || codes.length === 0) return null;
  let bits = 0n;
  for (const c of codes) {
    const op = operations.find(
      o => o.resourceTypeCode === resourceTypeCode && o.code === c
    );
    if (op) bits |= BigInt(op.binaryBit);
  }
  return bits.toString();
}

/** 校验创建/更新请求字段（对齐后端 ResourceDependencyCreateReq 必填约束） */
function validateBody(body: any): string | null {
  if (!body) return "请求体不能为空";
  if (!body.sourceResourceTypeCode || !body.sourceResourceCode) {
    return "源资源类型与编码必填";
  }
  if (!body.targetResourceTypeCode || !body.targetResourceCode) {
    return "目标资源类型与编码必填";
  }
  const requiredCodes: string[] = body.requiredOperationCodes ?? [];
  if (!Array.isArray(requiredCodes) || requiredCodes.length === 0) {
    return "要求操作码列表必填且不能为空";
  }
  return null;
}

/** 判定码列表是否非空但全为空白元素（混合空白不算） */
function isAllBlankCodes(codes: unknown): boolean {
  return (
    Array.isArray(codes) &&
    codes.length > 0 &&
    codes.every(c => !c || !String(c).trim())
  );
}

/** 解析资源对 + 自依赖/空白码/未知操作码预检（对齐后端 20004/20044/20005 顺序；
 *  空白码三入口统一口径：混合空白忽略、全空白 20044）。
 *  返回 [sourceId, targetId] 或错误响应。 */
function resolvePair(body: any): [number, number] | ReturnType<typeof error> {
  const sourceId = resolveResourceId(
    body.sourceResourceTypeCode,
    body.sourceResourceCode,
    body.sourceCodeType
  );
  if (sourceId == null) {
    return error(
      20004,
      `源资源不存在: ${body.sourceResourceTypeCode}/${body.sourceResourceCode}`
    );
  }
  const targetId = resolveResourceId(
    body.targetResourceTypeCode,
    body.targetResourceCode,
    body.targetCodeType
  );
  if (targetId == null) {
    return error(
      20004,
      `目标资源不存在: ${body.targetResourceTypeCode}/${body.targetResourceCode}`
    );
  }
  if (sourceId === targetId) {
    return error(20044, "源资源与目标资源不能相同（自依赖成环）");
  }
  if (
    isAllBlankCodes(body.sourceOperationCodes) ||
    isAllBlankCodes(body.requiredOperationCodes)
  ) {
    // 全空白码列表畸形参数（三入口统一 20044；混合空白由下方未知码检查忽略）
    return error(20044, "operationCodes 不能为全空白元素");
  }
  const unknown = [
    ...collectUnknownCodes(
      body.sourceOperationCodes,
      body.sourceResourceTypeCode
    ),
    ...collectUnknownCodes(
      body.requiredOperationCodes,
      body.targetResourceTypeCode
    )
  ];
  if (unknown.length > 0) {
    return error(20005, `操作权限不存在: ${unknown.join(", ")}`);
  }
  return [sourceId, targetId];
}

/** 判定两条依赖是否语义等价（同源资源对 + 同 sourceOperationBits）。
 *  对齐 schema uk_resource_dependency 唯一约束：
 *  (tenant_id, resource_entity_id, depends_on_resource_entity_id, COALESCE(source_operation_bits,0)) */
function isDuplicate(
  body: any,
  sourceId: number,
  targetId: number,
  excludeId?: number
): boolean {
  const bits = codesToBits(
    body.sourceOperationCodes,
    body.sourceResourceTypeCode
  );
  return deps.some(d => {
    if (d.deleted || d.id === excludeId) return false;
    if (
      d.resourceEntityId !== sourceId ||
      d.dependsOnResourceEntityId !== targetId
    )
      return false;
    const dBits = d.sourceOperationBits ?? "0";
    return dBits === (bits ?? "0");
  });
}

/** 循环检测：DFS 从 target 反查是否能到达 source（对齐后端 canReach）。
 *  若 source===target 直接成环。添加 source->target 后，若 target 能到达 source 则成环。 */
function hasCycle(sourceId: number, targetId: number): boolean {
  if (sourceId === targetId) return true;
  const graph = new Map<number, Set<number>>();
  for (const d of deps) {
    if (d.deleted) continue;
    if (!graph.has(d.resourceEntityId))
      graph.set(d.resourceEntityId, new Set());
    graph.get(d.resourceEntityId)!.add(d.dependsOnResourceEntityId);
  }
  // 添加待检测的边 source->target
  if (!graph.has(sourceId)) graph.set(sourceId, new Set());
  graph.get(sourceId)!.add(targetId);
  // 从 target 出发能否到达 source
  const visited = new Set<number>();
  function canReach(current: number, target: number): boolean {
    if (current === target) return true;
    if (visited.has(current)) return false;
    visited.add(current);
    const neighbors = graph.get(current);
    if (!neighbors) return false;
    for (const next of neighbors) {
      if (canReach(next, target)) return true;
    }
    return false;
  }
  return canReach(targetId, sourceId);
}

export default defineFakeRoute([
  {
    url: "/api/perm/resource-dependency/list",
    method: "post",
    response: ({ body }) => {
      let items = deps.filter(d => !d.deleted);
      // 仅按 resourceEntityId 过滤（对齐后端 DependencyListReq；全量不分页定案）
      if (body?.resourceEntityId != null) {
        items = items.filter(d => d.resourceEntityId === body.resourceEntityId);
      }
      return ok({ items: items.sort((a, b) => a.id - b.id).map(clone) });
    }
  },

  {
    url: "/api/perm/resource-dependency/graph",
    method: "post",
    response: ({ body }) => {
      let items = deps.filter(d => !d.deleted);
      // graph：resourceEntityId=null 返回全量，否则按源资源过滤（对齐后端 graph 实现）
      if (body?.resourceEntityId != null) {
        items = items.filter(d => d.resourceEntityId === body.resourceEntityId);
      }
      return ok({ items: items.sort((a, b) => a.id - b.id).map(clone) });
    }
  },

  {
    url: "/api/perm/resource-dependency/create",
    method: "post",
    response: ({ body }) => {
      const err = validateBody(body);
      if (err) return error(400, err);

      // 对齐后端（2026-08-27 定案 + T-PERM-031 顺序）：autoGrant 预检最先，仅接受 false/省略
      if (body.autoGrant === true) {
        return error(
          20048,
          "autoGrant=true 不支持：自动授权未实现（预留字段），仅接受 false"
        );
      }
      const pair = resolvePair(body);
      if (!Array.isArray(pair)) return pair;
      const [sourceId, targetId] = pair;
      if (isDuplicate(body, sourceId, targetId)) {
        return error(
          20054,
          "等价依赖规则已存在（同源/目标资源对 + 同触发操作位）"
        );
      }

      const sourceRef = getResourceRef(sourceId)!;
      const targetRef = getResourceRef(targetId)!;
      const createdAt = now();
      const created: InternalDep = {
        id: nextId++,
        tenantId: 1,
        resourceEntityId: sourceId,
        sourceResourceCode: sourceRef.code,
        dependsOnResourceEntityId: targetId,
        depResourceCode: targetRef.code,
        sourceOperationBits: codesToBits(
          body.sourceOperationCodes,
          body.sourceResourceTypeCode
        ),
        requiredOperationBits:
          codesToBits(
            body.requiredOperationCodes,
            body.targetResourceTypeCode
          ) ?? "0",
        autoGrant: false,
        description: body.description ?? null,
        ownerServiceCode: null,
        maintainSource: "ADMIN_UI",
        createdAt,
        updatedAt: createdAt,
        deleted: false
      };
      deps.push(created);
      return ok(clone(created));
    }
  },

  {
    url: "/api/perm/resource-dependency/update",
    method: "post",
    response: ({ body }) => {
      const d = deps.find(item => item.id === body?.id && !item.deleted);
      if (!d) return error(20019, "资源依赖不存在");

      const err = validateBody(body);
      if (err) return error(400, err);

      if (body.autoGrant === true) {
        return error(
          20048,
          "autoGrant=true 不支持：自动授权未实现（预留字段），仅接受 false"
        );
      }
      const pair = resolvePair(body);
      if (!Array.isArray(pair)) return pair;
      const [sourceId, targetId] = pair;
      if (isDuplicate(body, sourceId, targetId, d.id)) {
        return error(
          20054,
          "等价依赖规则已存在（同源/目标资源对 + 同触发操作位）"
        );
      }

      // 全量替换（Q3=B）：资源对可改，完整字段覆盖；
      // maintainSource/ownerServiceCode 来源归属不变（对齐后端，仅同步侧可变更）
      const sourceRef = getResourceRef(sourceId)!;
      const targetRef = getResourceRef(targetId)!;
      d.resourceEntityId = sourceId;
      d.sourceResourceCode = sourceRef.code;
      d.dependsOnResourceEntityId = targetId;
      d.depResourceCode = targetRef.code;
      d.sourceOperationBits = codesToBits(
        body.sourceOperationCodes,
        body.sourceResourceTypeCode
      );
      d.requiredOperationBits =
        codesToBits(body.requiredOperationCodes, body.targetResourceTypeCode) ??
        "0";
      d.autoGrant = false;
      d.description = body.description ?? null;
      d.updatedAt = now();
      return ok(clone(d));
    }
  },

  {
    url: "/api/perm/resource-dependency/remove",
    method: "post",
    response: ({ body }) => {
      const ids: number[] = Array.isArray(body?.ids) ? body.ids : [];
      if (ids.length === 0) return error(400, "ids 不能为空");
      for (const d of deps) {
        if (ids.includes(d.id) && !d.deleted) {
          d.deleted = true;
        }
      }
      // 对齐后端：幂等跳过幽灵 id，响应 data=null 无行数（调用方以事后查询核对）
      return ok(null);
    }
  },

  {
    url: "/api/perm/resource-dependency/check",
    method: "post",
    response: ({ body }) => {
      if (!body?.sourceResourceTypeCode || !body.sourceResourceCode) {
        return error(400, "源资源类型与编码必填");
      }
      if (!body?.targetResourceTypeCode || !body.targetResourceCode) {
        return error(400, "目标资源类型与编码必填");
      }
      const sourceId = resolveResourceId(
        body.sourceResourceTypeCode,
        body.sourceResourceCode,
        body.sourceCodeType
      );
      if (sourceId == null) {
        return error(
          20004,
          `源资源不存在: ${body.sourceResourceTypeCode}/${body.sourceResourceCode}`
        );
      }
      const targetId = resolveResourceId(
        body.targetResourceTypeCode,
        body.targetResourceCode,
        body.targetCodeType
      );
      if (targetId == null) {
        return error(
          20004,
          `目标资源不存在: ${body.targetResourceTypeCode}/${body.targetResourceCode}`
        );
      }
      const hasCycleResult = hasCycle(sourceId, targetId);
      return ok({
        hasCycle: hasCycleResult,
        sourceResourceTypeCode: body.sourceResourceTypeCode,
        sourceResourceCode: body.sourceResourceCode,
        targetResourceTypeCode: body.targetResourceTypeCode,
        targetResourceCode: body.targetResourceCode
      });
    }
  }
]);
