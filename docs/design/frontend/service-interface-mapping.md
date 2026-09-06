---
doc_type: design
title: 5.2 服务与接口映射页 前端设计
status: adopted
domain: frontend
last_reviewed: 2026-09-05   # 2026-09-05 T-PERM-053 锁步补正（§3.2 同步必填字段清单删 operationCode——接口权限无操作粒度，随契约 §6.3 字段退役）；2026-09-03   # 2026-09-03 T-FE-022 联调收口（mock 退役/api 切 Gateway /perm 前缀/浏览器冒烟全过）——Gateway +8 端点（service-config 5 + api-mapping 3，detail 不消费）；2026-08-29   # 2026-08-29 §7.6 资源选择器收口（T-PERM-028：类型下拉+树选落地、裸 ID 输入删除）；同日 T-PERM-027 后端收口终态化（删除级联/资源业务字段/FULL-only/mapping list 门禁/updatedAt/bootstrap SERVICE 授权；§7 七项收口标注）；原文 2026-07-11 Phase 1 前端设计定稿
---

# 5.2 服务与接口映射页 前端设计

> **T-FE-022 联调注记（2026-09-03）**：api/service-interface.ts 十端点切 Gateway（service-config 6 + api-mapping 4；页面消费 9——detail 与 create 的注册归依：detail 页面不消费不注册、create 先期在册） `/perm/api/perm/*`；mock/service-interface.ts 整删；浏览器全链路实证——登记/选中/新增映射/FULL 同步清理边界（服务维护映射清理、手工映射保留）/移除/删除服务。

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

页面采用左右布局（与用户管理页一致）：左侧为服务列表面板，右侧聚焦当前选中服务的接口映射，避免上下布局在内容稀疏时出现大片空白。

```
┌─ 服务 [n] ── 登记 ┐┌─ {serviceCode} · {name} · 状态 · 基础路径 ─ 编辑/删除 ┐
│ [搜索]            ││ ─ 接口映射 ─────────────── 同步接口 / 新增映射 ──────┤
│ ▎接入服务  ●     ││ 路径/资源 | 方法 | 状态 | 重置                       │
│   access-svc 3/5 ││ HTTP 方法 | Gateway 路径 | 资源 | 顺序 | 状态 | 操作│
│   example-svc 2/2││ ...                                                  │
│                  ││                                                      │
└──────────────────┘└──────────────────────────────────────────────────────┘
```

- 左侧面板用 `grid-template-columns: minmax(180px, 240px) 1fr` 约束宽度（对齐 CSS 设计系统 §2），标题栏承载「登记」入口与计数，搜索框实时过滤服务项，选中项以 primary 语义高亮，停用项降低不透明度。
- 右侧顶部信息条展示当前服务的编码、名称、状态与基础路径，并承载「编辑服务 / 删除服务」；下方 `PureTableBar` + `pure-table` 承载同步、新增映射入口与三维筛选；未选中服务时提示去左侧选择。
- 色彩全部复用 Element Plus 语义令牌：主文本 `--el-text-color-primary`、工作面 `--el-fill-color-light`、边界 `--el-border-color-lighter`，浅色/深色模式随全局主题自动适配。

## 3. 交互与 mock 行为

### 3.1 服务目录

- 首次加载并行请求服务列表和全部映射列表：后者仅用于计算每张服务卡片的“有效 / 总接口数”，避免逐服务请求。
- 选中服务的明细统一调用 `service-config/apis`；按编码、名称、基础路径在前端快速过滤服务卡片。
- 新建、编辑走 `service-config/save` 幂等保存；编辑态锁定 `serviceCode`，因为它是服务稳定业务键。
- 删除先二次确认，再提交 `{ids:[serviceConfigId]}`；后端级联清理已随 T-PERM-027 落地（见 §7.2）：同事务软删该服务全部映射与 SERVICE_SYNC 孤立资源，并广播 Gateway 快照失效。

### 3.2 FULL 接口同步

- 「同步接口」只在拥有 `SERVICE:SYNC_INTERFACE` 时展示。
- 弹窗固定 `syncMode: "FULL"`，基础路径可调整，分组 JSON 在前端校验 `groups[] → apis[]` 的必要字段。
- 每条 API 要求 `name/httpMethod/path/resourceCode`，`path` 必须以 `/` 开头（`operationCode` 已删除：接口权限无操作粒度，T-PERM-053，2026-09-05）。
- mock 保留 `SERVICE_SYNC` 与 `MANUAL` 维护来源：FULL 同步只删除当前服务缺失的 `SERVICE_SYNC` 映射，绝不删除 `MANUAL` 映射；成功消息展示新增、更新、清理数量。

### 3.3 手工 API 映射

- 映射表显示 HTTP 方法、Gateway 路径、资源业务编码（`resourceCode`，T-PERM-027 已补；资源已删时回退 `#资源实体ID`）、匹配顺序、状态和更新时间，支持路径/资源、方法、状态三维过滤（资源过滤同时匹配 resourceCode 与实体 ID）。
- 新增和编辑用独立表单：路径以 `/` 开头，顺序为非负整数，`extra` 若填写必须为合法 JSON。
- 映射 create/update 以 `resourceId` 内部主键提交（api-contract §5.4 定案不随业务键切换）；表单资源选取已升级为「类型下拉 + 资源树选择」（T-PERM-028 联动落地，选中取树节点内部 id，数据源 `resource-entity/tree`），裸数字输入形态已删除（见 §7.6）。
- 创建、编辑、移除仅在拥有 `SERVICE:MANAGE_API_MAPPING` 时展示；无写权限时表格保留只读状态，不发送写请求。

## 4. 数据与 API 依赖

| 操作 | 接口 | 请求 | 响应 | 核对 |
|---|---|---|---|---|
| 服务列表 | `POST /api/perm/service-config/list` | `{}` | `ItemsResp<ServiceConfigResp>` | ✅（T-PERM-027 收口：维持全量返回设计定案，见 §7.1） |
| 服务详情 | `POST /api/perm/service-config/detail` | `{serviceCode}` | `ServiceConfigResp` | ✅ |
| 服务保存 | `POST /api/perm/service-config/save` | `serviceCode/name/basePath/...` | `ServiceConfigResp` | ✅（Resp 含 updatedAt，§7.7） |
| 服务删除 | `POST /api/perm/service-config/remove` | `{ids}` | `void` | ✅（T-PERM-027 级联清理落地，§7.2） |
| 服务接口清单 | `POST /api/perm/service-config/apis` | `{serviceCode}` | `ItemsResp<ApiMappingResp>` | ✅（T-PERM-027 补资源业务字段，§7.3） |
| 完整同步 | `POST /api/perm/service-config/sync` | `ServiceConfigSyncReq(FULL)` | `ServiceConfigSyncResp` | ✅（T-PERM-027 后端 FULL-only 校验，§7.4） |
| 映射列表（计数） | `POST /api/perm/resource-api-mapping/list` | `{resourceId?,serviceCode?}` | `ItemsResp<ApiMappingResp>` | ✅（T-PERM-027 补 SERVICE:VIEW 门禁+服务维裁剪，§7.5） |
| 新增映射 | `POST /api/perm/resource-api-mapping/create` | `resourceId/serviceCode/method/path/...` | `ApiMappingResp` | ✅（§6.10.4；资源键 T-PERM-028 联动，§7.6） |
| 更新映射 | `POST /api/perm/resource-api-mapping/update` | `resourceId/mappingId/...` | `ApiMappingResp` | ✅（§6.10.4；同上） |
| 移除映射 | `POST /api/perm/resource-api-mapping/remove` | `{ids}` | `void` | ✅ |

所有接口由 `src/api/service-interface.ts` 解包统一 `R<T>` 信封；请求体不传 `tenantId`。

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

## 7. API 核对清单（T-PERM-027 已收口，2026-08-29）

Phase 1 不修改后端；以下项目登记到 T-PERM-027 并已随其收口（原文划线保留核对轨迹）。

1. ~~**服务列表缺少分页与筛选**~~（已收口为设计定案，2026-08-29）：`list` 维持 `{}` 全量返回——服务登记数量有界（租户内微服务个数），左栏目录面板无分页 UI、本地过滤已可用，补无人消费的分页参数属死契约面；契约口径见 api-contract §5.4。
2. ~~**服务删除未级联处理映射**~~（已收口，2026-08-29）：`deleteServiceConfigsByIds` 同事务级联软删该服务全部映射（含 MANUAL）+ 该服务 SERVICE_SYNC 自动维护的孤立 API 资源（FULL diff 同清理边界；被其他服务跨服务手工映射引用的资源保留），并 `markServiceCodes` 广播 Gateway 快照失效；Controller 注释与实现一致。ServiceConfigCascadePgIT 真库锁定。
3. ~~**`service-config/apis` 并非资源树**~~（已收口，2026-08-29）：维持扁平 `ApiMappingResp`（页面形态即扁平映射表，无树形诉求），但补齐 `resourceCode/resourceName/resourceTypeCode/maintainSource` 资源业务字段（批量补全，资源已删为 null，前端回退展示 `#实体ID`）；映射表「资源实体」列升级为「资源」列展示业务编码。
4. ~~**同步模式设计与 DTO 漂移**~~（已收口，2026-08-29）：`ServiceConfigSyncReq.syncMode` 校验层 `@Pattern("FULL")` 拒绝其他值（HTTP 400，body `code=90001` 参数校验失败），零调用的 `IncrementalSyncStrategy` 删除；后端与权威契约 §6.3、前端与 mock 三方一致。
5. ~~**映射列表缺少权限校验**~~（已收口，2026-08-29）：`listApiMappings` 补 SERVICE:VIEW 门禁——带 `serviceCode` 按该服务实例校验，不带按类型级校验并对结果做服务维裁剪（`getDeniedResourceCodes` 批量判权，拒绝服务的映射不外泄）；`service-config/apis` 委托同一实现（门禁与补全单点）。
6. ~~**手工映射使用内部资源 ID**~~ **已收口（T-PERM-028，2026-08-29 用户决策实现）**：create/update 仍以 `resourceId` 内部主键提交（api-contract §5.4 定案）；前端资源选择器已落地——MappingForm 裸「资源实体 ID」数字输入替换为「资源类型下拉 + el-tree-select 资源树选择」，选中取节点内部 id 提交，编辑态只读展示行内资源业务字段（`resourceCode·resourceName`）。
7. ~~**同步状态不可追溯**~~（已收口，2026-08-29）：`ServiceConfigResp` 补 `updatedAt`（列已有，保存与 FULL 同步回写 basePath 时刷新），服务信息条展示「更新于」；`lastSyncedAt` 不设——无现成列且按映射 MAX(updated_at) 聚合推导语义模糊（行更新≠最近一次成功同步），登记不做。

## 8. 验收记录

- [x] 路由、服务库存、服务登记/编辑/删除、接口同步、手工映射和三维筛选均以 mock 数据可交互。
- [x] FULL 同步的清理边界在界面、表单校验和 mock 行为三处保持一致。
- [x] `hasPerms` 按 SERVICE 四类权限做按钮门控与只读降级。
- [x] API 封装、mock 响应与 `R<T>` 信封一致；create/update 映射直接消费单条响应。
- [x] 布局采用左右两栏（左服务列表 + 右接口映射），对齐用户管理页范式与 CSS 设计系统，移除装饰性标题与工作区外壳，保持全站视觉一致。
- [x] 已完成 `pnpm typecheck`。
