// 资源依赖 Mock（T-FE-011）。
// 对齐 permission-center 的 resource-dependency 管理接口；
// 资源依赖 CRUD + 依赖图 + 循环检测。
//
// ⚠️ 禁止 import src/api：fake-server 静默吞加载错误会致 404，类型/常量本地声明。
//
// 种子 ID 对齐（名称/类型映射由前端 hook 加载 resource-operation mock 建立）：
// - 资源：201-204 MENU / 211-212 BUTTON / 221-222 API / 231-232 DATA（见 mock/resource-operation.ts）
// - 操作位：CREATE=1 VIEW=2 UPDATE=4 DELETE=8 MANAGE=16（见 mock/resource-operation.ts CRUD_OPS）
//
// 对齐后端 DependencyAppServiceImpl（T-PERM-031 核对）：
// - create/update 用业务键（sourceResourceTypeCode+sourceResourceCode+...），mock 内部解析为资源 ID
// - update 全量替换（对齐 conflict-rule 范式，Q3=B）：资源对可改，mock 支持完整字段覆盖
// - check 循环检测：DFS 从 target 反查是否能到达 source（对齐后端 hasDependencyCycle.canReach）
// - graph 返回扁平依赖列表（对齐后端 graph，前端建 nodes/edges）
// - list 仅按 resourceEntityId 过滤（对齐后端 DependencyListReq）
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
  dependsOnResourceEntityId: number;
  depResourceCode: string;
  sourceOperationBits: number | null;
  requiredOperationBits: number;
  autoGrant: boolean;
  description: string | null;
  createdAt: string;
};

type InternalDep = ResourceDependencyResp & { deleted: boolean };

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
    sourceOperationBits: 2,
    requiredOperationBits: 2,
    autoGrant: true,
    description: "访问角色管理需先通过鉴权校验",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 802,
    tenantId: 1,
    resourceEntityId: 204,
    sourceResourceCode: "res-op",
    dependsOnResourceEntityId: 222,
    depResourceCode: "res-tree",
    sourceOperationBits: 2,
    requiredOperationBits: 2,
    autoGrant: true,
    description: "资源与操作页需资源树查询接口",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 803,
    tenantId: 1,
    resourceEntityId: 202,
    sourceResourceCode: "user",
    dependsOnResourceEntityId: 231,
    depResourceCode: "dept-data",
    sourceOperationBits: 2,
    requiredOperationBits: 2,
    autoGrant: true,
    description: "组织与用户页依赖部门数据",
    createdAt: BASE_TIME,
    deleted: false
  },
  {
    id: 804,
    tenantId: 1,
    resourceEntityId: 212,
    sourceResourceCode: "btn-edit",
    dependsOnResourceEntityId: 232,
    depResourceCode: "role-data",
    sourceOperationBits: 4,
    requiredOperationBits: 2,
    autoGrant: false,
    description: "编辑按钮依赖角色数据查看（手动补全）",
    createdAt: BASE_TIME,
    deleted: false
  }
];

// ========== 工具函数 ==========

function clone(d: InternalDep): ResourceDependencyResp {
  const { deleted: _d, ...resp } = d;
  return { ...resp };
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

/** 操作码列表 -> 操作位掩码（对齐后端 resolveOperationBits）。
 *  按资源类型解析（当前类型 + 全局操作 resourceTypeCode=null），空列表返回 null（=任意操作触发）。
 *  BigInt 位或，兼容 63 位 bigint 列（JS |= 截断 32 位，P1 修复）。 */
function codesToBits(
  codes: string[] | null | undefined,
  resourceTypeCode: string
): number | null {
  if (!codes || codes.length === 0) return null;
  let bits = 0n;
  for (const c of codes) {
    const op = operations.find(
      o =>
        (o.resourceTypeCode === resourceTypeCode ||
          o.resourceTypeCode == null) &&
        o.code === c
    );
    if (op) bits |= BigInt(op.binaryBit);
  }
  return Number(bits);
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
    const dBits = d.sourceOperationBits ?? 0;
    return dBits === (bits ?? 0);
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
      // 🔧 仅按 resourceEntityId 过滤（对齐后端 DependencyListReq）
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

      const sourceId = resolveResourceId(
        body.sourceResourceTypeCode,
        body.sourceResourceCode,
        body.sourceCodeType
      );
      if (sourceId == null) {
        return error(
          404,
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
          404,
          `目标资源不存在: ${body.targetResourceTypeCode}/${body.targetResourceCode}`
        );
      }
      if (isDuplicate(body, sourceId, targetId)) {
        return error(409, "等价依赖规则已存在（同源/目标资源对 + 同触发操作）");
      }

      const sourceRef = getResourceRef(sourceId)!;
      const targetRef = getResourceRef(targetId)!;
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
          ) ?? 0,
        autoGrant: body.autoGrant != null ? body.autoGrant : true,
        description: body.description ?? null,
        createdAt: now(),
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
      if (!d) return error(404, "资源依赖不存在");

      const err = validateBody(body);
      if (err) return error(400, err);

      const sourceId = resolveResourceId(
        body.sourceResourceTypeCode,
        body.sourceResourceCode,
        body.sourceCodeType
      );
      if (sourceId == null) {
        return error(
          404,
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
          404,
          `目标资源不存在: ${body.targetResourceTypeCode}/${body.targetResourceCode}`
        );
      }
      if (isDuplicate(body, sourceId, targetId, d.id)) {
        return error(409, "等价依赖规则已存在（同源/目标资源对 + 同触发操作）");
      }

      // 全量替换（对齐 conflict-rule UpdateReq 范式，Q3=B）：资源对可改，完整字段覆盖
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
        0;
      d.autoGrant = body.autoGrant != null ? body.autoGrant : true;
      d.description = body.description ?? null;
      return ok(clone(d));
    }
  },

  {
    url: "/api/perm/resource-dependency/remove",
    method: "post",
    response: ({ body }) => {
      const ids: number[] = Array.isArray(body?.ids) ? body.ids : [];
      if (ids.length === 0) return error(400, "ids 不能为空");
      let count = 0;
      for (const d of deps) {
        if (ids.includes(d.id) && !d.deleted) {
          d.deleted = true;
          count += 1;
        }
      }
      return ok({ removed: count });
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
          404,
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
          404,
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
