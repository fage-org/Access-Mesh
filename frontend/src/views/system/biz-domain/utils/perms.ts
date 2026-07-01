/**
 * 「业务域」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `BIZ_DOMAIN_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(BIZ_DOMAIN_PERMS.XXX)`
 *
 * ## 权限锚点（对齐后端 BizDomainAppServiceImpl / DomainConfigAppServiceImpl）
 *
 * 本页涉及**两个资源类型**的门禁（与 system-config 单一 SYSTEM_CONFIG 不同）：
 *
 * - `DOMAIN:VIEW`（资源类型 DOMAIN，独立）—— biz-domain **list/detail** 门禁。
 *   后端 `listBizDomains`/`getBizDomain` 以 `DOMAIN:VIEW` 校验（ResourceTypeCode.DOMAIN）。
 *   🔧 DOMAIN 权限种子缺失：schema 无 INSERT 为 DOMAIN 资源类型预置 VIEW 操作位，login 矩阵此前也无 DOMAIN 串，
 *   联调真后端时 biz-domain list/detail 可能全账号 403——本页新增 DOMAIN_VIEW，login 矩阵为所有账号预置 DOMAIN:VIEW
 *   （业务域基础设施各角色均可见列表），登记 T-PERM-026。
 *
 * - `SYSTEM_CONFIG:VIEW` / `SYSTEM_CONFIG:MANAGE`（资源类型 SYSTEM_CONFIG，复用）—— domain-config 子区 +
 *   biz-domain 写操作门禁：
 *   - biz-domain **create/update/remove** → `SYSTEM_CONFIG:MANAGE`（后端 createBizDomain/updateBizDomain/deleteBizDomainsByIds）
 *   - domain-config **list/detail** → `SYSTEM_CONFIG:VIEW`（后端 listDomainConfigs/getDomainConfig）
 *   - domain-config **save/remove** → `SYSTEM_CONFIG:MANAGE`（后端 upsertDomainConfig/deleteDomainConfigsByIds）
 *
 * 即：biz-domain 列表/详情看 DOMAIN:VIEW，其余写操作与域配置子区统一走 SYSTEM_CONFIG:VIEW/MANAGE。
 *
 * 详见 `docs/design/frontend/biz-domain.md` §权限接线。
 */
export const BIZ_DOMAIN_PERMS = {
  /** 查看业务域列表/详情（资源类型 DOMAIN，独立） */
  DOMAIN_VIEW: "DOMAIN:VIEW",
  /** 查看域配置列表/详情（复用 SYSTEM_CONFIG:VIEW） */
  CONFIG_VIEW: "SYSTEM_CONFIG:VIEW",
  /** biz-domain create/update/remove + domain-config save/remove（复用 SYSTEM_CONFIG:MANAGE） */
  CONFIG_SAVE: "SYSTEM_CONFIG:MANAGE"
} as const;

export type BizDomainPermKey = keyof typeof BIZ_DOMAIN_PERMS;
export type BizDomainPermValue = (typeof BIZ_DOMAIN_PERMS)[BizDomainPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 *
 * DOMAIN_VIEW / CONFIG_VIEW / CONFIG_SAVE 三值无重复，仍用 Set 去重保持与同范式（system-config）一致，
 * 确保 `meta.auths` 无冗余。
 */
export const BIZ_DOMAIN_PERM_LIST: ReadonlyArray<BizDomainPermValue> =
  Array.from(new Set(Object.values(BIZ_DOMAIN_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 * 含 DOMAIN_VIEW（biz-domain 列表可见）+ CONFIG_VIEW（domain-config 子表可见）。
 */
export const BIZ_DOMAIN_VIEW_PERMS: ReadonlyArray<BizDomainPermValue> = [
  BIZ_DOMAIN_PERMS.DOMAIN_VIEW,
  BIZ_DOMAIN_PERMS.CONFIG_VIEW
];
