// 业务域 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 permission-center 的 BizDomainResp
// 契约依据：docs/design/permission-center/api-contract.md §5.1（类型与域）
// 表结构：docs/design/schema/permission-center.sql:55-72
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/**
 * 本地声明类型（不 import src/api/biz-domain，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 system-config.ts mock 零 src 依赖范式一致。
 * 字段定义同步注释于下方，保持与 api 层对齐。
 */

/** 业务域响应（对齐 src/api/biz-domain.ts 的 BizDomainResp / 后端 BizDomainResp）。
 *  🔧 后端 Resp 不返回 global（entity/schema 有 global，每租户仅一个全局域），前端无法区分全局域，
 *  登记 T-PERM-026。mock 内部用 global 字段做「全局域不可删」校验，但响应 clone 时剔除 global，对齐后端现状。
 *  deleted 为 mock 内部软删标记（对齐 schema delete_flag），不出现在真实响应中。 */
type BizDomainResp = {
  id: number;
  tenantId?: number;
  code: string;
  name: string;
  description?: string | null;
  createdAt?: string;
  /** mock 内部：是否全局域（每租户仅一个，不可删）——对齐 schema global，但后端 Resp 不返回 */
  global?: boolean;
  /** mock 内部：软删标记 */
  deleted?: boolean;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

// ========== Mock 数据：业务域（含一个全局域） ==========

/**
 * biz_domain 内存数据。
 *
 * schema（permission-center.sql:55-72）：
 * - uk_biz_domain(tenant_id, code) WHERE delete_flag=0 —— 租户内 code 唯一
 * - uk_biz_domain_global(tenant_id) WHERE global=TRUE AND delete_flag=0 —— 每租户仅一个全局域
 *
 * 全局域 GLOBAL：隐式包含未被其他域认领的资源类型（DomainClassifyService 全局域语义）。
 * 业务域 HR/ORDER/CRM/ASSET 覆盖典型业务场景。
 */
const mockDomains: BizDomainResp[] = [
  {
    id: 1,
    tenantId: 1,
    code: "GLOBAL",
    name: "全局域",
    description:
      "全局域：隐式包含未被其他域认领的资源类型，每租户仅一个，不可删除",
    createdAt: "2026-01-01 00:00:00",
    global: true
  },
  {
    id: 2,
    tenantId: 1,
    code: "HR",
    name: "人事域",
    description: "组织与用户相关资源的业务域",
    createdAt: "2026-01-02 00:00:00",
    global: false
  },
  {
    id: 3,
    tenantId: 1,
    code: "ORDER",
    name: "订单域",
    description: "订单业务相关资源的业务域",
    createdAt: "2026-01-03 00:00:00",
    global: false
  },
  {
    id: 4,
    tenantId: 1,
    code: "CRM",
    name: "客户域",
    description: "客户关系管理相关资源的业务域",
    createdAt: "2026-01-04 00:00:00",
    global: false
  },
  {
    id: 5,
    tenantId: 1,
    code: "ASSET",
    name: "资产域",
    description: "资产设备管理相关资源的业务域",
    createdAt: "2026-01-05 00:00:00",
    global: false
  }
];

/** 自增 id（已用最大 id + 1） */
let _nextId = mockDomains.reduce((max, d) => Math.max(max, d.id), 0) + 1;

/** 深拷贝并剔除 mock 内部字段（global/deleted），对齐后端 Resp 现状（不返回 global）。 */
function clone(d: BizDomainResp): BizDomainResp {
  const { global: _g, deleted: _del, ...rest } = d;
  return { ...rest };
}

export default defineFakeRoute([
  // 列表：对齐后端 ItemsResp（全量，无分页，登记 T-PERM-026 🔧）。
  // 前端 hook 本地做 keyword 过滤 + 切片分页；此处仅排除已软删行，按 createdAt 排序。
  // 后端 list 接 EmptyReq 无参。
  {
    url: "/api/perm/biz-domain/list",
    method: "post",
    response: () => {
      const items = mockDomains
        .filter(d => !d.deleted)
        .sort((a, b) => (a.createdAt ?? "").localeCompare(b.createdAt ?? ""))
        .map(clone);
      return ok({ items });
    }
  },
  // 详情：按 id 查（对齐后端 IdReq{id}，🔧 切业务键 code 登记 T-PERM-026）
  {
    url: "/api/perm/biz-domain/detail",
    method: "post",
    response: ({ body }) => {
      const { id } = body || {};
      const found = mockDomains.find(d => d.id === id && !d.deleted);
      if (!found) return err(404, "业务域不存在");
      return ok(clone(found));
    }
  },
  // 创建：校验 code 非空 + 租户内唯一（uk_biz_domain），insert 自动分配 id+时间戳。
  {
    url: "/api/perm/biz-domain/create",
    method: "post",
    response: ({ body }) => {
      const { code, name, description } = body || {};
      if (!code) return err(400, "code 不能为空");
      if (!name) return err(400, "name 不能为空");
      if (mockDomains.some(d => d.code === code && !d.deleted)) {
        return err(409, `业务域编码已存在: ${code}`);
      }
      const newDomain: BizDomainResp = {
        id: _nextId++,
        tenantId: 1,
        code,
        name,
        description: description ?? null,
        createdAt: "2026-07-01 00:00:00",
        global: false
      };
      mockDomains.push(newDomain);
      return ok(clone(newDomain));
    }
  },
  // 更新：按 domainId 查存在则 update name/description（🔧 后端用内部主键，应切业务键 code，登记 T-PERM-026）
  {
    url: "/api/perm/biz-domain/update",
    method: "post",
    response: ({ body }) => {
      const { domainId, name, description } = body || {};
      const found = mockDomains.find(d => d.id === domainId && !d.deleted);
      if (!found) return err(404, "业务域不存在");
      if (name != null) found.name = name;
      if (description != null) found.description = description;
      return ok(clone(found));
    }
  },
  // 删除：批量软删（设 deleted=true）。全局域不可删（后端需校验 global=true 拒绝；
  // 🔧 Resp 不返回 global 前端无法预判，登记 T-PERM-026）。
  {
    url: "/api/perm/biz-domain/remove",
    method: "post",
    response: ({ body }) => {
      const { ids } = body || {};
      if (!Array.isArray(ids) || ids.length === 0) return ok(null);
      const toDelete = mockDomains.filter(
        d => ids.includes(d.id) && !d.deleted
      );
      // 全局域不可删
      const globalOnes = toDelete.filter(d => d.global);
      if (globalOnes.length > 0) {
        return err(400, "全局域不可删除");
      }
      toDelete.forEach(d => {
        d.deleted = true;
      });
      return ok(null);
    }
  }
]);
