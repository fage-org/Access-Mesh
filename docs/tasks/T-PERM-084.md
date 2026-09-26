---
doc_type: task
id: T-PERM-084
title: （R2-T05）QueryReadSupport 与读来源分桶
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §3.3/§4.2/§5.2/§5.4
depends_on:
  - T-PERM-082
blocks: []
acceptance:
  - "部件级 I02：20 辅助类型默认一次多类型 Mapper/SQL 调用；I03：新读取部件先判断输出开关，最小输出无装配专用操作解析/描述读取；整体 execute 接线分别随 T-PERM-085/087 验证，旧引擎保持不动"
  - "三态记忆（UNLOADED／LOADED_EMPTY／LOADED_VALUE）落地；I04：已读空类型/缺失操作请求内不重复回源（Map.get()==null 不再兼任「未读」与「不存在」）"
  - "I05：缓存回填保留读前令牌/剩余 TTL 不重置；I06：缓存掩码目录与新鲜定义分桶、互不覆盖（缓存掩码不得覆盖 freshDefinitionIndex）"
  - "RolePermEntry 作为缓存载荷边界例外保留（读边界转 GrantFact），新执行器不消费旧 PermResult（§5.4）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-26
---

# T-PERM-084 （R2-T05）QueryReadSupport 与读来源分桶

## 背景

设计 §5.2 读取矩阵与 §4.2 装载批/判定集合分离（报告临时编号 R2-T05）。旧路径 resolveOperationIdsForAncillary 在 includeOperations 开关前逐类型解析，batchLoadOperationsByResourceTypes 逐类型查询。新读取部件改为开关后合批数据库读取；输出/互斥定义仍按设计读取数据库，不改用长 TTL 掩码缓存。

## 范围

- QueryReadSupport 部件（有租户/空集守卫的批量 DB/缓存访问与同源复用）；空 entityIds/bitMasks 守卫不得退化为无界 SQL。
- 读来源分桶与三态记忆；转授无目标类型授权时仍装载目标操作定义（区分 INVALID_OPERATION 与 NO_PERMISSION）。

## 非目标 / 遗留

- 不改各缓存条目 mode/TTL（§5.2 保留既有边界）；RolePermEntry 载荷版本化备选不在本卡（§5.4 备选）。
- 旧 PermQueryEngine 保持不动；本卡只实现新读取部件，I02～I06 按部件级验收。整体 execute 判定与投影接线分别由 T-PERM-085/087 验证（2026-09-26 用户确认）。
- 旧共享 TypeResolutionService 的同型字符串键碰撞及其现役消费方另行处理（Q-044）。

## 当前口径

- `OutputSpec.extraOperationKeys` 改为 `Set<TypeOperation>`，复用已有类型—操作配对；结构校验、模型测试同批更新，外部 HTTP DTO 不变（2026-09-26 用户确认）。
- 请求内读取记忆归 RunState，释放后禁止继续读取；三态用键缺失、Optional.empty/空集合、有值表达。数据库完整操作目录、按 ID 定义索引、普通掩码缓存分别记忆；部分 ID 读取不标记整个类型完整。
- 同次执行保留首次按 ID 未命中，后续整类型数据库查询不推翻该负记忆，下一次 execute 才重新读取（2026-09-26 用户确认）。该记忆不作为共享缓存回填来源。
- 掩码缓存 miss 独立查询本次数据库类型目录，不用请求内旧定义回填；资源内存匹配采用三字段元组，跨层 BusinessKeyUtil 格式不变。
- 原始授权不做 item 过滤。TYPE_GRANT/INSTANCE 固定数据库目标下推；LIST 按请求选择 DATABASE 或 ROLE_SNAPSHOT，前者不读写快照。数据库事实与快照事实隔离；快照 miss 的同一运行态复用首次读前令牌，分块/后续 miss/重试不重置预算。

## 验收对照

| 验收 | 证据 |
|---|---|
| I02 合批读取 | QueryReadSupportTest 二十类型/501 类型分块；QueryReadSupportPgIT 记录真实 JDBC prepare，二十类型一条 SQL |
| I03 输出开关 | minimal 含额外目标也不解析操作、读取资源或角色描述；无授权时开启描述仍读取额外目标定义 |
| I04 三态与复用 | 缺失类型/操作 ID/操作码/资源键/描述及空类型桶重复请求不再回源；资源解析复用类型记忆 |
| I05 缓存令牌 | 冷/热/混合 miss、读前顺序；真实 DefaultCacheService 配可控时钟，耗时 3s/8s 时回填 TTL=7s/2s，11s 不回填；PG/Redis 原载荷往返 |
| I06 来源隔离 | 长 TTL 掩码与数据库定义正反读取顺序均隔离；数据库目标授权不被角色快照替代；操作回填完整字段保留 |
| 读取边界 | 租户/角色/目标/掩码空集守卫、错误透传、不把失败记为空；GrantFact 不可变；RunState 释放后不可复用 |

## 完成记录

- 2026-09-26：`mvn test -pl access-service -DskipTestcontainers=true -Dtest=QueryReadSupportTest,QueryContractModelsTest,QueryRequestValidationTest,QueryExecutionEngineTest` 定向 67 项通过；随后补充 TTL、缓存完整字段、资源复用与来源隔离用例，最终新增读取部件单测为 20 项。
- 2026-09-26：`mvn test -pl access-service -Dtest=QueryReadSupportTest,QueryReadSupportPgIT`，20 项单测＋4 项真实 PG/Redis 用例，0 失败、0 跳过。
- 2026-09-26：`mvn test -T 1C`，11 模块 BUILD SUCCESS；全仓 2127 项、0 失败、0 错误、0 跳过，其中 access-service 单测 1482、容器 367、E2E 16。包含 ResourcePublicationHeavyPgIT，未带 skipE2E/skipHeavyIT；测试期间源码冻结。
- 反例证据：同日 `mvn test -pl access-service -DskipTestcontainers=true -Dtest=QueryReadSupportTest` 在输出读取未实现/额外配对未校验时 14 项中 1 失败＋2 错误；完整缓存载荷断言在缺字段回填时 18 项中 1 失败。`-Dtest=QueryReadSupportTest#should_reuseTypeResolutionAndRememberExactResourceMisses_whenResolvingBusinessKeys` 在委托旧资源解析路径时 1 项失败；修正后均随全量通过。
- 本地代码轨：核对租户/空集守卫、分块后事实完整返回、DB/快照隔离、缓存命中不覆盖新鲜定义、读前令牌和失败透传，实证通过；P3 资源键 mock 返回条件不可能行已改为真实 SQL 可达的跨类型超集（QueryReadSupportTest 的 should_reuseTypeResolutionAndRememberExactResourceMisses_whenResolvingBusinessKeys）。未发现未处置 P0–P2、存疑或机制可裁剪项。
- 本地文档轨：部件与整体执行验收边界、extraOperationKeys 结构化配对、设计引用、GrantFact 已实现注记及 085/087 接线验收已一致；旧引擎零改动、缓存 mode/TTL 零变更、无四旧 DTO 新依赖已核对。无未决项；未自动执行外部评审。
- 2026-09-26 外部评审对象 `d6846666f`：Claude CLI 配置别名 astron-code-latest、会话返回 deepseek-flash（用户确认沿用并据实标注），原始 P2×2；Grok grok-4.6/xhigh，原始 P2×2、P3×1。代码级核验确认两项独立 P2：请求内旧操作定义回填长 TTL 缓存、资源字符串键碰撞，均已修正。按 ID 首次未命中项按用户确认的本次执行稳定语义保留；描述对象可变项缺少实际修改调用方，且 §3.3 约束最终 Details，不作为当前缺陷，087 输出契约仍须落实。无可裁剪项；旧共享解析同型问题登记 Q-044。
- 修正验证（2026-09-26）：`mvn test -pl access-service -DskipTestcontainers=true -Dtest=QueryReadSupportTest` 修正前 24 项中新增 4 条反例全部失败；修正后 `mvn test -pl access-service -Dtest=QueryReadSupportTest,QueryReadSupportPgIT` 24 单测＋6 PG/Redis 全通过。新增真实数据库反例覆盖含冒号的 code/codeType 各回自身 ID，以及操作定义更新＋缓存失效后回填值不使用本次执行旧记忆。
- 最终全量（2026-09-26）：`mvn test -T 1C`，11 模块 BUILD SUCCESS，2133 项、0 失败、0 错误、0 跳过；access-service 单测 1486、容器 369、E2E 16，heavy 实跑。源码冻结期间完成；增量本地代码轨与文档轨检查均通过，无未决项，未追加外部评审。
