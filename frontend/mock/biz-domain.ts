// 业务域 Mock
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 BizDomainResp
// 契约依据：docs/design/permission-center/api-contract.md §5.1（类型与域，T-PERM-026 收口契约要点）
// 表结构：docs/design/schema/access-service.sql
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import {
  listAllDomains,
  findDomainByCode,
  registerDomain,
  updateDomain,
  softDeleteDomains,
  type BizDomainRecord
} from "./_bizDomainRegistry";
import { findValidConfigsByDomainIds } from "./_domainConfigRegistry";

/**
 * 本地声明类型（不 import src/api/biz-domain，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 system-config.ts mock 零 src 依赖范式一致。
 *
 * 业务域数据下沉至 mock/_bizDomainRegistry.ts 共享注册表；域配置数据在
 * mock/_domainConfigRegistry.ts——本文件 remove 的「域下存在配置拒删」引用检查
 * 需要两份运行时数据（T-PERM-026 删除保护对齐）。
 */

/** 业务域响应（对齐 src/api/biz-domain.ts 的 BizDomainResp / 后端 BizDomainResp）。
 *  T-PERM-026 起 Resp 返回 global（是否全局域，每租户仅一个，不可删）。
 *  deleted 为 mock 内部软删标记（对齐 schema delete_flag），不出现在真实响应中。 */
type BizDomainResp = {
  id: number;
  tenantId?: number;
  code: string;
  name: string;
  description?: string | null;
  global?: boolean | null;
  createdAt?: string;
};

/** 分页响应（对齐后端 PaginatedResp / src/api/role-manage.ts 同形） */
type PaginatedResp<T> = {
  items: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  hasNext: boolean;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

/** 深拷贝并剔除 mock 内部字段（deleted），对齐后端 Resp（T-PERM-026 起 Resp 含 global）。 */
function clone(d: BizDomainRecord): BizDomainResp {
  const { deleted: _del, ...rest } = d;
  return { ...rest, global: d.global === true };
}

export default defineFakeRoute([
  // 列表：服务端 keyword 过滤（code/name/description LIKE 大小写敏感）+ 分页
  // （ORDER BY code, T-PERM-026 收口；pageNum/pageSize 均不传 = 全量）。
  {
    url: "/api/perm/biz-domain/list",
    method: "post",
    response: ({ body }) => {
      const { keyword, pageNum, pageSize } = body || {};
      let list = listAllDomains()
        .filter(d => !d.deleted)
        .slice()
        .sort((a, b) => a.code.localeCompare(b.code));
      if (keyword) {
        list = list.filter(
          d =>
            d.code.includes(keyword) ||
            d.name.includes(keyword) ||
            (d.description ?? "").includes(keyword)
        );
      }
      const total = list.length;
      const paged =
        pageNum != null || pageSize != null
          ? { page: pageNum ?? 1, size: pageSize ?? 10 }
          : { page: 1, size: Math.max(total, 1) };
      const start = (paged.page - 1) * paged.size;
      const items = list.slice(start, start + paged.size).map(clone);
      return ok({
        items,
        total,
        pageNum: paged.page,
        pageSize: paged.size,
        hasNext: start + items.length < total
      } satisfies PaginatedResp<BizDomainResp>);
    }
  },
  // 详情：按业务键 code 查（T-PERM-026 切业务键）；未命中 data=null 不抛错（后端同口径）
  {
    url: "/api/perm/biz-domain/detail",
    method: "post",
    response: ({ body }) => {
      const { domainCode } = body || {};
      const found = domainCode ? findDomainByCode(domainCode) : undefined;
      return ok(found ? clone(found) : null);
    }
  },
  // 创建：校验 code 非空 + 租户内唯一（uk_biz_domain，重复 20052——T-PERM-026 与后端同码），
  // registerDomain 注册到共享表并自动分配 id。
  {
    url: "/api/perm/biz-domain/create",
    method: "post",
    response: ({ body }) => {
      const { code, name, description } = body || {};
      if (!code) return err(400, "code 不能为空");
      if (!name) return err(400, "name 不能为空");
      if (listAllDomains().some(d => d.code === code && !d.deleted)) {
        return err(20052, `业务域编码已存在（租户内唯一）: ${code}`);
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
  // 更新：按业务键 code 定位（T-PERM-026 切业务键）；name/description null=不更新、
  // description 空串=显式清空。未知编码 20017（后端 DOMAIN_NOT_FOUND 同码）。
  {
    url: "/api/perm/biz-domain/update",
    method: "post",
    response: ({ body }) => {
      const { domainCode, name, description } = body || {};
      const updated = updateDomain(domainCode, name, description);
      if (!updated) return err(20017, `业务域不存在: ${domainCode}`);
      return ok(clone(updated));
    }
  },
  // 删除：批量软删。删除保护（T-PERM-026，20051）：全局域不可删（registry global 校验）；
  // 域下仍存在有效域配置时整批拒绝（共享域配置注册表引用检查，需先删配置）。
  {
    url: "/api/perm/biz-domain/remove",
    method: "post",
    response: ({ body }) => {
      const { ids } = body || {};
      if (!Array.isArray(ids) || ids.length === 0) return ok(null);
      const targets = listAllDomains().filter(
        d => ids.includes(d.id) && !d.deleted
      );
      // 删除保护先于任何变更（整批失败则整批不变更，后端 @Transactional 等价）
      const globalCodes = targets.filter(d => d.global).map(d => d.code);
      if (globalCodes.length > 0) {
        return err(20051, `业务域不可删除：全局域（${globalCodes.join(",")}）`);
      }
      const referenced = findValidConfigsByDomainIds(targets.map(d => d.id));
      if (referenced.length > 0) {
        return err(
          20051,
          "业务域不可删除：域下存在域配置，请先删除其配置"
        );
      }
      softDeleteDomains(ids);
      return ok(null);
    }
  }
]);
