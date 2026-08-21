// 域配置 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 DomainConfigResp
// 契约依据：docs/design/permission-center/api-contract.md §5.6（高级能力）
// 表结构：docs/design/schema/permission-center.sql:465-483
import { defineFakeRoute } from "vite-plugin-fake-server/client";
import { resolveDomainId } from "./_bizDomainRegistry";

/**
 * 本地声明类型（不 import src/api/domain-config，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 system-config.ts mock 零 src 依赖范式一致。
 * 字段定义同步注释于下方，保持与 api 层对齐。
 */

/** 域配置响应（对齐 src/api/domain-config.ts 的 DomainConfigResp / 后端 DomainConfigResp）。
 *  extra 为 JSON 字符串（schema 是 JSONB，entity 映射为 String；🔧 JSONB↔String 映射确认登记 T-PERM-026）。
 *  deleted 为 mock 内部软删标记（对齐 schema delete_flag），不出现在真实响应中。
 *  domainCode 为 mock 内部冗余字段（便于按域过滤，后端 Resp 不返回——后端按 bizDomainId 关联）。 */
type DomainConfigResp = {
  id: number;
  tenantId?: number;
  bizDomainId: number;
  configType: string;
  extra: string;
  updatedAt?: string;
  /** mock 内部：软删标记 */
  deleted?: boolean;
  /** mock 内部：所属域编码（便于按域过滤，后端 Resp 不返回） */
  domainCode?: string;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

// ========== 业务域编码 → ID 解析（共享 registry） ==========
// 后端 save 时由 typeResolutionService.resolveDomainId(tenantId, domainCode) 解析 bizDomainId；
// mock 调用共享注册表 mock/_bizDomainRegistry.ts 的 resolveDomainId，实时感知运行时新建/删除的业务域
//（修复：原静态 DOMAIN_CODE_TO_ID 反查表不含新建域 code，导致新建域保存配置返回「未知域编码」）。

// ========== Mock 数据：域配置（覆盖 5 种 configType + 多域） ==========

/**
 * domain_config 内存数据。
 *
 * schema 注释（permission-center.sql:482-483）：
 * - config_type 取值：SCOPE / RELATION / BINDING / SUB_PERM / CLASSIFY
 * - 每个域独立，无继承
 *
 * 🔧 后端 AppServiceImpl.upsertDomainConfig 注释只提 CLASSIFY/SUB_PERM，schema 注释列 5 种，
 * 登记 T-PERM-026。mock 覆盖全 5 种以验证前端下拉展示与筛选。
 */
const mockConfigs: DomainConfigResp[] = [
  {
    id: 1,
    tenantId: 1,
    bizDomainId: 2,
    domainCode: "HR",
    configType: "CLASSIFY",
    extra: '{"typeCodes":["USER","ORG","POSITION"]}',
    updatedAt: "2026-01-02 10:00:00"
  },
  {
    id: 2,
    tenantId: 1,
    bizDomainId: 2,
    domainCode: "HR",
    configType: "SCOPE",
    extra: '{"mode":"DOMAIN_ONLY"}',
    updatedAt: "2026-01-02 11:00:00"
  },
  {
    id: 3,
    tenantId: 1,
    bizDomainId: 3,
    domainCode: "ORDER",
    configType: "CLASSIFY",
    extra: '{"typeCodes":["ORDER","ORDER_ITEM"]}',
    updatedAt: "2026-01-03 10:00:00"
  },
  {
    id: 4,
    tenantId: 1,
    bizDomainId: 3,
    domainCode: "ORDER",
    configType: "RELATION",
    extra: '{"parent":"ORDER","child":"ORDER_ITEM"}',
    updatedAt: "2026-01-03 11:00:00"
  },
  {
    id: 5,
    tenantId: 1,
    bizDomainId: 4,
    domainCode: "CRM",
    configType: "BINDING",
    extra: '{"resourceType":"CUSTOMER","bindTo":"USER"}',
    updatedAt: "2026-01-04 10:00:00"
  },
  {
    id: 6,
    tenantId: 1,
    bizDomainId: 4,
    domainCode: "CRM",
    configType: "SUB_PERM",
    extra: '{"parent":"CUSTOMER:VIEW","children":["CUSTOMER:EXPORT"]}',
    updatedAt: "2026-01-04 11:00:00"
  },
  {
    id: 7,
    tenantId: 1,
    bizDomainId: 5,
    domainCode: "ASSET",
    configType: "SCOPE",
    extra: '{"mode":"GLOBAL_PLUS"}',
    updatedAt: "2026-01-05 10:00:00"
  }
];

/** 自增 id（已用最大 id + 1） */
let _nextId = mockConfigs.reduce((max, c) => Math.max(max, c.id), 0) + 1;

/** 深拷贝并剔除 mock 内部字段（deleted/domainCode），对齐后端 Resp 现状（不返回 domainCode）。 */
function clone(c: DomainConfigResp): DomainConfigResp {
  const { deleted: _del, domainCode: _dc, ...rest } = c;
  return { ...rest };
}

export default defineFakeRoute([
  // 列表：按 body.domainCode 过滤（不传则全量），返回未软删 ItemsResp（无分页）。
  {
    url: "/api/perm/domain-config/list",
    method: "post",
    response: ({ body }) => {
      const { domainCode } = body || {};
      let list = mockConfigs.filter(c => !c.deleted);
      if (domainCode) {
        list = list.filter(c => c.domainCode === domainCode);
      }
      const items = list
        .sort((a, b) => (a.configType ?? "").localeCompare(b.configType ?? ""))
        .map(clone);
      return ok({ items });
    }
  },
  // 详情：按 domainCode + configType 业务键二元组查（对齐后端 DomainConfigGetReq）
  {
    url: "/api/perm/domain-config/detail",
    method: "post",
    response: ({ body }) => {
      const { domainCode, configType } = body || {};
      const found = mockConfigs.find(
        c =>
          c.domainCode === domainCode &&
          c.configType === configType &&
          !c.deleted
      );
      if (!found) return err(404, "域配置不存在");
      return ok(clone(found));
    }
  },
  // 保存：upsert 幂等。校验 extra 合法 JSON + domainCode 解析为 bizDomainId +
  // 按 bizDomainId+configType 查存在则 update extra，不存在则 insert。
  {
    url: "/api/perm/domain-config/save",
    method: "post",
    response: ({ body }) => {
      const { domainCode, configType, extra } = body || {};
      if (!domainCode) return err(400, "domainCode 不能为空");
      if (!configType) return err(400, "configType 不能为空");
      if (!extra) return err(400, "extra 不能为空");
      // 校验 extra 为合法 JSON（后端 JsonValidationUtils.validateJson 等价）
      try {
        JSON.parse(extra);
      } catch {
        return err(400, "配置值必须是合法 JSON");
      }
      const bizDomainId = resolveDomainId(domainCode);
      if (bizDomainId == null) {
        return err(404, `未知域编码: ${domainCode}`);
      }
      const found = mockConfigs.find(
        c =>
          c.bizDomainId === bizDomainId &&
          c.configType === configType &&
          !c.deleted
      );
      if (found) {
        // upsert：存在则 update
        found.extra = extra;
        found.updatedAt = "2026-07-01 00:00:00";
        return ok(clone(found));
      }
      // upsert：不存在则 insert
      const newConfig: DomainConfigResp = {
        id: _nextId++,
        tenantId: 1,
        bizDomainId,
        domainCode,
        configType,
        extra,
        updatedAt: "2026-07-01 00:00:00"
      };
      mockConfigs.push(newConfig);
      return ok(clone(newConfig));
    }
  },
  // 删除：批量软删
  {
    url: "/api/perm/domain-config/remove",
    method: "post",
    response: ({ body }) => {
      const { ids } = body || {};
      if (!Array.isArray(ids) || ids.length === 0) return ok(null);
      mockConfigs.forEach(c => {
        if (ids.includes(c.id)) c.deleted = true;
      });
      return ok(null);
    }
  }
]);
