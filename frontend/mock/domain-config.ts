// 域配置 Mock
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 DomainConfigResp
// 契约依据：docs/design/permission-center/api-contract.md §5.6（高级能力，T-PERM-026 契约要点）
// 表结构：docs/design/schema/access-service.sql
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import { resolveDomainId } from "./_bizDomainRegistry";
import {
  listAllConfigs,
  listValidConfigsByDomainCode,
  findValidConfig,
  upsertConfig,
  softDeleteConfigs
} from "./_domainConfigRegistry";

/**
 * 本地声明类型（不 import src/api/domain-config，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 system-config.ts mock 零 src 依赖范式一致。
 *
 * 配置数据下沉至 mock/_domainConfigRegistry.ts 共享注册表（T-PERM-026 起）：
 * biz-domain mock 的 remove 需按域做「存在有效配置拒删」引用检查，
 * 必须与本文件的 save/remove 运行时数据一致（_bizDomainRegistry 同范式）。
 */

/** 域配置响应（对齐 src/api/domain-config.ts 的 DomainConfigResp / 后端 DomainConfigResp）。
 *  extra 为 JSON 字符串（schema 是 JSONB；JSONB↔String 映射已随 T-PERM-026 PgIT 确认语义等价：
 *  读出为 DB 规范化 JSON 文本，语义等价、可直接再提交）。 */
type DomainConfigResp = {
  id: number;
  tenantId?: number;
  bizDomainId: number;
  configType: string;
  extra: string;
  updatedAt?: string;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

/** 深拷贝并剔除 mock 内部字段（deleted/domainCode），对齐后端 Resp 现状（不返回 domainCode）。 */
function clone(c: {
  id: number;
  tenantId?: number;
  bizDomainId: number;
  configType: string;
  extra: string;
  updatedAt?: string;
  deleted?: boolean;
  domainCode?: string;
}): DomainConfigResp {
  const { deleted: _del, domainCode: _dc, ...rest } = c;
  return { ...rest };
}

// ========== 业务域编码 → ID 解析（共享 registry） ==========
// 后端 save 时由 typeResolutionService.resolveDomainId(tenantId, domainCode) 解析 bizDomainId；
// mock 调用共享注册表 mock/_bizDomainRegistry.ts 的 resolveDomainId，实时感知运行时新建/删除的业务域。

export default defineFakeRoute([
  // 列表：按 body.domainCode 过滤（不传则全量），返回未软删 ItemsResp（量小不分页，
  // 每域至多 SUB_PERM/CLASSIFY 两条，T-PERM-026 契约要点）。
  {
    url: "/api/perm/domain-config/list",
    method: "post",
    response: ({ body }) => {
      const { domainCode } = body || {};
      let list = listAllConfigs().filter(c => !c.deleted);
      if (domainCode) {
        list = listValidConfigsByDomainCode(domainCode);
      }
      const items = list
        .slice()
        .sort((a, b) => (a.configType ?? "").localeCompare(b.configType ?? ""))
        .map(clone);
      return ok({ items });
    }
  },
  // 详情：按 domainCode + configType 业务键二元组查（对齐后端 DomainConfigGetReq；
  // 未命中 data=null 不抛错，后端 getDomainConfig 同口径）
  {
    url: "/api/perm/domain-config/detail",
    method: "post",
    response: ({ body }) => {
      const { domainCode, configType } = body || {};
      const bizDomainId = resolveDomainId(domainCode);
      if (bizDomainId == null) return ok(null);
      const found = findValidConfig(bizDomainId, configType);
      return ok(found ? clone(found) : null);
    }
  },
  // 保存：upsert 幂等。校验 extra 合法 JSON（后端 JsonValidationUtils.validateJson 同款，
  // T-PERM-026 补齐）+ configType 白名单 + domainCode 解析为 bizDomainId +
  // 按 bizDomainId+configType 查存在则 update extra，不存在则 insert。
  {
    url: "/api/perm/domain-config/save",
    method: "post",
    response: ({ body }) => {
      const { domainCode, configType, extra } = body || {};
      if (!domainCode) return err(400, "domainCode 不能为空");
      if (!configType) return err(400, "configType 不能为空");
      // 对齐后端 DomainConfigReq @Pattern 白名单（2026-08-27）：仅接受已实现两类
      if (configType !== "SUB_PERM" && configType !== "CLASSIFY") {
        return err(
          400,
          "configType 仅支持 SUB_PERM/CLASSIFY（SCOPE/RELATION/BINDING 未实现）"
        );
      }
      if (!extra) return err(400, "extra 不能为空");
      // 校验 extra 为合法 JSON（后端 JsonValidationUtils.validateJson 等价，T-PERM-026 补齐）
      try {
        JSON.parse(extra);
      } catch {
        return err(400, "JSON格式不合法");
      }
      const bizDomainId = resolveDomainId(domainCode);
      if (bizDomainId == null) {
        return err(20017, `未知域编码: ${domainCode}`);
      }
      const saved = upsertConfig(bizDomainId, domainCode, configType, extra);
      return ok(clone(saved));
    }
  },
  // 删除：批量软删
  {
    url: "/api/perm/domain-config/remove",
    method: "post",
    response: ({ body }) => {
      const { ids } = body || {};
      if (!Array.isArray(ids) || ids.length === 0) return ok(null);
      softDeleteConfigs(ids);
      return ok(null);
    }
  }
]);
