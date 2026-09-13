---
doc_type: task
id: T-ACCESS-036
title: resource_entity.sort_order 全字段面退役
status: done
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§5.2
  - docs/design/access-service-api-contract.md#§12.1（resource-entity 契约；T-ACCESS-040 重挂总册）
  - docs/design/schema/access-service.sql
  - perm-sdk/perm-common（ResourceCreateReq/ResourceUpdateReq/ResourceResp/ResourceEntitySyncReq 单源双侧）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-040
blocks: []
acceptance:
  - "resource 面 sortOrder 全量退役（以全仓 rg sortOrder 清单核对）：resource_entity.sort_order 列与实体字段、SDK perm-common 册 ResourceCreateReq.sortOrder / ResourceUpdateReq.sortOrder / ResourceResp.sortOrder **与 access-service 服务端册 resource/dto/resp/ResourceResp.sortOrder（T-ACCESS-033 迁移后包位）**（HTTP 线格式独立于 SDK——仅改 SDK 则响应仍回 sortOrder）、ResourceEntitySyncReq 与 ResourceEntitySyncItem（full-sync item）的 sortOrder、ResourceTreeResp.ResourceTreeNode.sortOrder、全部写入点（管理面 create/batch/update 与资源同步通道）、前端 resource-operation.ts 类型、hook.ts 创建/编辑提交载荷与 ResourceForm.vue 表单展示/必填链路"
  - "role/menu/org/type_definition 的 sortOrder 不在范围（功能角色排序、菜单展示排序、组织投影源、类型定义排序均在用；hook.ts:63 的树排序消费的是 type_definition.sortOrder），验收以范围区分为准"
  - "负向锁与既有严格 mapper 行为锁对齐（T-PERM-053 先例）：旧载荷（仍含 sortOrder）→ 400 且不触达业务层（全局唯一 ObjectMapper 为 cacheObjectMapper 裸实例、FAIL_ON_UNKNOWN_PROPERTIES 保持 Jackson 默认开启，未知字段走 HttpMessageNotReadableException → 90001 信封）；不放宽 mapper 配置，ServiceConfigSyncOperationCodeRetiredTest 同款锁风格新增 sortOrder 用例"
  - "前后端与 SDK 同批锁步（硬断裂，空库无存量调用方）：前端停发与表单链路清理、SDK 双册删字段与 access-service 同一提交批"
  - "PermCommonReqContractTest 注解签名快照更新；契约（新册）回写（明确 400 行为）；全量回归绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-13
---

## 背景

resource_entity.sort_order 全仓零读取方（无任何按该列排序的消费；资源管理页创建/编辑当前每次提交 sortOrder、sync 通道可写该字段，但均为死数据写入）。因涉 SDK 单源 DTO、同步通道载荷与前端提交面，从字段消减批次单列。

## 范围

resource 面 sortOrder 的列、实体、SDK 双册字段（含 ResourceEntitySyncReq/full-sync item）、树响应字段、全部写入点、前端类型与提交/表单展示链路一次性退役（前端同批清理属字段清理，非信息架构变更——设计 §6 的「前端 IA 维持」不冲突）。

## 当前口径

- 未知字段实际通道为严格 mapper 400（HttpMessageNotReadableException → 90001 信封），与 T-PERM-053 的 operationCode 退役行为锁同一机制——负向锁钉该行为，不放宽 mapper。
- 空库窗口内做对外契约变更成本最低（无存量调用方）；前后端 + SDK 同批锁步发布。
- depends_on 含 040：契约回写落新册。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不动 abstract_role.sort_order（功能角色在用）、sys_menu.sort_order（菜单展示在用）、sys_org.sort_order（组织投影源）、type_definition.sort_order（类型定义在用）。

## 完成记录（2026-09-13）

**后端（12 文件）**：ResourceEntity 实体删 sortOrder 字段；服务端 ResourceResp / ResourceTreeResp.ResourceTreeNode 删组件；ResourceEntitySyncReq / ResourceEntitySyncItem（full-sync item）删组件；perm-common SDK 册 ResourceCreateReq / ResourceUpdateReq / ResourceResp 删组件（starter 仅泛型声明零字段访问，e2e/example/gateway 零构造点）。写入点五面全清：ResourceManageAppServiceImpl（create / batchCreate / update 含 extraClear 分支 patch / 树节点构造 / toResourceResp）、ResourceEntitySyncAppServiceImpl（full-sync item 转 oneReq / insert / update 三处）、ResourceSyncHandlerImpl（service-config/sync 通道 API 资源落库的写死 setSortOrder(0)——盘点阶段 grep 输出截断漏网、编译期抓获后清扫，写入点完备性以 `rg "setSortOrder|getSortOrder"` 按实体归类终扫为零为准）、upsertAdminMenu 接口+impl 删零消费 sortOrder 参数（T-ACCESS-035 登记的存量观察兑现，MenuWriteAppServiceImpl / AccessBootstrapInitializer 两调用点同步收窄；sys_menu.sort_order 本体未动）。ResourceManageAppService javadoc 同步。

**前端（5 文件 + mock）**：api/resource-operation.ts 四类型（ResourceResp / ResourceTreeNode / Create / Update Req）、utils/types.ts 表单类型与空值工厂、utils/hook.ts 创建/编辑提交载荷、ResourceForm.vue 表单项/必填规则/默认值/initFormData 全链路；mock/_shared/resource-fixtures.ts 本地镜像类型与全部种子行（含 `i * 10` 循环种子）清扫——mock 对齐契约形态（退役清扫面含 mock 定规）。typecheck + vitest 全绿。

**schema 与契约**：access-service.sql resource_entity 删 `sort_order INT DEFAULT 0,`（ItInfra 容器测试直接执行该 DDL，全量回归即运行时证据）；契约总册 §12.1 update 示例删字段 + 新增「resource 面 sortOrder 字段退役」注记（退役面清单 + 400 机制 + 负向锁指向）、§19.1 sync 示例删字段 + 规则列表补 400 指引条、§19.2 full-sync item 示例删字段；frontend/resource-operation.md 字段表删行 + frontmatter last_reviewed 追加注记。superseded 旧册（permission-center/api-contract.md、services/admin-service-api-contract.md）不回写（T-ACCESS-040 定案）。

**回归锁**：新增 `ResourceSortOrderRetiredTest`（ServiceConfigSyncOperationCodeRetiredTest 同款形态：真实拦截器 + 上下文 mapper 的完整 MockMvc 请求链）——正向 1 例（update 新载荷反序列化成功、ArgumentCaptor 断言字段全量到达业务层）+ 负向 4 例（create / update / sync / full-sync item 四 DTO 面旧载荷含 sortOrder → 400 + 90001 信封 + verifyNoInteractions 不触达业务层）；负向载荷 @NotBlank 字段全提供（400 只能来自反序列化拒绝），mapper 配置零改动。PermCommonReqContractTest 注解签名快照与组件序快照同步（ResourceCreateReq 12 参 / ResourceUpdateReq 8 参）。

**验证**：定向 78 用例绿 → 单测轨道（-DskipTestcontainers）全绿 → 全量 `mvn test -T 1C`（含 E2E）BUILD SUCCESS 全模块 0 失败 0 错误；前端 typecheck + vitest 全绿。SDK 变更先 `mvn install -pl perm-sdk/perm-common` 刷新本地仓库。

## 评审处置（本地双轨，2026-09-13）

代码轨 P0-P2 零 + P3×1、文档轨 P0-P2 零 + P3×1，逐条核实后全处置：

- **代码轨 P3（采纳，已修）**：新测试内联注释「信封码断言把 400 归因钉死…防前置校验意外通过」措辞夸大——MethodArgumentNotValidException 与 HttpMessageNotReadableException 同返 400 + 90001（GlobalExceptionHandler:92-94/:143-148 实证），信封码不唯一钉通道。该句系从 T-PERM-053 先例文件逐字复刻（评审员称先例无此声明系只看类 Javadoc 的误判，行内注释 :152-153 原句仍在）；本文件注释已收敛为准确表述（负向载荷不触发 @Valid + 正向用例补证通道组合），先例文件属 T-PERM-053 范围未动——同句措辞夸大为两处同款锁测试共享的已知瑕疵，是否统一修正先例由用户拍板。
- **文档轨 P3（采纳，已修）**：frontend/resource-operation.md frontmatter `last_reviewed` 追加 T-ACCESS-036 注记（permission-condition.md 先例格式）。

两轨存疑待决策项均零；实证通过项：写入点完备性（setSortOrder/getSortOrder 按实体归类终扫 ResourceEntity 为零）、位置参数实参序（全部降参构造点逐一对位）、负向锁判别力（旧实现下四负向载荷反序列化通过 → 200 非 400+90001，锁与删除语义同向）、DDL/实体/mapper 映射闭合、SDK 四模块影响面、前端链路无悬空引用、残留终扫（resource 面活引用为零，范围外五面保留）。
