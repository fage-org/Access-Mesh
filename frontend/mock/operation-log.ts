// 操作日志 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 OperationLogResp
// 契约依据：docs/design/permission-center/api-contract.md §5.8（操作日志仅 1 行表格条目，路径写错且无独立字段契约章节）
// 表结构：docs/design/schema/permission-center.sql:661-690
// 后端实现：LogQueryController（@RequestMapping("/api/perm/log")）+ LogQueryAppServiceImpl.listOperationLogs
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/**
 * 本地声明类型（不 import src/api/operation-log，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 system-config.ts mock 零 src 依赖范式一致。
 * 字段定义同步注释于下方，保持与 api 层对齐。
 */

/** 操作日志响应（对齐 src/api/operation-log.ts 的 OperationLogResp / 后端 OperationLogResp）。 */
type OperationLogResp = {
  id: number;
  tenantId?: number;
  module: string;
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  summary?: string | null;
  operatorId?: number | null;
  operatorName?: string | null;
  ipAddress?: string | null;
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

// ========== Mock 数据：操作日志（覆盖多 module/action 组合） ==========

/**
 * operation_log 内存数据。
 *
 * module 取值对齐后端 operation_log.module 三值（T-ACCESS-007：ADMIN=管理域 /
 * PERMISSION=权限域 / ACCESS=跨域编排，按事务边界判定）。
 * action 取值对齐后端 `{业务对象}_{动作}` 大写事件码（各业务方法 @OperationLog 注解维护）。
 * targetType 保留细粒度物理表名（小写表名），targetId 为字符串（对齐后端 VARCHAR(256)）。
 *
 * 每条含 operatorName/ipAddress/requestId/summary/targetType/targetId/createdAt，
 * 覆盖抽屉详情全字段展示。createdAt 固定字符串（脚本禁用 Date.now），按 DESC 排序验证分页。
 */
const mockLogs: OperationLogResp[] = [
  {
    id: 1,
    tenantId: 1,
    module: "ADMIN",
    action: "CONFIG_UPDATE",
    targetType: "system_config",
    targetId: "",
    summary: '保存系统配置 ROLE_NAME_UNIQUE_MODE = {"mode":"DOMAIN_UNIQUE"}',
    operatorId: 1,
    operatorName: "超级管理员",
    ipAddress: "192.168.1.10",
    requestId: "req-001",
    createdAt: "2026-07-01 09:30:00"
  },
  {
    id: 2,
    tenantId: 1,
    module: "PERMISSION",
    action: "ROLE_RESOURCE_PERMISSION_GRANT",
    targetType: "abstract_role",
    targetId: "101",
    summary: "为角色 admin 批量授予 12 项资源权限（含 2 项条件）",
    operatorId: 2,
    operatorName: "安全管理员",
    ipAddress: "192.168.1.11",
    requestId: "req-002",
    createdAt: "2026-07-01 10:15:00"
  },
  {
    id: 3,
    tenantId: 1,
    module: "PERMISSION",
    action: "RESOURCE_ENTITY_CREATE",
    targetType: "resource_entity",
    targetId: "5",
    summary: "新建资源类型 API（resourceTypeCode 分派为 5）",
    operatorId: 2,
    operatorName: "安全管理员",
    ipAddress: "192.168.1.11",
    requestId: "req-003",
    createdAt: "2026-06-30 14:20:00"
  },
  {
    id: 4,
    tenantId: 1,
    module: "ADMIN",
    action: "USER_UPDATE",
    targetType: "sys_user",
    targetId: "2001",
    summary: "更新用户 zhangsan 的状态为启用",
    operatorId: 3,
    operatorName: "组织人事管理员",
    ipAddress: "192.168.1.12",
    requestId: "req-004",
    createdAt: "2026-06-30 16:45:00"
  },
  {
    id: 5,
    tenantId: 1,
    module: "PERMISSION",
    action: "ABSTRACT_ROLE_FULL_SYNC",
    targetType: "abstract_role",
    targetId: "",
    summary: "全量同步角色 temp-role（软删除，关联用户角色关系同步清理）",
    operatorId: 2,
    operatorName: "安全管理员",
    ipAddress: "192.168.1.11",
    requestId: "req-005",
    createdAt: "2026-06-29 11:00:00"
  },
  {
    id: 6,
    tenantId: 1,
    module: "PERMISSION",
    action: "RESOURCE_ENTITY_SYNC",
    targetType: "resource_entity",
    targetId: "accessmesh-admin",
    summary: "同步服务 accessmesh-admin 的 8 个接口资源映射",
    operatorId: 2,
    operatorName: "安全管理员",
    ipAddress: "192.168.1.11",
    requestId: "req-006",
    createdAt: "2026-06-29 13:30:00"
  },
  {
    id: 7,
    tenantId: 1,
    module: "ACCESS",
    action: "USER_ORG_ASSIGN",
    targetType: "sys_user_org",
    targetId: "2001",
    summary: "为用户 zhangsan 分配组织 position-engineer",
    operatorId: 3,
    operatorName: "组织人事管理员",
    ipAddress: "192.168.1.12",
    requestId: "req-007",
    createdAt: "2026-06-28 09:10:00"
  },
  {
    id: 8,
    tenantId: 1,
    module: "PERMISSION",
    action: "OPERATION_PERMISSION_UPDATE",
    targetType: "operation_permission",
    targetId: "2",
    summary: "更新操作权限 role_type / POSITION 名称",
    operatorId: 2,
    operatorName: "安全管理员",
    ipAddress: "192.168.1.11",
    requestId: "req-008",
    createdAt: "2026-06-28 15:25:00"
  },
  {
    id: 9,
    tenantId: 1,
    module: "ADMIN",
    action: "CONFIG_UPDATE",
    targetType: "system_config",
    targetId: "",
    summary: '保存系统配置 UNREGISTERED_API_POLICY = {"mode":"DENY"}',
    operatorId: 1,
    operatorName: "超级管理员",
    ipAddress: "192.168.1.10",
    requestId: "req-009",
    createdAt: "2026-06-27 10:00:00"
  },
  {
    id: 10,
    tenantId: 1,
    module: "PERMISSION",
    action: "ABSTRACT_ROLE_SYNC",
    targetType: "abstract_role",
    targetId: "110",
    summary: "同步角色 audit-viewer（只读审计角色）",
    operatorId: 2,
    operatorName: "安全管理员",
    ipAddress: "192.168.1.11",
    requestId: "req-010",
    createdAt: "2026-06-27 14:40:00"
  },
  {
    id: 11,
    tenantId: 1,
    module: "ACCESS",
    action: "USER_ORG_ASSIGN",
    targetType: "sys_user_org",
    targetId: "2002",
    summary: "为用户 lisi 分配组织 system-admin",
    operatorId: 3,
    operatorName: "组织人事管理员",
    ipAddress: "192.168.1.12",
    requestId: "req-011",
    createdAt: "2026-06-26 11:20:00"
  },
  {
    id: 12,
    tenantId: 1,
    module: "ACCESS",
    action: "USER_DELETE",
    targetType: "sys_user",
    targetId: "2003",
    summary: "删除用户 wangwu（孤儿 user_role 关系延迟补偿清理）",
    operatorId: 1,
    operatorName: "超级管理员",
    ipAddress: "192.168.1.10",
    requestId: "req-012",
    createdAt: "2026-06-26 16:50:00"
  }
];

/** 深拷贝（返回给前端，避免外部修改内存） */
function clone(l: OperationLogResp): OperationLogResp {
  return { ...l };
}

export default defineFakeRoute([
  // 列表：对齐后端 PaginatedResp（服务端分页 + module/action 过滤）。
  // 后端 OperationLogListReq：module?/action?/pageNum @NotNull/pageSize @NotNull。
  // 按 createdAt DESC 排序后过滤 + offset/limit 切片，返回分页结构。
  {
    url: "/api/perm/log/operation/list",
    method: "post",
    response: ({ body }) => {
      const { module, action, pageNum, pageSize } = body || {};
      const page = pageNum && pageNum > 0 ? pageNum : 1;
      const size = pageSize && pageSize > 0 ? pageSize : 15;
      // 按 createdAt DESC 排序（字符串可比，格式 yyyy-MM-dd HH:mm:ss）
      let list = mockLogs.slice().sort((a, b) => {
        const ta = a.createdAt || "";
        const tb = b.createdAt || "";
        return tb.localeCompare(ta);
      });
      // module/action 过滤（null/空不过滤）
      if (module) {
        list = list.filter(l => l.module === module);
      }
      if (action) {
        list = list.filter(l => l.action === action);
      }
      const total = list.length;
      const offset = (page - 1) * size;
      const items = list.slice(offset, offset + size).map(clone);
      const hasNext = offset + items.length < total;
      const data: PaginatedResp<OperationLogResp> = {
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
