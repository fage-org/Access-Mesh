---
doc_type: task
id: T-PERM-060
title: 工程健康小批次——权限视图域映射批量化 + T-PERM-055 误读观察撤回 + EXT-7/8 处置登记
status: in-progress
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§2.7
depends_on: []
blocks: []
acceptance:
  - "背景①（T-PERM-055 claude 外评存量观察 1 处置）：PermViewAssembler.buildDomainCodeMap 逐 distinct typeCode 调 findDomainIdByTypeCode（单条形态每次 3 查询：selectNonGlobalByTenant + selectByTenantId + selectGlobalByTenant）；该路径为登录权限串唯一存续消费面（buildEffectiveView → assemble 无条件执行 buildDomainCodeMap），资源类型上限 12 → 每次登录权限串装配最多 36 条查询"
  - "修法：换既有批量方法 findDomainIdsByTypeCodes 一次调用（findDomainIdByTypeCode 本就是批量方法的单元素委托，语义等价可证；PermissionGrantPlanDomainServiceImpl 已有两处批量消费先例）"
  - "语义不变：resId→domainCode 输出与逐类型点查一致；回归锁 = verify never findDomainIdByTypeCode + times(1) findDomainIdsByTypeCodes（distinct 全集）+ 输出 domainCodeMap 断言，旧实现（逐条点查）下必失败"
  - "背景②（T-PERM-055 登记观察撤回）：「PermissionQueryAppServiceImplTest 单独运行 0 tests」为误读——surefire 对 @Nested 类分桶报告，外层容器行只统计外层自有用例（该类外层 0 个，7 个用例全在 @Nested 容器行报告）；XML 报告实证 7 用例全执行全绿（2026-09-11 实核 TEST-*.xml testcase 条目），无代码问题，T-PERM-055.md 非目标/遗留段同步撤回"
  - "背景③（EXT 处置登记）：EXT-7（batchCheck 逐条 engine.query）立项 T-PERM-061；EXT-8（enqueueAll 逐条 insert）失效撤注——宿主 SyncTaskDomainServiceImpl 已随内部同步子系统删除（T-ACCESS-005），2026-09-11 全仓实核零命中"
  - "低优先级工程健康批次（半天量级），无硬依赖"
design_writeback:
  required: false
  status: ""
  rationale: 消费面内部批量化——findDomainIdsByTypeCodes 契约与「批量上下文必须走批量形态」注记已随 T-PERM-055 落盘 implementation §2.7，本任务为该口径对最后遗留循环点的应用，无新增设计语义
last_updated: 2026-09-11
---

# T-PERM-060 工程健康小批次——权限视图域映射批量化 + T-PERM-055 误读观察撤回 + EXT-7/8 处置登记

> 状态：in-progress（2026-09-11 立项，用户选定空档方向「工程健康小批次」）
> 依赖：无硬依赖（来源：T-PERM-055 claude 外评存量观察 1 + 收口遗留观察）

## 背景

三个独立小项合并的工程健康批次：

1. **buildDomainCodeMap 逐类型点查**（T-PERM-055 claude 外评存量观察 1，当时定级低优先级不处置）：
   `PermViewAssembler.buildDomainCodeMap`（util 包，@Component）对 entries 涉及的每个 distinct
   resourceTypeCode 调一次 `domainClassifyService.findDomainIdByTypeCode`，每次调用内部跑
   3 条查询。`assemble` 无条件调用该方法（不依赖 domainCode 过滤器是否启用），而 assemble 的
   唯一存续消费面是登录权限串（`PermissionViewAppServiceImpl.buildEffectiveView` → forUserView
   管线）——每次登录权限串装配最多 12 类型 × 3 查询 = 36 条。
   T-PERM-055 已把批量形态 `findDomainIdsByTypeCodes` 定稿在接口与实现 §2.7（单条形态即批量方法的
   单元素委托），且 `PermissionGrantPlanDomainServiceImpl:595/:646` 已有批量消费先例——本项为
   该口径对最后遗留循环点的应用。
2. **T-PERM-055 误读观察撤回**：收口时登记「PermissionQueryAppServiceImplTest 单独运行
   0 tests、待另行排查」。2026-09-11 复核证伪：surefire 对 `@Nested` 内部类分桶报告，控制台
   `Tests run: 0 -- in OuterClass` 行只统计外层容器自有用例（该类外层恰好 0 个），7 个用例
   实际全部执行且全绿（surefire XML 报告 testcase 条目铁证：3 外层 + 2 InterfaceSnapshotTests +
   2 QueryResourcesTests 全在 `TEST-...PermissionQueryAppServiceImplTest.xml`）。观察撤回，
   无代码问题。
3. **EXT-7/EXT-8 处置**（审计 S-024 无主性能项，看板挂「待单独立项」）：
   - EXT-7：`PermissionCheckAppServiceImpl.batchCheck` 逐 item 循环 `engine.query`（:118）仍存在，
     立项 T-PERM-061（异构 item 分组批量化，引擎级设计，独立任务）。
   - EXT-8：`SyncTaskDomainServiceImpl.enqueueAll` 宿主已随内部同步子系统整体删除
     （T-ACCESS-005 同事务投影替代），2026-09-11 全仓 rg 实核零命中——失效撤注。

## 实施记录

（进行中）
