---
doc_type: task
id: T-ACCESS-145
title: 资源归属与引用实施契约定稿
status: done
plan: docs/archive/2026-10-22/resource-association-plan.md
domain: cross-service
design_refs:
  - docs/design/resource-association.md#contract-gate
  - docs/design/access-service-api-contract.md
  - docs/design/engine/implementation.md#permission-query
  - docs/design/frontend/permission-grant.md
  - docs/design/example-service-api-contract.md
  - docs/design/example-permission-demo.md
depends_on: []
acceptance:
  - "G1–G4 均有可执行的终态规则、具体正反场景及权威落点，无未决产品语义流入下游。"
  - "契约覆盖资源/关系维护分权、主资源来源资格、类型模式切换、树继承、ALL/DIRECT/DEPENDENT、解绑再绑及父 ALL 来源收集。"
  - "API 字段、错误分类、分页/批量与响应预算、增量/FULL 版本和清理边界、SDK 与消费者适配已登记；DDL 约束和锁序可证明，未声称 CHECK 能跨表核基数。"
  - "查询/保存/自动依赖/准入快照/管理与同步/删除清理/种子等入口逐项标适用或不适用；example 共享范围从 SYNC 改为 MANAGED 的接线明确。"
  - "本文件与契约、引擎、前端、example 设计完成目标口径回写，依赖图仍无环，后续卡验收与定稿一致。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-21
---

## 背景

确认专属与通用两类资源的维护分工后，需要把影响授权效果的剩余语义落成可实施契约，防止按展示过滤替代运行时约束。

## 范围

完成目标设计 G1–G4 的场景走查、接口/错误/预算/身份定义、DDL 约束方案、写入与读取覆盖表；写回所属规范，保持目标与已实现状态可辨。此卡是后续代码任务的共同准入。

## 当前口径

已确认的类型性质、来源分工、操作复用和原入口不重复决策；G1/G2 的 ALL、DIRECT、父来源及解绑复用取舍按真实场景交用户确认。技术细化按现有组件与工程规范解决，不另设审批流程。只有文档定稿，不执行后端、前端或数据库变更。

通用引用采用[整类引用、实例级授权](../../../design/resource-association.md#model)，原通用实例子集引用方案已取代；需要计算子权限时采用[完整父来源](../../../design/engine/implementation.md#association-parent-sources)。ALL、解绑再绑、实例/类型删除及已使用类型用途不可变规则已归[目标契约](../../../design/access-service-api-contract.md#resource-association-contract)。下游任务验收随目标修订，直接依赖不变。

详细规则以[资源关系设计](../../../design/resource-association.md)及上列 design_refs 为准。G3 的主资源删建代际采用不可复用来源身份核验，允许业务编码复用，字段发布须携带预期主来源身份；规则与协议已回写契约总册及 example 契约。实现中的新取舍仍须交用户决定。

**实施入口核对**（代码基线 `52eba3f0147bcca86a41c7320e6788f617c48e11`；下面记录定稿时现状与接线责任，不表示新能力已实现）：

| 入口 | 已核对现状 | 本计划接线责任 |
|---|---|---|
| `ResourceManageAppServiceImpl` 创建/批量创建/更新/移动/删除 | 写入口共持 RESOURCE_ENTITY 树锁；删除已有角色授权及服务快照影响收集 | 146 接入模式与生命周期守卫；148 补关系影响的授权/失效处理 |
| `ResourceEntitySyncAppServiceImpl.sync/fullSync` | 来源核验、树锁内类型所有权守卫，已有版本/代次及严格重放协议 | 146/147 将专属实例与归属置于同一 IAM 事务；通用引用不能借此改写 MANAGED 实例 |
| `ResourcePublicationDomainService` | 复用发布账本，调用方负责事务和资源树锁 | 147 核定关系发布的范围键、版本和 FULL 清理；不机械新增平行队列 |
| `TypeDefinitionAppServiceImpl` 创建/更新/删除 | 资源类型变更在树锁内校验；已有所有权变更及资源存在性守卫 | 146 补关系声明、被引用目标类型与删除守卫；已使用类型用途不可变 |
| `ServiceConfigAppServiceImpl.deleteServiceConfigsByIds` | 当前级联清理服务拥有的 API 资源与映射，并广播服务快照变化 | 146/148 核对新增关系生命周期，不能据既有 API 清理推断所有业务资源已被清理 |
| `TenantAppServiceImpl` / `TenantBaselineInitializer` | 当前为创建、改名、启停、管理员重置；开通事务初始化基线，失败回滚并清理门禁预留 | 146 核对初始化与租户隔离；现有租户入口未提供删除，不新增假想清理端点 |
| `PermissionGrantAppServiceImpl.applyGrantPlan` / `PermissionGrantPlanDomainServiceImpl.prevalidate` | 资源树锁先于预校验，现有单事务保存；SUB_PERM 解析已有唯一入口 | 148 在同一写边界校验专属归属/通用类型引用，复用 SUB_PERM；解绑保留配置不等于允许继续新增无效授权 |
| `AutoGrantMaterializationDomainServiceImpl` | 已有按角色及资源重算、授权回收领域入口 | 148 对照自动推导种子与物化写者，不只校验手工授权保存 |
| `QueryExecutionEngine` / `ScopeCoverageProjector` | 父来源现为 DECISION 短路；范围 ALL 不展开，投影不再鉴权 | 148 收集完整父来源并同源限制关系；专属范围按实际实例投影，通用 ALL 保持全范围 |
| 操作准入及快照 | 当前子候选只核父结构，运行时父判断延后最终检查 | 148 核对关系变更的失效与最终判定；准入不能冒充报表上下文检查 |
| bootstrap / SDK / DTO 对账 | 固定管理图与接口清单、SDK、TS 镜像均有现有载体 | 147/FE-080 在正式接口确定后同步字段、路由与客户端；不向既有普通类型默认开启关系模式 |
| example 元数据、发布与准备脚本 | `saveScope` 写本地目录并入发布队列；`publishNext` 在本地领取事务之外调用 IAM，再单独结束事件；脚本仍逐项调用 `demo-scope/save` | 149 改为 IAM 管理通用目录、业务只保留映射与整类引用；保留远端调用不入本地写事务的边界 |

**定稿落点**：API 字段、错误、预算与消费者边界见契约总册 §12.6；数据库约束方案与锁序见[服务架构](../../../design/access-service-architecture.md#resource-association-storage)；example 接线与退役范围见[业务契约](../../../design/example-service-api-contract.md#resource-association-publication)。本卡仅定稿；实际交付与联合证据见 [T-ACCESS-150](T-ACCESS-150.md)。

## 验收对照

- [x] G1–G4 均有可执行的终态规则、具体正反场景及权威落点，无未决产品语义流入下游。
- [x] 契约覆盖资源/关系维护分权、主资源来源资格、类型模式切换、树继承、ALL/DIRECT/DEPENDENT、解绑再绑及父 ALL 来源收集。
- [x] API 字段、错误分类、分页/批量与响应预算、增量/FULL 版本和清理边界、SDK 与消费者适配已登记；DDL 约束和锁序可证明，未声称 CHECK 能跨表核基数。
- [x] 查询/保存/自动依赖/准入快照/管理与同步/删除清理/种子等入口逐项标适用或不适用；example 共享范围从 SYNC 改为 MANAGED 的接线明确。
- [x] 本文件与契约、引擎、前端、example 设计完成目标口径回写，依赖图仍无环，后续卡验收与定稿一致。

验证层：仅文档场景、代码对照、链接/锚点和依赖检查；不以业务测试替代语义定稿。

## 非目标 / 遗留

不重新设计 IAM 产品，不实施业务代码或可执行 DDL；后续工作分别由同计划实现卡承接。

## 完成记录

2026-10-21：`git diff --check` 通过；PowerShell 对新增相对链接/显式锚点、计划任务依赖图、变更仅限 docs 的检查通过。`rg` 对本计划任务及目标设计扫描旧实例子集引用、未获执行授权和过程标记，无正向残留。业务测试未运行：本卡只交付契约，没有源码或可执行 DDL 变化。

本地代码轨：按上表对照实际写入口、父来源短路、同步重放及 example 事务边界，目标复用既有组件，无新增独立引擎/队列/缓存框架；业务正确性证据由实现卡承接，未发现定稿阻断项。文档轨：已核对最新用户决定与目标契约、下游验收、状态和链接一致；解绑采用精确清理，旧全部保留方案已取代。无待用户决策项；实施中出现新问题须重新上报，不能借本卡 done 豁免。
