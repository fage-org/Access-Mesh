// 业务域 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 permission-center 的 BizDomainResp
// 契约依据：docs/design/permission-center/api-contract.md §5.1（类型与域）
// 表结构：docs/design/schema/permission-center.sql:55-72
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import {
  listAllDomains,
  findDomainById,
  registerDomain,
  updateDomain,
  softDeleteDomains,
  type BizDomainRecord
} from "./_bizDomainRegistry";

/**
 * 本地声明类型（不 import src/api/biz-domain，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 system-config.ts mock 零 src 依赖范式一致。
 * 字段定义同步注释于下方，保持与 api 层对齐。
 *
 * 业务域数据下沉至 mock/_bizDomainRegistry.ts 共享注册表，使 domain-config mock 的
 * save（解析 domainCode → bizDomainId）能实时感知运行时新建/删除的业务域，
 * 不再依赖静态反查表（修复「新建业务域后无法新增域配置」）。
 */

/** 业务域响应（对齐 src/api/biz-domain.ts 的 BizDomainResp / 后端 BizDomainResp）。
 *  🔧 后端 Resp 不返回 global（entity/schema 有 global，每租户仅一个全局域），前端无法区分全局域，
 *  登记 T-PERM-026。mock 内部用 registry 的 global 字段做「全局域不可删」校验，
 *  但响应 clone 时剔除 global/deleted，对齐后端 Resp 现状。
 *  deleted 为 mock 内部软删标记（对齐 schema delete_flag），不出现在真实响应中。 */
type BizDomainResp = {
  id: number;
  tenantId?: number;
  code: string;
  name: string;
  description?: string | null;
  createdAt?: string;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

/** 深拷贝并剔除 mock 内部字段（global/deleted），对齐后端 Resp 现状（不返回 global）。 */
function clone(d: BizDomainRecord): BizDomainResp {
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
      const items = listAllDomains()
        .filter(d => !d.deleted)
        .slice()
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
      const found = findDomainById(id);
      if (!found || found.deleted) return err(404, "业务域不存在");
      return ok(clone(found));
    }
  },
  // 创建：校验 code 非空 + 租户内唯一（uk_biz_domain），registerDomain 注册到共享表并自动分配 id。
  {
    url: "/api/perm/biz-domain/create",
    method: "post",
    response: ({ body }) => {
      const { code, name, description } = body || {};
      if (!code) return err(400, "code 不能为空");
      if (!name) return err(400, "name 不能为空");
      if (listAllDomains().some(d => d.code === code && !d.deleted)) {
        return err(409, `业务域编码已存在: ${code}`);
      }
      const created = registerDomain(
        code,
        name,
        description ?? null,
        "2026-07-01 00:00:00"
      );
      return ok(clone(created));
    }
  },
  // 更新：按 domainId 查存在则 update name/description（🔧 后端用内部主键，应切业务键 code，登记 T-PERM-026）
  {
    url: "/api/perm/biz-domain/update",
    method: "post",
    response: ({ body }) => {
      const { domainId, name, description } = body || {};
      const updated = updateDomain(domainId, name, description);
      if (!updated) return err(404, "业务域不存在");
      return ok(clone(updated));
    }
  },
  // 删除：批量软删（设 deleted=true）。全局域不可删（registry 校验 global=true 拒绝；
  // 🔧 Resp 不返回 global 前端无法预判，登记 T-PERM-026）。
  {
    url: "/api/perm/biz-domain/remove",
    method: "post",
    response: ({ body }) => {
      const { ids } = body || {};
      if (!Array.isArray(ids) || ids.length === 0) return ok(null);
      const { blockedGlobals } = softDeleteDomains(ids);
      if (blockedGlobals.length > 0) {
        return err(400, "全局域不可删除");
      }
      return ok(null);
    }
  }
]);
