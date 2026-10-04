---
doc_type: task
id: T-ACCESS-072
title: Schema 证据迁至 PG 与连接烟测去重
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#candidates
  - docs/design/testing-simplification.md#validation
depends_on: 
  - T-ACCESS-069
blocks: []
acceptance:
  - "PG 约束负例各自可执行且命中目标约束，移除目标约束会由对应断言失败，不再捕获上次错误留下的事务中止"
  - "H2 有效证据逐项有 PG 或其他可靠落点，CRUD 数量与操作位/掩码未混同"
  - "无 Docker/日常单测轨的 DDL 兜底有显式处置；未确认 PG-only 反馈边界或未有可靠接替时保留 H2"
  - "接替 PG 案例实际发现且执行，关键约束替代具备目标错误反例；skip 不得验收"
  - "烟测配置等价有证据；不等价则明确保留，不机械把非空断言搬入大类"
  - "移除资产无活跃引用残留；设计 §4/§6 回写真实结果与基线对比"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-072 Schema 证据迁至 PG 与连接烟测去重

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#candidates)与[执行计划](../testing-simplification-plan.md)。实施已启动；方案按章节采纳，行为验证以实际报告为准。

## 范围

1. 先修正 AccessServiceSchemaPostgresTest.shouldKeepPartialUniqueIndexSemantics 在同事务连续异常后未恢复导致的假绿：负例独立事务或保存点、SQLSTATE/目标约束断言、合法准备不包入 assertThrows；定向错误变体证明目标约束失效会变红，再推进替代。
2. 比较 AccessServiceSchemaH2Test 与 AccessServiceSchemaPostgresTest 的每个有效断言；优先接入已有 PG 类，覆盖设计 §4 所列初始差异，含 CRUD 方法内额外位值/掩码。
3. 以原样 DDL、独立事务/数据验证列、种子与约束；异常断言验证目标约束及有效对照，不接受任意 SQLException 的假替代。
4. 单独核对 PermissionCenterIntegrationTest 与 AccessBootstrapPgIT/其他真实业务测试的 profile、数据源与 Redis 配置、执行路径；仅证据充分时删除独立烟测。
5. 先运行接替证据，并明确处置 H2 在无 Docker/日常单测轨执行适配 DDL 的兜底职责，再决定退出 H2 schema 字符串适配与冗余烟测。若接受 PG-only，记录日常非容器轨不再验证 DDL、DDL 变更须实跑定向 PG，且同步 PG 类 Javadoc 等兜底引用；未确认该反馈边界或未有可靠接替则保留 H2。不得用只检查文本或仍不能执行 PG 方言的极简烟测冒充等价兜底。保留其他 H2 上下文与原双 execution。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] PG 约束负例各自可执行且命中目标约束，移除目标约束会由对应断言失败，不再捕获上次错误留下的事务中止
- [x] H2 有效证据逐项有 PG 或其他可靠落点，CRUD 数量与操作位/掩码未混同
- [x] 无 Docker/日常单测轨的 DDL 兜底有显式处置；未确认 PG-only 反馈边界或未有可靠接替时保留 H2
- [x] 接替 PG 案例实际发现且执行，关键约束替代具备目标错误反例；skip 不得验收
- [x] 烟测配置等价有证据；不等价则明确保留，不机械把非空断言搬入大类
- [x] 移除资产无活跃引用残留；设计 §4/§6 回写真实结果与基线对比

## 非目标 / 遗留

不整体移除 H2，不共享 Spring 上下文，不按删类数推算容器启动收益。


## 完成记录

2026-10-03：PG 负例保存点恢复与目标约束断言、H2 有效主张迁入、PG-only 及烟测去重完成。最终定向命令 `mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,AccessBootstrapPgIT`：39 testcase，零失败/错误/跳过；目标错误变体和逐项映射见[验收证据](evidence/T-ACCESS-072/schema-evidence.md)。完整计划回归由 T-ACCESS-076 承担。
