---
doc_type: task
id: T-PERM-027
title: 5.2 服务+接口映射后端——删除级联/资源业务字段/FULL-only/映射门禁收口（service-config + resource-api-mapping）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.4
  - docs/design/permission-center/api-contract.md#§6.3
  - docs/design/permission-center/api-contract.md#§6.10.4
  - docs/design/frontend/service-interface-mapping.md#§7
depends_on:
  - T-FE-007
blocks: []
acceptance:
  - "服务删除级联清理：deleteServiceConfigsByIds 同事务软删该服务全部 resource_api_mapping（含 MANUAL）+ 该服务 SERVICE_SYNC 自动维护的孤立 API 资源（复用 ResourceSyncHandler 新增 cleanupServiceOwnedResources，FULL diff 同清理边界；被其他服务跨服务手工映射引用的资源保留）+ markServiceCodes 广播 Gateway 快照失效——§7 第 2 项（Controller 注释漂移一并收口）"
  - "ApiMappingResp 补关联资源业务字段 resourceCode/resourceName/resourceTypeCode/maintainSource（批量补全防 N+1；资源已软删为 null）——§7 第 3 项"
  - "listApiMappings 补 SERVICE:VIEW 门禁：带 serviceCode 按实例校验、不带按类型级校验 + getDeniedResourceCodes 服务维结果裁剪；service-config/apis 委托同一实现（门禁与补全单点）——§7 第 5 项"
  - "syncMode FULL-only：ServiceConfigSyncReq @Pattern(\"FULL\") 校验层拒绝其他值（HTTP 400，body code=90001 参数校验失败），删除零调用的 IncrementalSyncStrategy——§7 第 4 项"
  - "ServiceConfigResp 补 updatedAt（列已有）；lastSyncedAt 登记不做（无现成列、聚合推导语义模糊）——§7 第 7 项"
  - "list 维持 {} 全量返回收口为设计定案（服务数量有界/目录面板无分页 UI/本地过滤已可用）——§7 第 1 项"
  - "手工映射内部 resourceId 绑定维持，资源树/业务键稳定定位与前端资源选择器登记 T-PERM-028——§7 第 6 项"
  - "SERVICE:VIEW/MANAGE/SYNC_INTERFACE 补入空库 bootstrap 固定图（无授予起点死锁防护，DOMAIN:VIEW 先例；MANAGE_API_MAPPING 原已持有）"
  - "前端与 mock 对齐：ApiMappingResp/ServiceConfigResp 类型扩展、映射表资源列展示业务编码（回退 #id）、服务信息条「更新于」、mock 透出资源字段与 updatedAt"
  - "design_writeback：api-contract §5.4 契约要点 + §6.3/§6.10.4 口径句、service-interface-mapping.md §4/§7 终态化、看板行 ✅"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-29
---

# T-PERM-027 5.2 服务+接口映射后端——删除级联/资源业务字段/FULL-only/映射门禁收口

> 状态：done（2026-08-29 收口）
> 依赖：T-FE-007（前端服务+接口映射页 + 🔧 清单登记）
> 归属：frontend-phase2（5.2 服务+接口映射后端）

## 背景

T-FE-007 前端服务+接口映射页（左服务目录 + 右接口映射）在 API 核对中登记 7 项 🔧（service-interface-mapping.md §7）：list 无分页、删除未级联、apis 缺资源业务字段、syncMode DTO 漂移、映射列表无门禁、手工映射内部 ID、同步状态不可追溯。

## 设计定案（2026-08-29）

1. **删除级联清理**：同事务软删该服务全部映射（含 MANUAL——服务已删则其路由不再存在，映射即死路径）+ SERVICE_SYNC 孤立资源（FULL diff 同边界，跨服务手工映射引用的资源保留）+ Gateway 快照失效广播；对齐 Phase 1 mock 语义与 Controller 注释承诺。
2. **syncMode FULL-only**：实现向权威契约 §6.3 对齐（校验层 @Pattern 拒绝其他值 + 删除零调用的增量策略），不反向扩契约。
3. **list 维持全量**：服务登记数量有界（租户内微服务个数），目录面板无分页 UI、本地过滤已可用，补无人消费的分页参数属死契约面（过度设计）。
4. **同步元数据只补 updatedAt**：lastSyncedAt 无现成列，按映射 MAX(updated_at) 聚合推导语义模糊（行更新≠最近一次成功同步），登记不做。

## 范围与实现

- 级联删除：ResourceApiMappingMapper 新增 selectValidByServiceCodes（IN 批量，防按服务循环查询）；ResourceSyncHandler 新增 cleanupServiceOwnedResources（批量版孤立资源清理，与 cleanupOrphanedResources 同边界）；deleteServiceConfigsByIds 补 @PermissionChange + markServiceCodes（对齐 removeApiMappingsByIds 范式）。
- 资源业务字段：ResourceManageAppServiceImpl toApiMappingResp 扩 4 字段 + toEnrichedApiMappingResps 批量补全（selectValidByIds 一次查询）；addApiMapping 复用已加载实体、updateApiMapping 回读资源。
- 映射门禁：listApiMappings 实例/类型级 SERVICE:VIEW + 结果裁剪；listServiceApis 委托 listApiMappings（同层复用，原内联构造删除）。
- bootstrap：businessGrants + SERVICE VIEW/MANAGE/SYNC_INTERFACE 三条类型级（AccessBootstrapPgIT 计数 24→27 / scopeAll 11→14）。
- 前端：api/service-interface.ts 类型扩展；hook 过滤匹配 resourceCode；index.vue 资源列（tooltip 含名称与 #id）+ 信息条「更新于」；mock 响应透出资源字段与 updatedAt（InternalMapping 仅内部持有 source 同义标记）。

## 验收对照

- design_refs：api-contract §5.4 契约要点 + §6.3 FULL-only 执行口径 + §6.10.4 字段口径已回写；service-interface-mapping.md §4 核对表 ✅ 化、§7 七项划线收口。
- 测试：ServiceConfigAppServiceImplTest 8→11（级联批量软删断言、apiType 缺失跳过资源清理、apis 委托、updatedAt 透出）；ResourceManageAppServiceImplTest 2→7（实例/类型级门禁、服务维裁剪、资源字段补全、资源已删置 null）；ServiceSyncAppServiceImplTest 2→3（@Pattern 校验层锁定 FULL 通过/INCREMENTAL、PARTIAL、full、空串拒绝）；AccessBootstrapPgIT 计数 27/14；新增 ServiceConfigCascadePgIT（真库：级联清理边界——A 服务映射全删/B 服务与跨服务引用保留/SERVICE_SYNC 孤立资源删除 + 映射响应资源业务字段含 resolveTypeCode 真实解析 + updateApiMapping 首查参数序回归锁）。
- 回归：access-service mvn test 全绿；前端 typecheck 干净 + vitest 216 项全绿 + 变更文件 eslint 干净。

## 已知限制

- **删除级联并发窗口**：服务删除事务进行中，并发 FULL sync/手工加映射可在级联查询之后插入、留下引用已软删服务的残留有效行（惰性死路径，无安全影响；删除提交后的 sync 正确 404、服务已删无法再 sync 自愈）。窗口窄、失败模式惰性，不加锁（双轨评审存疑项，2026-08-29 用户决策按本卡轻量登记）。
- `SERVICE:MANAGE`（删除服务）级联删映射与 `SERVICE:MANAGE_API_MAPPING`（直接删映射）的不对称为设计口径：删服务是严格更强的破坏性操作，门禁按操作破坏性上界分配；api-contract §5.4 已记载。

## 完成记录

- 2026-08-29 收口：七项 🔧 全处置（第 1/3/4/5/7 项实现收口、第 2 项级联清理落地、第 6 项登记 T-PERM-028）+ 四项设计定案（级联边界、FULL-only、list 维持全量、只补 updatedAt）+ bootstrap SERVICE 三授权；§7.6 资源选择器联动登记随 T-PERM-028 资源树后端一并落地。
- 2026-08-29 双轨评审收口（代码轨+文档轨）：P1 updateApiMapping 首查换参修复（既有缺陷，真库必 RESOURCE_NOT_FOUND，PgIT 锁）+ @Transactional(readOnly)/apiType 缺失 warn/空白 serviceCode 规整/两处「支持增量同步」Javadoc 残留/mock updatedAt 条件刷新；project-rules §3.3 异常表按代码对齐（用户决策）、access-service-architecture §14.4 补 DOMAIN:VIEW 与 SERVICE 三行+权威指针注（用户决策）、「资源树」措辞×3 收口为映射列表、错误通道措辞订正（HTTP 400 + body 90001）、测试计数订正（8→11/+11）；拒绝项：resolveTypeCode 批量化（toResourceResp 既有同款缓存范式）、@PermissionChange 反射存在性断言与事务原子性 PgIT 锁（全仓无先例，新机制属过度设计）。
