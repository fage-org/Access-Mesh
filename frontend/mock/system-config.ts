// 系统配置 Mock（Phase 1）
// 经 vite-plugin-fake-server 拦截，统一返回后端 PermResult 信封：{ code, message, data }
// 字段已对齐 access-service 权限域的 SystemConfigResp
// 契约依据：docs/design/permission-center/api-contract.md §5.8（系统配置仅 3 行表格条目，无独立字段契约章节）
// 表结构：docs/design/schema/access-service.sql
import { defineFakeRoute } from "vite-plugin-fake-server/client";

/**
 * 本地声明类型（不 import src/api/system-config，避免 fake-server 经 bundle-import 打包 src/api 链——
 * 该链 import 了 @/utils/http 等浏览器侧依赖，在 node platform 下打包会失败，导致整个 mock 文件
 * 加载被静默吞掉 → 路由不注册 → 404）。与 type-def.ts mock 零 src 依赖范式一致。
 * 字段定义同步注释于下方，保持与 api 层对齐。
 */

/** 系统配置响应（对齐 src/api/system-config.ts 的 SystemConfigResp / 后端 SystemConfigResp）。
 *  deleted 为 mock 内部软删标记（对齐 schema delete_flag），不出现在真实响应中——
 *  clone 后列表/detail 路由已过滤 deleted 行。后端当前无删除接口，deleted 标记仅预留对齐范式。 */
type SystemConfigResp = {
  id: number;
  tenantId?: number;
  configKey: string;
  configValue: string;
  description: string | null;
  updatedAt?: string;
  createdAt?: string;
  deleted?: boolean;
};

/** 统一成功信封（对齐 common.model.PermResult.success） */
const ok = data => ({ code: 200, message: "success", data });

/** 错误信封 */
const err = (code: number, message: string) => ({ code, message, data: null });

// ========== Mock 数据：租户级配置项（扁平 key-value，schema 示例键） ==========

/**
 * system_config 内存数据（扁平数组）。
 *
 * schema 注释示例键（access-service.sql）：
 * - ROLE_NAME_UNIQUE_MODE：角色名唯一性策略（{"mode":"DOMAIN_UNIQUE"}）
 * - UNREGISTERED_API_POLICY：未注册接口默认策略（{"mode":"NO_RESTRICT"}）
 *
 * 另补充 PASSWORD_POLICY / SESSION_TIMEOUT 示例覆盖不同 JSON 结构，验证 configValue 展示与校验。
 * 系统配置无 is_system 字段（与 type_definition 不同），所有项均为租户级可编辑。
 */
const mockConfigs: SystemConfigResp[] = [
  {
    id: 1,
    tenantId: 1,
    configKey: "ROLE_NAME_UNIQUE_MODE",
    configValue: '{"mode":"DOMAIN_UNIQUE"}',
    description:
      "角色名唯一性策略：DOMAIN_UNIQUE=域内唯一 / GLOBAL=全局唯一 / NO_RESTRICT=不限制",
    updatedAt: "2026-01-01 00:00:00"
  },
  {
    id: 2,
    tenantId: 1,
    configKey: "UNREGISTERED_API_POLICY",
    configValue: '{"mode":"NO_RESTRICT"}',
    description:
      "未注册接口默认策略：NO_RESTRICT=放行 / DENY=拒绝 / STALE_ALLOW=兜底放行",
    updatedAt: "2026-01-01 00:00:00"
  },
  {
    id: 3,
    tenantId: 1,
    configKey: "PASSWORD_POLICY",
    configValue:
      '{"minLength":8,"requireDigit":true,"requireSymbol":false,"expireDays":90}',
    description: "密码策略：最小长度 / 是否要求数字 / 是否要求符号 / 过期天数",
    updatedAt: "2026-02-01 00:00:00"
  },
  {
    id: 4,
    tenantId: 1,
    configKey: "SESSION_TIMEOUT",
    configValue: '{"seconds":1800}',
    description: "会话超时秒数",
    updatedAt: "2026-02-01 00:00:00"
  }
];

/** 自增 id（已用最大 id + 1） */
let _nextId = mockConfigs.reduce((max, c) => Math.max(max, c.id), 0) + 1;

/** 深拷贝（返回给前端，避免外部修改内存） */
function clone(c: SystemConfigResp): SystemConfigResp {
  return { ...c };
}

export default defineFakeRoute([
  // 列表：对齐后端 ItemsResp（全量，无分页/无 keyword 过滤，登记 T-PERM-024 🔧）。
  // 过滤/分页由前端 hook 本地完成；此处仅排除已软删行。后端 list 接 EmptyReq 无参。
  {
    url: "/api/perm/system-config/list",
    method: "post",
    response: () => {
      const items = mockConfigs.filter(c => !c.deleted).map(clone);
      return ok({ items });
    }
  },
  // 详情：按 configKey 查（对齐后端 SystemConfigGetReq{configKey}，非按 id）
  {
    url: "/api/perm/system-config/detail",
    method: "post",
    response: ({ body }) => {
      const { configKey } = body || {};
      const found = mockConfigs.find(
        c => c.configKey === configKey && !c.deleted
      );
      if (!found) return err(404, "系统配置不存在");
      return ok(clone(found));
    }
  },
  // 保存：upsert 幂等（按 configKey 查存在则 update，不存在则 insert）。
  // configValue 用 JSON.parse 校验合法性（对齐后端 JsonValidationUtils.validateJson）。
  // 后端无 create/update/remove 独立接口，新建/编辑统一走 save。
  {
    url: "/api/perm/system-config/save",
    method: "post",
    response: ({ body }) => {
      const { configKey, configValue, description } = body || {};
      if (!configKey) return err(400, "configKey 不能为空");
      if (!configValue) return err(400, "configValue 不能为空");
      // 校验 configValue 为合法 JSON（后端 JsonValidationUtils.validateJson 等价）
      try {
        JSON.parse(configValue);
      } catch {
        return err(400, "配置值必须是合法 JSON");
      }
      const found = mockConfigs.find(
        c => c.configKey === configKey && !c.deleted
      );
      if (found) {
        // upsert：存在则 update
        found.configValue = configValue;
        found.description = description ?? null;
        found.updatedAt = "2026-07-01 00:00:00";
        return ok(clone(found));
      }
      // upsert：不存在则 insert
      const newConfig: SystemConfigResp = {
        id: _nextId++,
        tenantId: 1,
        configKey,
        configValue,
        description: description ?? null,
        updatedAt: "2026-07-01 00:00:00",
        createdAt: "2026-07-01 00:00:00"
      };
      mockConfigs.push(newConfig);
      return ok(clone(newConfig));
    }
  }
]);
