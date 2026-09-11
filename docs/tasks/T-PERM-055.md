---
doc_type: task
id: T-PERM-055
title: 域分类查询批量预载优化——matchesTypeCode 循环点查放大收敛
status: done
plan: ""
domain: permission-center
design_refs:
  - docs/design/access-service-architecture.md#§13
  - docs/design/permission-center/api-contract.md#§3.4
depends_on: []
blocks: []
acceptance:
  - "背景（T-PERM-046 双轨评审存疑项，2026-09-09 用户拍板登记）：PermViewAssembler 按域过滤权限视图时对每个权限条目循环调一次 matchesTypeCode（每次含 isGlobalDomain 点查+声明集查询，未命中再查全局域声明/补集，补集路径 getAllClaimedTypeCodes 为 per-domain 循环单查的存量 N+1）——1000 条目、10 域租户最坏数千条点查；当前靠管理页低频 + resolveTypeValue/resolveDomainId L1 缓存兜住"
  - "修法方向：按 domainCode 预载域实际范围（含全局域声明/补集一次计算）供循环内过滤复用，消除逐条目点查放大"
  - "不改变三模式语义：ALL/GLOBAL_PLUS/DOMAIN_ONLY 判定结果与现行逐条调用一致，用对拍回归锁（同输入逐条调用 vs 批量预载结果相等）"
  - "调用方清单（GLOBAL_PLUS 批量上下文五处）：PermViewAssembler（权限视图域过滤×2）、UserManageAppServiceImpl（×2）、RoleManageAppServiceImpl（×3）、ResourceManageAppServiceImpl（×3）、PermissionQueryAppServiceImpl（×1）；单次调用场景不动"
  - "低优先级性能项（评审定级非阻断），无硬依赖"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-11
---

# T-PERM-055 域分类查询批量预载优化——matchesTypeCode 循环点查放大收敛

> 状态：done（2026-09-11 收口；2026-09-09 立项为 T-PERM-046 双轨评审存疑项 C）
> 依赖：无硬依赖

## 背景

T-PERM-046 收口的双轨代码轨评审发现：`DomainClassifyService.matchesTypeCode` 的调用方多在批量上下文循环内逐条调用（`PermViewAssembler.filterByDomainCode` 对每个权限条目一次），每次调用含数次单行点查，最坏情况放大到数千条查询。属存量结构（T-PERM-046 改动未恶化，全局域有声明路径反而省掉一个存量 N+1），靠管理页低频 + L1 缓存兜住，登记为独立优化待办而非随任务混修。

## 范围

- 批量预载接口（如 `preloadEffectiveTypeCodes(tenantId, domainCode)` 或批量变体）+ 五处调用方改造。
- 对拍回归锁：批量预载过滤结果 == 逐条 matchesTypeCode 结果（含全局域有声明/无声明/无全局域三态）。

## 验收对照

- ✅ 批量预载接口：落地为 `preloadCoveredTypeCodes(tenantId, mode, domainCode)`（mode 感知的批量变体，比预估名多 mode 参数）。
- ✅ 五处调用方 11 个调用点全部切换预载+contains；`matchesTypeCode` 保留为单次调用场景公开 API 与对拍语义基准（生产调用方清零为预期）。
- ✅ 三模式语义不变：对拍回归锁 6 用例（全局域有声明/无声明/无全局域三态 + 指定域=全局域自身两态 + 三模式与退化输入含无效声明码探针），预载 contains == 逐条 matchesTypeCode。
- ✅ 附带收敛：`getAllClaimedTypeCodes` per-domain 循环单查（存量 N+1）收敛为「selectNonGlobalByTenant + selectByTenantId」两条查询，配 `verify(times(1)).selectByTenantId + times(2)).selectValidByTypeString` 收敛锁。

## 完成记录

- 实现（2026-09-11）：`DomainClassifyServiceImpl.preloadCoveredTypeCodes`——ALL/空 domainCode=全部有效类型码；域不存在=空集；DOMAIN_ONLY=声明集∩有效类型；GLOBAL_PLUS=指定域实际范围∪全局域实际范围（有 CLASSIFY 声明=声明集，无声明/无全局域=未被非全局域认领的动态补集）；声明集中的无效类型码经有效类型交集滤除，对齐 `matchesTypeCode` 的 resolveTypeValue 前置判定（等价性前提：`selectByTypeKeyAndCode` 与 `selectByTenantAndTypeKey` 同表同过滤精确匹配，TypeDefinitionMapper.xml 核实）。接口 javadoc 声明返回集合不保证可变性、调用方不得修改。
- 设计回写：architecture §13.4（域分类联动条补批量预载口径）、api-contract §3.4（管理查询条补 preloadCoveredTypeCodes 强约束）+ §10 条 10 同步；顺带 implementation §2.7 域分类接口摘录补 `preloadCoveredTypeCodes` 与既有缺失的 `findDomainIdsByTypeCodes`；`.claude/rules/permission-center-coding-standards.md` §9 补批量上下文双示例（单副本无镜像）。
- 回归证据：`mvn test -pl access-service -DskipTestcontainers=true`（2026-09-11）1129 tests、0 failures、0 errors；定向 `DomainClassifyServiceImplTest` 13 tests、`RoleManageAppServiceImplTest` 21 tests 全绿；收口全量 `mvn test -T 1C` 两次仅 `TaskExecutionLeaseConcurrencyTest.claimBlockedOverMaxAttempts` 红（同方法、全量负载态），隔离复跑 10/10 全绿——命中 registry 2026-09-06「全量负载时序抖动」已登记定性（任务租约域与本任务改动零交集，不调查）；排除该已定性类后收口全量 `mvn test -T 1C -Dtest='!TaskExecutionLeaseConcurrencyTest' -Dsurefire.failIfNoSpecifiedTests=false` BUILD SUCCESS（access-service 1131 tests 0 失败，E2E 模块 2:33 全绿）。
- 双轨评审处置（2026-09-11，均代码级核实后处置）：代码轨零 P0-P2；P3×3 已修（preload 侧「指定域=全局域自身」两态对拍补强、声明集无效类型码交集滤除探针、javadoc 可变性契约），P3×2 接受现状（空白 type_code 理论边界由 schema NOT NULL+写路径校验兜底；退化输入下预载急切求值成本为收敛方向接受项）。文档轨 P2 收口时序随本收口消解；P3×2 顺带同步（implementation §2.7 摘录、api-contract §10 条 10）；AGENTS.md 能力级概述不动（批量预载强约束由 rule §9 与 api-contract §3.4 承载）。
- claude CLI 外评（2026-09-11 用户触发，read-only plan 模式、默认模型未显式指定，范围 40a837b78..0ce6c0190）：零 P0-P3；五项专项核查（预载逐分支对拍等价含有效性门/补集先后顺序/负缓存不存在、getAllClaimedTypeCodes 重写等价含映射与唯一索引多行不可达、11 调用方无漂移、回归锁反证改坏即红、残留引用）全部通过。存量观察四条均核实为既有现状非本次引入、不处置：①PermViewAssembler.buildDomainCodeMap 按 distinct typeCode 循环 findDomainIdByTypeCode（未用批量变体 findDomainIdsByTypeCodes，上限≤类型数、低优先）；②隔离复跑证据留存于本机 /tmp 日志非仓库产物；③「指定域=无声明全局域」路径 claimed 双算（既有 matchesTypeCode 同款形状，可忽略）；④matchesTypeCode「单次场景可继续用」javadoc 措辞与生产全切预载的现状差异（指引本身不误）。
- codex luna max 外评（2026-09-11 用户触发，gpt-5.6-luna + reasoning max + 872000 上下文显式注入，read-only，范围 40a837b78..62c6b9404，banner 核对一致，297,962 tokens，跑后 git status 实核干净）：零 P0-P3——「未发现具有明确证据的生产级缺陷」；两轮换方法复核（新增接口/全部预载调用方反查租户来源、既有权限门禁、短路顺序与查询参数 + domain_config 软删/配置类型/部分唯一索引、类型定义有效性、全局域四种退化路径与测试变异点独立复核 + 残留引用与提交范围收口核对）均通过。双外评通道（claude/codex）+ 双轨本地评审 + 收口全量回归（含 E2E）五面验证闭环。

## 非目标 / 遗留

- ~~`PermissionQueryAppServiceImplTest` 单独运行 0 tests~~ **撤回（2026-09-11，T-PERM-060 复核证伪）**：系误读 surefire 对 `@Nested` 类的分桶报告——控制台 `Tests run: 0 -- in OuterClass` 行只统计外层容器自有用例（该类外层恰好 0 个），7 个用例实际全在 `@Nested` 容器行报告（QueryResourcesTests=5 / InterfaceSnapshotTests=2）；surefire XML 报告铁证 7 用例全执行全绿（`TEST-...PermissionQueryAppServiceImplTest.xml` testcase 条目 3+2+2）。无代码问题，不立项。
- claude 外评存量观察 ①（buildDomainCodeMap 逐 distinct typeCode 循环 findDomainIdByTypeCode）→ 已立项 T-PERM-060 处置（2026-09-11）。
