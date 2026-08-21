// 权限变更日志 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 ChangeLogResp
// 契约依据：docs/design/permission-center/api-contract.md §5.8（变更日志仅 1 行表格条目，路径写错且无独立字段契约章节）
//           §6.8 diff_snapshot 轻量规范（L1590-1671）
// 表结构：docs/design/schema/permission-center.sql:600-632
// 后端实现：LogQueryController（@RequestMapping("/api/perm/log")）+ LogQueryAppServiceImpl.listChangeLogs
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/**
 * 本地声明类型（不 import src/api/permission-change-log，避免 fake-server 经 bundle-import 打包 src/api 链--
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 -> 路由不注册 -> 404）。与 operation-log.ts mock 零 src 依赖范式一致。
 * 字段定义同步注释于下方，保持与 api 层对齐。
 */

/** 变更日志响应（对齐 src/api/permission-change-log.ts 的 ChangeLogResp / 后端 ChangeLogResp） */
type ChangeLogResp = {
  id: number;
  tenantId?: number;
  entityType: string;
  entityId?: number | null;
  operation: string; // INSERT/UPDATE/DELETE（实体层）
  oldSnapshot?: string | null;
  newSnapshot?: string | null;
  diffSnapshot?: string | null; // JSON string，§6.8 规范
  affectedAbstractUserIds?: number[] | null;
  affectedAbstractRoleIds?: number[] | null;
  changeReason?: string | null;
  changeSource?: string | null; // MANUAL/SERVICE_SYNC（后端复用 MaintainSource）
  requestId?: string | null;
  createdAt?: string;
};

/** 分页响应（对齐后端 PaginatedResp） */
type PaginatedResp<T> = {
  items: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  hasNext: boolean;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

// ========== Mock 数据：变更日志（覆盖 7 种 eventType + 多 entityType/operation） ==========

/**
 * permission_change_log 内存数据。
 *
 * entityType 取值对齐 schema 注释（permission-center.sql:626）：
 * - user_role / role_resource_permission / abstract_user / abstract_role / resource_entity 等
 * operation 取值对齐 schema 注释（permission-center.sql:627）：INSERT/UPDATE/DELETE
 * changeSource 取值对齐后端代码实际（PermConstants.MaintainSource 复用）：MANUAL/SERVICE_SYNC
 *   🔧 schema L631 注释写 ADMIN/SYNC/API/SYSTEM 与代码不符，登记 T-PERM-032。
 * diffSnapshot 遵循 §6.8 L1590-1671 规范：eventType（7 枚举）+ items[]（changeType ADD/REMOVE/UPDATE）。
 *
 * 覆盖全部 7 种 eventType，验证 diff 面板结构化渲染能力（permission/role/resource/before-after 组合）。
 * createdAt 固定字符串（脚本禁用 Date.now），按 DESC 排序验证分页。
 */
const mockLogs: ChangeLogResp[] = [
  {
    id: 1,
    tenantId: 1,
    entityType: "role_resource_permission",
    entityId: 1001,
    operation: "DELETE",
    oldSnapshot:
      '{"id":1001,"abstractRoleId":101,"operationBits":8192,"conditionId":null}',
    newSnapshot: null,
    diffSnapshot: JSON.stringify({
      eventType: "ROLE_PERMISSION_CHANGE",
      items: [
        {
          changeType: "REMOVE",
          permission: {
            domainCode: "example",
            resourceTypeCode: "REPORT",
            resourceCode: "report:sales",
            codeType: "default",
            operationCode: "DATA_EDIT",
            scopeMode: "INSTANCE"
          },
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_report_editor",
            roleName: "报表编辑员"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [2001, 2002],
    affectedAbstractRoleIds: [101],
    changeReason: "回收报表编辑员对销售报表的编辑权限",
    changeSource: "MANUAL",
    requestId: "req-cl-001",
    createdAt: "2026-07-11 09:30:00"
  },
  {
    id: 2,
    tenantId: 1,
    entityType: "user_role",
    entityId: 3001,
    operation: "DELETE",
    oldSnapshot:
      '{"id":3001,"abstractUserId":2001,"abstractRoleId":102,"source":"POSITION"}',
    newSnapshot: null,
    diffSnapshot: JSON.stringify({
      eventType: "USER_ROLE_CHANGE",
      items: [
        {
          changeType: "REMOVE",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_finance_manager",
            roleName: "财务主管"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [2001],
    affectedAbstractRoleIds: [102],
    changeReason: "用户移出财务主管角色",
    changeSource: "MANUAL",
    requestId: "req-cl-002",
    createdAt: "2026-07-11 10:15:00"
  },
  {
    id: 3,
    tenantId: 1,
    entityType: "resource_entity",
    entityId: 5001,
    operation: "UPDATE",
    oldSnapshot: '{"id":5001,"resourceCode":"report:sales","status":1}',
    newSnapshot: '{"id":5001,"resourceCode":"report:sales","status":0}',
    diffSnapshot: JSON.stringify({
      eventType: "RESOURCE_STATUS_CHANGE",
      items: [
        {
          changeType: "UPDATE",
          resource: {
            domainCode: "example",
            resourceTypeCode: "REPORT",
            resourceCode: "report:sales",
            codeType: "default"
          },
          before: { status: 1 },
          after: { status: 0 }
        }
      ]
    }),
    affectedAbstractUserIds: [2001, 2002, 2003],
    affectedAbstractRoleIds: [101, 103],
    changeReason: "销售报表资源停用",
    changeSource: "MANUAL",
    requestId: "req-cl-003",
    createdAt: "2026-07-10 14:20:00"
  },
  {
    id: 4,
    tenantId: 1,
    entityType: "abstract_role",
    entityId: 101,
    operation: "UPDATE",
    oldSnapshot: '{"id":101,"roleTypeCode":"BASIC_ROLE","status":1}',
    newSnapshot: '{"id":101,"roleTypeCode":"BASIC_ROLE","status":0}',
    diffSnapshot: JSON.stringify({
      eventType: "ROLE_STATUS_CHANGE",
      items: [
        {
          changeType: "UPDATE",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_report_editor",
            roleName: "报表编辑员"
          },
          before: { status: 1 },
          after: { status: 0 }
        }
      ]
    }),
    affectedAbstractUserIds: [2001, 2002],
    affectedAbstractRoleIds: [101],
    changeReason: "报表编辑员角色停用",
    changeSource: "MANUAL",
    requestId: "req-cl-004",
    createdAt: "2026-07-10 16:45:00"
  },
  {
    id: 5,
    tenantId: 1,
    entityType: "role_resource_permission",
    entityId: 1002,
    operation: "INSERT",
    oldSnapshot: null,
    newSnapshot:
      '{"id":1002,"abstractRoleId":103,"operationBits":512,"conditionId":50}',
    diffSnapshot: JSON.stringify({
      eventType: "ROLE_PERMISSION_CHANGE",
      items: [
        {
          changeType: "ADD",
          permission: {
            domainCode: "example",
            resourceTypeCode: "REPORT",
            resourceCode: "report:finance",
            codeType: "default",
            operationCode: "DATA_READ",
            scopeMode: "INSTANCE"
          },
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_finance_viewer",
            roleName: "财务查看员"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [2003],
    affectedAbstractRoleIds: [103],
    changeReason: "为财务查看员授予财务报表读取权限（含时间条件）",
    changeSource: "MANUAL",
    requestId: "req-cl-005",
    createdAt: "2026-07-09 11:00:00"
  },
  {
    id: 6,
    tenantId: 1,
    entityType: "permission_condition",
    entityId: 50,
    operation: "UPDATE",
    oldSnapshot:
      '{"id":50,"code":"cond_worktime","logic":"AND","items":[{"type":"TIME_RANGE","start":"09:00","end":"18:00"}]}',
    newSnapshot:
      '{"id":50,"code":"cond_worktime","logic":"AND","items":[{"type":"TIME_RANGE","start":"08:30","end":"17:30"}]}',
    diffSnapshot: JSON.stringify({
      eventType: "CONDITION_CHANGE",
      items: [
        {
          changeType: "UPDATE",
          before: { timeRange: "09:00-18:00" },
          after: { timeRange: "08:30-17:30" }
        }
      ]
    }),
    affectedAbstractUserIds: [2003],
    affectedAbstractRoleIds: [103],
    changeReason: "调整工作时间条件范围",
    changeSource: "MANUAL",
    requestId: "req-cl-006",
    createdAt: "2026-07-09 13:30:00"
  },
  {
    id: 7,
    tenantId: 1,
    entityType: "abstract_role",
    entityId: 201,
    operation: "INSERT",
    oldSnapshot: null,
    newSnapshot:
      '{"id":201,"roleTypeCode":"GROUP_ROLE","externalId":"role_group_audit","name":"审计组"}',
    diffSnapshot: JSON.stringify({
      eventType: "GROUP_ROLE_CHANGE",
      items: [
        {
          changeType: "ADD",
          role: {
            roleTypeCode: "GROUP_ROLE",
            roleExternalId: "role_group_audit",
            roleName: "审计组"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [],
    affectedAbstractRoleIds: [201],
    changeReason: "新建审计分组角色",
    changeSource: "MANUAL",
    requestId: "req-cl-007",
    createdAt: "2026-07-08 09:10:00"
  },
  {
    id: 8,
    tenantId: 1,
    entityType: "resource_dependency",
    entityId: 7001,
    operation: "INSERT",
    oldSnapshot: null,
    newSnapshot:
      '{"id":7001,"resourceEntityId":5001,"dependsOnResourceEntityId":6001,"sourceOperationBits":512,"requiredOperationBits":1,"autoGrant":true}',
    diffSnapshot: JSON.stringify({
      eventType: "RESOURCE_DEPENDENCY_CHANGE",
      items: [
        {
          changeType: "ADD",
          resource: {
            domainCode: "example",
            resourceTypeCode: "REPORT",
            resourceCode: "report:sales",
            codeType: "default"
          },
          message: "新增依赖：授权销售报表读取时自动补齐查询接口 ACCESS"
        }
      ]
    }),
    affectedAbstractUserIds: [],
    affectedAbstractRoleIds: [],
    changeReason: "配置销售报表->查询接口的自动授权依赖",
    changeSource: "MANUAL",
    requestId: "req-cl-008",
    createdAt: "2026-07-08 15:25:00"
  },
  {
    id: 9,
    tenantId: 1,
    entityType: "resource_entity",
    entityId: 6001,
    operation: "INSERT",
    oldSnapshot: null,
    newSnapshot:
      '{"id":6001,"resourceTypeCode":"API","resourceCode":"api:report:sales:query","maintainSource":"SERVICE_SYNC"}',
    diffSnapshot: JSON.stringify({
      eventType: "RESOURCE_STATUS_CHANGE",
      items: [
        {
          changeType: "ADD",
          resource: {
            domainCode: "example",
            resourceTypeCode: "API",
            resourceCode: "api:report:sales:query",
            codeType: "default"
          },
          after: { status: 1, maintainSource: "SERVICE_SYNC" }
        }
      ]
    }),
    affectedAbstractUserIds: [],
    affectedAbstractRoleIds: [],
    changeReason: "服务同步新增接口资源",
    changeSource: "SERVICE_SYNC",
    requestId: "req-cl-009",
    createdAt: "2026-07-07 10:00:00"
  },
  {
    id: 10,
    tenantId: 1,
    entityType: "user_role",
    entityId: 3002,
    operation: "INSERT",
    oldSnapshot: null,
    newSnapshot:
      '{"id":3002,"abstractUserId":2002,"abstractRoleId":104,"source":"MANUAL"}',
    diffSnapshot: JSON.stringify({
      eventType: "USER_ROLE_CHANGE",
      items: [
        {
          changeType: "ADD",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_position_engineer",
            roleName: "岗位工程师"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [2002],
    affectedAbstractRoleIds: [104],
    changeReason: "为用户分配岗位工程师角色",
    changeSource: "MANUAL",
    requestId: "req-cl-010",
    createdAt: "2026-07-07 14:40:00"
  },
  {
    id: 11,
    tenantId: 1,
    entityType: "role_resource_permission",
    entityId: 1003,
    operation: "UPDATE",
    oldSnapshot:
      '{"id":1003,"abstractRoleId":104,"operationBits":512,"conditionId":null}',
    newSnapshot:
      '{"id":1003,"abstractRoleId":104,"operationBits":512,"conditionId":50}',
    diffSnapshot: JSON.stringify({
      eventType: "ROLE_PERMISSION_CHANGE",
      items: [
        {
          changeType: "UPDATE",
          permission: {
            domainCode: "example",
            resourceTypeCode: "REPORT",
            resourceCode: "report:sales",
            codeType: "default",
            operationCode: "DATA_READ",
            scopeMode: "INSTANCE"
          },
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_position_engineer",
            roleName: "岗位工程师"
          },
          before: { conditionId: null },
          after: { conditionId: 50 }
        }
      ]
    }),
    affectedAbstractUserIds: [2002],
    affectedAbstractRoleIds: [104],
    changeReason: "为岗位工程师的报表读取权限绑定工作时间条件",
    changeSource: "MANUAL",
    requestId: "req-cl-011",
    createdAt: "2026-07-06 11:20:00"
  },
  {
    id: 12,
    tenantId: 1,
    entityType: "abstract_role",
    entityId: 105,
    operation: "DELETE",
    oldSnapshot:
      '{"id":105,"roleTypeCode":"BASIC_ROLE","externalId":"role_temp","name":"临时角色"}',
    newSnapshot: null,
    diffSnapshot: JSON.stringify({
      eventType: "ROLE_STATUS_CHANGE",
      items: [
        {
          changeType: "REMOVE",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_temp",
            roleName: "临时角色"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [],
    affectedAbstractRoleIds: [105],
    changeReason: "删除临时角色（软删除，关联用户角色关系同步清理）",
    changeSource: "MANUAL",
    requestId: "req-cl-012",
    createdAt: "2026-07-06 16:50:00"
  },
  {
    // 批量删除角色聚合日志：entityId=0 对齐后端 RoleManageAppServiceImpl:300
    //（批量操作无单一实体 ID，用 0 作聚合标记；前端筛选 entityId=0 可命中）
    id: 13,
    tenantId: 1,
    entityType: "abstract_role",
    entityId: 0,
    operation: "BATCH_DELETE",
    oldSnapshot: null,
    newSnapshot: null,
    diffSnapshot: JSON.stringify({
      // 后端实际写 "ROLE_BATCH_DELETE"（超出 §6.8 7 枚举），前端 EVENT_TYPE_META fallback 显示原值不崩溃
      eventType: "ROLE_BATCH_DELETE",
      items: [
        {
          changeType: "REMOVE",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_temp_a",
            roleName: "临时角色A"
          }
        },
        {
          changeType: "REMOVE",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_temp_b",
            roleName: "临时角色B"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [2004, 2005],
    affectedAbstractRoleIds: [106, 107],
    changeReason: "批量删除临时角色（聚合日志，entityId=0）",
    changeSource: "MANUAL",
    requestId: "req-cl-013",
    createdAt: "2026-07-06 09:00:00"
  },
  {
    // 批量撤销用户角色聚合日志：entityId=0 对齐后端 UserManageAppServiceImpl:672
    id: 14,
    tenantId: 1,
    entityType: "user_role",
    entityId: 0,
    operation: "BATCH_REMOVE",
    oldSnapshot: null,
    newSnapshot: null,
    diffSnapshot: JSON.stringify({
      eventType: "USER_ROLE_CHANGE",
      items: [
        {
          changeType: "REMOVE",
          role: {
            roleTypeCode: "BASIC_ROLE",
            roleExternalId: "role_position_engineer",
            roleName: "岗位工程师"
          }
        }
      ]
    }),
    affectedAbstractUserIds: [2006, 2007],
    affectedAbstractRoleIds: [104],
    changeReason: "批量撤销用户岗位工程师角色（聚合日志，entityId=0）",
    changeSource: "MANUAL",
    requestId: "req-cl-014",
    createdAt: "2026-07-05 15:30:00"
  }
];

/** 深拷贝（返回给前端，避免外部修改内存） */
function clone(l: ChangeLogResp): ChangeLogResp {
  return { ...l };
}

export default defineFakeRoute([
  // 列表：对齐后端 PaginatedResp（服务端分页 + entityType/entityId 过滤）。
  // 后端 ChangeLogListReq：entityType?/entityId?/pageNum @NotNull/pageSize @NotNull。
  // 按 createdAt DESC 排序后过滤 + offset/limit 切片，返回分页结构。
  {
    url: "/api/perm/log/change/list",
    method: "post",
    response: ({ body }) => {
      const { entityType, entityId, pageNum, pageSize } = body || {};
      const page = pageNum && pageNum > 0 ? pageNum : 1;
      const size = pageSize && pageSize > 0 ? pageSize : 15;
      // 按 createdAt DESC 排序（字符串可比，格式 yyyy-MM-dd HH:mm:ss）
      let list = mockLogs.slice().sort((a, b) => {
        const ta = a.createdAt || "";
        const tb = b.createdAt || "";
        return tb.localeCompare(ta);
      });
      // entityType/entityId 过滤（null/空不过滤）
      if (entityType) {
        list = list.filter(l => l.entityType === entityType);
      }
      if (entityId != null && entityId !== "") {
        list = list.filter(l => l.entityId === entityId);
      }
      const total = list.length;
      const offset = (page - 1) * size;
      const items = list.slice(offset, offset + size).map(clone);
      const hasNext = offset + items.length < total;
      const data: PaginatedResp<ChangeLogResp> = {
        items,
        total,
        pageNum: page,
        pageSize: size,
        hasNext
      };
      return ok(data);
    }
  }
]);
