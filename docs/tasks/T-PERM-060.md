---
doc_type: task
id: T-PERM-060
title: 工程健康小批次——权限视图域映射批量化 + T-PERM-055 误读观察撤回 + EXT-7/8 处置登记
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§2.7
depends_on: []
blocks: []
acceptance:
  - "背景①（T-PERM-055 claude 外评存量观察 1 处置）：PermViewAssembler.buildDomainCodeMap 逐 distinct typeCode 调 findDomainIdByTypeCode（单条形态每次 3 查询：selectNonGlobalByTenant + selectByTenantId + selectGlobalByTenant）；该路径为登录权限串唯一存续消费面（buildEffectiveView → assemble 无条件执行 buildDomainCodeMap），放大按 distinct 类型数 ×3 计（schema 预置 25 类 + type_definition 可自定义新增，无硬上限——最坏 75+ 条查询/次）"
  - "修法：换既有批量方法 findDomainIdsByTypeCodes 一次调用（findDomainIdByTypeCode 本就是批量方法的单元素委托，语义等价可证；PermissionGrantPlanDomainServiceImpl 已有两处批量消费先例）"
  - "语义不变：resId→domainCode 输出与逐类型点查一致；回归锁 = verify never findDomainIdByTypeCode + times(1) findDomainIdsByTypeCodes（distinct 全集）+ 输出 domainCodeMap 断言，旧实现（逐条点查）下必失败"
  - "背景②（T-PERM-055 登记观察撤回）：「PermissionQueryAppServiceImplTest 单独运行 0 tests」为误读——surefire 对 @Nested 类分桶报告，外层类的汇总行恒报 Tests run: 0（连外层自有用例也不计入该行、并入嵌套容器行计数）；XML 按 classname 归属铁证 7 用例（外层 3 + $InterfaceSnapshotTests 2 + $QueryResourcesTests 2）全执行全绿（2026-09-11 实核 TEST-*.xml testcase 条目），无代码问题，T-PERM-055.md 非目标/遗留段同步撤回"
  - "背景③（EXT 处置登记）：EXT-7（batchCheck 逐条 engine.query）立项 T-PERM-061；EXT-8（enqueueAll 逐条 insert）失效撤注——宿主 SyncTaskDomainServiceImpl 已随内部同步子系统删除（T-ACCESS-005），2026-09-11 全仓实核零命中"
  - "低优先级工程健康批次（半天量级），无硬依赖"
design_writeback:
  required: false
  status: none
  rationale: 消费面内部批量化——findDomainIdsByTypeCodes 契约已随 T-PERM-055 落盘 implementation §2.7 接口摘录（「批量上下文必须走预载」注记针对 preloadCoveredTypeCodes/matchesTypeCode 对，本方法无同款强约束句）；单条=批量单元素委托由 DomainClassifyServiceImpl 代码证实、PermissionGrantPlanDomainServiceImpl:595/:646 两处批量消费先例在册，本任务为该能力对最后遗留循环点的应用，无新增设计语义
last_updated: 2026-09-11
---

# T-PERM-060 工程健康小批次——权限视图域映射批量化 + T-PERM-055 误读观察撤回 + EXT-7/8 处置登记

> 状态：done（2026-09-11 立项并收口，用户选定空档方向「工程健康小批次」）
> 依赖：无硬依赖（来源：T-PERM-055 claude 外评存量观察 1 + 收口遗留观察）

## 背景

三个独立小项合并的工程健康批次：

1. **buildDomainCodeMap 逐类型点查**（T-PERM-055 claude 外评存量观察 1，当时定级低优先级不处置）：
   `PermViewAssembler.buildDomainCodeMap`（util 包，@Component）对 entries 涉及的每个 distinct
   resourceTypeCode 调一次 `domainClassifyService.findDomainIdByTypeCode`，每次调用内部跑
   3 条查询。`assemble` 无条件调用该方法（不依赖 domainCode 过滤器是否启用），而 assemble 的
   唯一存续消费面是登录权限串（`PermissionViewAppServiceImpl.buildEffectiveView` → forUserView
   管线）——放大按 distinct 类型数 ×3 计（schema 预置 25 类 + type_definition 可自定义新增，
   无硬上限，最坏 75+ 条查询/次）。
   T-PERM-055 已把批量形态 `findDomainIdsByTypeCodes` 定稿在接口与实现 §2.7（单条形态即批量方法的
   单元素委托），且 `PermissionGrantPlanDomainServiceImpl:595/:646` 已有批量消费先例——本项为
   该口径对最后遗留循环点的应用。
2. **T-PERM-055 误读观察撤回**：收口时登记「PermissionQueryAppServiceImplTest 单独运行
   0 tests、待另行排查」。2026-09-11 复核证伪：surefire 对 `@Nested` 内部类分桶报告，外层类的
   控制台/`.txt` 汇总行恒报 `Tests run: 0`（连外层自有用例也不计入该行，外层自有用例被并入
   嵌套容器行计数——该类 QueryResourcesTests 行报 5 = 其 2 + 外层 3）；XML 按 classname 正确
   归属，7 用例（外层 3 + InterfaceSnapshotTests 2 + QueryResourcesTests 2）实际全部执行且
   全绿（surefire XML 报告 testcase 条目铁证）。观察撤回，无代码问题。
3. **EXT-7/EXT-8 处置**（审计 S-024 无主性能项，看板挂「待单独立项」）：
   - EXT-7：`PermissionCheckAppServiceImpl.batchCheck` 逐 item 循环 `engine.query`（:118）仍存在，
     立项 T-PERM-061（异构 item 分组批量化，引擎级设计，独立任务）。
   - EXT-8：`SyncTaskDomainServiceImpl.enqueueAll` 宿主已随内部同步子系统整体删除
     （T-ACCESS-005 同事务投影替代），2026-09-11 全仓 rg 实核零命中——失效撤注。

## 实施记录

- 代码（commit 7159f0d97）：`buildDomainCodeMap` 收集 distinct typeCode（LinkedHashSet）后一次调
  `findDomainIdsByTypeCodes`（空集短路 `Map.of()` 不调服务），删除逐类型
  `findDomainIdByTypeCode` 点查循环。
- 回归锁（新建 `PermViewAssemblerTest`，本类首件单测）：①批量单次调用锁——
  `verify times(1) findDomainIdsByTypeCodes(全 distinct 集)` + `verify never findDomainIdByTypeCode`
  + 输出 `getDomainCodeMap()` 断言（同类型多资源去重 + 跨类型归属）；②缺 key 边界锁——批量映射
  缺 key（类型未认领）不进 domainCodeMap。RED 已实证（2026-09-11，
  `mvn test -pl access-service -Dtest=PermViewAssemblerTest -DskipTestcontainers=true`：
  旧实现下未打桩 `findDomainIdByTypeCode` 返回 Mockito 对包装类型 Long 的默认值 0L（非 null）
  → `selectValidByIds(1L,[0])` 与打桩失配，两用例均以 PotentialStubbingProblem 于 assemble
  内失败中止——`verify never` 行未及执行，其必失败为推断而非实测）；GREEN 同命令 2/2。
- 空集短路分支未单独上锁（双轨评审裁决）：该守卫是纯优化分支（有无守卫输出一致，仅差一次空集
  服务调用），锁定会过度约束无害重构。
- 误读撤回与 EXT 处置登记同 commit 落盘（T-PERM-055 非目标/遗留段、看板两处 EXT 注记、
  plans/README 归档行 EXT 口径同步）。
- 双轨评审处置（2026-09-11）：代码轨零 P0-P2、P3×2（「上限 12」口径失准并入文档轨修正；
  空集分支锁裁决不补）；文档轨 P1×1（撤回机制数字自相矛盾——亲核 XML classname 3+2+2 证伪
  「外层恰好 0 个」，真实机制=外层汇总行对 @Nested 类恒报 0 且外层自有用例并入嵌套容器行，
  两卡三处+记忆统一修正）+ P2×1（plans/README EXT 旧口径残留）+ P3×4（frontmatter status
  空串→none 对齐 T-ACCESS-030 先例、rationale 注记归属对象修正、「上限 12」改「distinct×3
  无硬上限（schema 预置 25 类）」、「不排期」段清空）。全部为事实性最小修正直接修，无设计取舍项。
- 回归证据：单测轨道 `mvn test -pl access-service -DskipTestcontainers=true` 1133 tests、
  0 失败；收口全量 `mvn test -T 1C`（E2E 必跑）BUILD SUCCESS 一次通过——access-service
  单测轨 1133 + 容器轨 193、e2e 14（BasicRoleGrant 8 + ExampleProtectedApi 6）、其余模块
  30/65/15/10/106 全绿，`TaskExecutionLeaseConcurrencyTest` 本轮 10/10 未触发已登记抖动；
  总耗时 5:42。
- claude CLI 外评（2026-09-11 用户触发，read-only plan 模式、默认模型未显式指定，范围 5e3536427..d95f34576）：P0-P2 全零、P3×2 均核实属实并已修复——①代码侧两处注释残留「≤12 类型」上界（d95f34576 只修了任务卡未同步代码注释，两处统一为「distinct 类型数 ×3 无硬上限」）；②实施记录 RED 失败模式与实测日志不符（实测两用例均以 PotentialStubbingProblem 于 assemble 内失败中止、verify never 行未及执行，「never 双重失败」系推断被写成实证——改写为实测口径并内联命令与日期）。五项专项核查（批量化六分支逐元素恒等含构造性等价证明、回归锁真红真绿无假绿、0-test 撤回证据链与仓库事实逐字吻合、EXT-7/8 处置三处登记互指一致、残留与范围含 d95f34576 回归数字逐项比对）全部通过；第二轮换方法复查（零写入口/API/DB 写入点，调用链反查三公开入口）无发现。存量观察一条登记见下。
- 修复后定向复跑（2026-09-11，`mvn test -pl access-service -Dtest=PermViewAssemblerTest -DskipTestcontainers=true`）：2/2 通过（注释级改动，编译面复验）。

## 非目标 / 遗留

- claude 外评存量观察：`DomainClassifyService.findDomainIdByTypeCode` 经本任务失去最后一个生产调用方（原唯一生产点即 buildDomainCodeMap），现仅剩测试消费（DomainClassifyServiceImplTest 直测 + PermViewAssemblerTest never 断言）——非缺陷，可变动死 API；下次触达 DomainClassifyService 接口时随「删除/收窄」一并处置或显式保留单元素委托形态。
