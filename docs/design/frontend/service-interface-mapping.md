---
doc_type: design
title: 5.2 服务与接口映射页 前端设计
status: draft
domain: frontend
last_reviewed: 2026-07-11
---

# 5.2 服务与接口映射页 前端设计

> 任务：T-FE-007（Phase 1，mock 驱动）
> 后端契约：`docs/design/permission-center/api-contract.md` §5.4、§6.3、§6.10.4
> 对应后端改造：T-PERM-027（Phase 2）

## 1. 页面定位

本页面向租户的安全管理员，完成三件边界清晰的事：

1. **登记服务**：维护服务编码、名称、Gateway 基础路径、状态和说明。
2. **同步接口**：把某服务本次完整的接口清单以 `FULL` 模式提交给权限中心；同步只处理该服务自动维护的资源与映射。
3. **维护手工映射**：为已有资源实体补充、调整或移除精确的 HTTP 接口映射。

同步和手工映射不能混为一种操作：FULL 同步是“服务事实源覆盖”，手工映射是“管理员显式配置”。页面在同步弹窗中明确说明 FULL diff 的清理边界，主界面的映射表只承载手工维护入口。

## 2. 布局与视觉方向

页面采用“服务控制台”工作区，不使用普通的上下两张表：左侧是可切换的服务库存，右侧始终只聚焦一个服务的接口边界。

```
┌─ ROUTE CONTROL ─ 服务与接口映射 ──────────────── 登记服务 / 刷新 ┐
├────────────── 服务库存 ──────────────┬──────── 当前服务 ────────┤
│ 搜索（名称 / 编码 / 路径）             │ serviceCode + 状态 + 服务说明           │
│ ● 管理服务                              │ 基础路径 | 已登记接口 | 有效映射 | 停用 │
│ ● 权限中心（选中）                      │ FULL 同步边界提示                        │
│ ○ 示例服务                              ├─ API MAP ─ 同步接口 / 新增映射 ────────┤
│                                         │ 路径/方法/状态筛选 + 映射表              │
│ OR 语义说明                             │ HTTP 方法 | Gateway 路径 | 资源 ID ...   │
└─────────────────────────────────────────┴─────────────────────────────────────┘
```

- 色彩全部复用 Element Plus 语义令牌：主文本 `--el-text-color-primary`、信号 `--el-color-success`、工作面 `--el-fill-color-light`、边界 `--el-border-color`。页面不定义局部十六进制主题色，因此浅色/深色模式会随全局主题自动适配。
- 标志性元素是左侧服务卡片的“信号点 + 路由线”：启用服务采用 success 语义色，停用服务采用 info 语义色；右侧用同一信号色强调 Gateway 边界，而非另加装饰。
- 选中卡片、方法标签和接口表格是功能信息，不承载无关动画。保留 `:focus-visible`、窄屏纵向折叠和 `prefers-reduced-motion` 降级。

## 3. 交互与 mock 行为

### 3.1 服务目录

- 首次加载并行请求服务列表和全部映射列表：后者仅用于计算每张服务卡片的“有效 / 总接口数”，避免逐服务请求。
- 选中服务的明细统一调用 `service-config/apis`；按编码、名称、基础路径在前端快速过滤服务卡片。
- 新建、编辑走 `service-config/save` 幂等保存；编辑态锁定 `serviceCode`，因为它是服务稳定业务键。
- 删除先二次确认，再提交 `{ids:[serviceConfigId]}`；mock 同步移除该服务的映射，真实后端的级联行为需 Phase 2 修复（见 §7）。

### 3.2 FULL 接口同步

- 「同步接口」只在拥有 `SERVICE:SYNC_INTERFACE` 时展示。
- 弹窗固定 `syncMode: "FULL"`，基础路径可调整，分组 JSON 在前端校验 `groups[] → apis[]` 的必要字段。
- 每条 API 要求 `name/httpMethod/path/operationCode/resourceCode`，`path` 必须以 `/` 开头。
- mock 保留 `SERVICE_SYNC` 与 `MANUAL` 维护来源：FULL 同步只删除当前服务缺失的 `SERVICE_SYNC` 映射，绝不删除 `MANUAL` 映射；成功消息展示新增、更新、清理数量。

### 3.3 手工 API 映射

- 映射表显示 HTTP 方法、Gateway 路径、资源实体 ID、匹配顺序、状态和更新时间，支持路径 / 资源 ID、方法、状态三维过滤。
- 新增和编辑用独立表单：路径以 `/` 开头，顺序为非负整数，`extra` 若填写必须为合法 JSON。
- 当前后端以 `resourceId` 内部主键绑定映射，故 Phase 1 表单显式输入资源实体 ID；资源树/业务键选择器属于 T-PERM-027 与 T-PERM-028 的联动范围。
- 创建、编辑、移除仅在拥有 `SERVICE:MANAGE_API_MAPPING` 时展示；无写权限时表格保留只读状态，不发送写请求。

## 4. 数据与 API 依赖

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 服务列表 | `POST /api/perm/service-config/list` | `{}` | `ItemsResp<ServiceConfigResp>` | 🔧 见 §7.1 |
| 服务详情 | `POST /api/perm/service-config/detail` | `{serviceCode}` | `ServiceConfigResp` | ✅ |
| 服务保存 | `POST /api/perm/service-config/save` | `serviceCode/name/basePath/...` | `ServiceConfigResp` | ✅ |
| 服务删除 | `POST /api/perm/service-config/remove` | `{ids}` | `void` | 🔧 见 §7.2 |
| 服务接口清单 | `POST /api/perm/service-config/apis` | `{serviceCode}` | `ItemsResp<ApiMappingResp>` | 🔧 见 §7.3 |
| 完整同步 | `POST /api/perm/service-config/sync` | `ServiceConfigSyncReq(FULL)` | `ServiceConfigSyncResp` | 🔧 见 §7.4 |
| 映射列表（计数） | `POST /api/perm/resource-api-mapping/list` | `{resourceId?,serviceCode?}` | `ItemsResp<ApiMappingResp>` | 🔧 见 §7.5 |
| 新增映射 | `POST /api/perm/resource-api-mapping/create` | `resourceId/serviceCode/method/path/...` | `ApiMappingResp` | ✅（§6.10.4） |
| 更新映射 | `POST /api/perm/resource-api-mapping/update` | `resourceId/mappingId/...` | `ApiMappingResp` | ✅（§6.10.4） |
| 移除映射 | `POST /api/perm/resource-api-mapping/remove` | `{ids}` | `void` | ✅ |

所有接口由 `src/api/service-interface.ts` 解包统一 `PermResult<T>` 信封；请求体不传 `tenantId`。

## 5. 组件与权限接线

```
views/system/service-interface/
├── index.vue                    # 服务工作区、筛选、对话框与页面级降级
├── components/
│   ├── ServiceForm.vue           # 服务登记/编辑（save）
│   ├── SyncForm.vue              # 固定 FULL 的接口清单校验与提交
│   └── MappingForm.vue           # 手工映射 create/update
└── utils/
    ├── hook.ts                   # useServiceInterface（目录、明细、CRUD、同步）
    ├── perms.ts                  # SERVICE_INTERFACE_PERMS（SSOT）
    └── types.ts                  # 表单模型、HTTP 方法、JSON/同步清单校验
```

| 权限串 | 服务端门禁 | 前端控制 |
|---|---|---|
| `SERVICE:VIEW` | 服务 list/detail/apis | 页面加载；无权限时显示空态，不触发加载 |
| `SERVICE:MANAGE` | 服务 save/remove | 登记、编辑、删除服务 |
| `SERVICE:SYNC_INTERFACE` | `service-config/sync` | FULL 同步入口 |
| `SERVICE:MANAGE_API_MAPPING` | 映射 create/update/remove | 新增、编辑、移除映射 |

`router/modules/system.ts` 的 `meta.auths` 从 `SERVICE_INTERFACE_PERM_LIST` 派生；`mock/login.ts` 反向复用同一 SSOT：admin 与 sec 全权，hr 与 auditor 只读。

## 6. Step 1.5 组件识别

| 候选组件 | 使用场景 | 结论 |
|---|---|---|
| JSON 清单编辑器 | 本页 FULL 同步、后续资源/依赖批量同步 | ⏳ 暂不抽取；待 T-FE-011 确认第二个同形态页面后，由 T-FE-001 立项 |
| 资源实体选择器 | 本页手工映射、T-FE-008 资源管理、T-FE-014 授权 | ⏳ 需先完成 T-FE-008 的资源树契约，当前以 resource ID 输入降级 |
| 服务选择器 | 当前页固定左侧工作区 | ❌ 不抽取；与其他页面的服务选择形态尚未确认一致 |

## 7. API 核对清单（登记 T-PERM-027）

Phase 1 不修改后端；以下项目登记到 T-PERM-027。

1. **服务列表缺少分页与筛选**：`list` 接 `EmptyReq`，返回租户全量服务。当前前端本地筛选可用；应补 `keyword/status/pageNum/pageSize` 和分页响应。
2. **服务删除未级联处理映射**：`ServiceConfigAppServiceImpl.deleteServiceConfigsByIds` 当前只软删服务配置，未处理该服务 `resource_api_mapping`、自动维护资源及 Gateway 缓存失效；Controller 注释“同时处理映射关系”与实现不一致。应明确并实现安全的关联清理 / 停用策略与失效事件。
3. **`service-config/apis` 并非资源树**：当前返回扁平 `ApiMappingResp`，且缺 `resourceCode/resourceName/resourceTypeCode/maintainSource`。页面只能显示内部资源 ID；应补可展示的资源业务字段，或提供按分组/资源组织的树响应。
4. **同步模式设计与 DTO 漂移**：权威契约 §6.3 首期仅允许 `FULL`，但 `ServiceConfigSyncReq` 与策略实现仍可接受其他值。前端已强制 FULL；后端应在校验层只允许 FULL，或先更新权威契约。
5. **映射列表缺少权限校验**：`ResourceManageAppServiceImpl.listApiMappings` 未校验 `SERVICE:VIEW`，可能暴露跨服务 API 路径与资源 ID。应至少按请求 `serviceCode` 校验实例 VIEW；无 serviceCode 的管理列表应做类型级 VIEW 与结果裁剪。
6. **手工映射使用内部资源 ID**：create/update 使用 `resourceId/resourceEntityId`，缺资源业务键或资源选择器支撑。应与 T-PERM-028 一起补资源树/按 `resourceTypeCode + resourceCode + codeType` 的稳定定位能力，前端再替换当前 ID 输入框。
7. **同步状态不可追溯**：`ServiceConfigResp` 未提供 `updatedAt/lastSyncedAt`，页面不能展示真实的最近同步时间或来源。若运维需要排障，应补充服务更新与最近同步元数据。

## 8. 验收记录

- [x] 路由、服务库存、服务登记/编辑/删除、接口同步、手工映射和三维筛选均以 mock 数据可交互。
- [x] FULL 同步的清理边界在界面、表单校验和 mock 行为三处保持一致。
- [x] `hasPerms` 按 SERVICE 四类权限做按钮门控与只读降级。
- [x] API 封装、mock 响应与 `PermResult<T>` 信封一致；create/update 映射直接消费单条响应。
- [x] 已完成 `pnpm typecheck`。
