---
doc_type: task
id: T-ACCESS-073
title: 历史迁移支持裁决与测试资产闭合
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#support-boundary
  - docs/design/dependency-auto-grant.md#10-正式契约与迁移
  - docs/design/access-service-api-contract.md
  - docs/design/access-service-architecture.md
  - docs/design/services/gateway.md
  - docs/design/access-service-rebuild-runbook.md
depends_on: 
  - T-ACCESS-069
blocks: []
acceptance:
  - "071 与 062 各自的支持保留/退出有当前权威与来源，未定时不宣称本卡完成"
  - "退出分支的当前发布/初始化/回滚/拒绝保护有实跑 PG 接替证据，含无授权种子禁止副作用"
  - "脚本/fixture/手册及 T-ACCESS-066/Q-015 的活跃引用闭合，历史引用与执行入口区分"
  - "支持保留分支注明不删范围与依据；设计 §3 及实际受影响产品权威完成回写"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-073 历史迁移支持裁决与测试资产闭合

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#support-boundary)与[执行计划](../testing-simplification-plan.md)。两条历史升级支持已独立确认退出，当前按接替证据与引用闭合推进。

## 范围

1. 以 T-ACCESS-069 对 071 自动授权迁移、062 API 授权退役分别形成的支持裁决为入口；任何一条待定时不得删除对应资产，退出一条不蕴含另一条退出。支持保留时明确保留独有迁移/回滚证据，本卡以裁决与范围闭合验收，不以必须删文件为目标。
2. 仅确认退出 071 时，先更新 dependency-auto-grant §10 与该链路运维入口，再处理 AutoGrantMigrationPgIT、071 预检/迁移 SQL、auto-grant-before-071.sql 及 ManifestMigrationPublishPgIT 的历史部分；保留 ApiAuthorizationRetirementPgIT、062 退役/回退 SQL 和手册。062 只有单独裁决退出后才可处理，同时闭合 AccessBootstrapInitializer 的受控迁移提示、服务架构 §14.2、契约 §25、Gateway 回退说明及重建手册；不得保留拒启却删掉其仍承诺的恢复路径。涉及生产提示调整时先核对范围，不借本卡改变拒启行为。
3. 混合迁移发布测试中的当前 manifest 编译与无授权种子时不凭空授权，优先迁入 PermissionManifestPgIT；使用隔离租户/目标或前后状态，不依赖全库为空。
4. 按两条链路分别闭合 fixture、盘点/迁移/回退 SQL、手册、发现配置、生产引用和任务引用；核对 T-ACCESS-066 与 Q-015 同步条件，先后顺序均不留悬空约束。历史资产按文档归档规则保留追溯。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] 071 与 062 各自的支持保留/退出有当前权威与来源，未定时不宣称本卡完成
- [x] 退出分支的当前发布/初始化/回滚/拒绝保护有实跑 PG 接替证据，含无授权种子禁止副作用
- [x] 脚本/fixture/手册及 T-ACCESS-066/Q-015 的活跃引用闭合，历史引用与执行入口区分
- [x] 支持保留分支注明不删范围与依据；设计 §3 及实际受影响产品权威完成回写

## 非目标 / 遗留

不连接或迁移未知部署库，不清历史生产数据，不删除当前旧协议拒绝/冲突拒启锁。


## 完成记录

2026-10-03：两条支持均选择退出，无保留分支。历史资产、当前发布/no-seed 接替与活跃引用已闭合，见[验收证据](evidence/T-ACCESS-073/retirement-evidence.md)。最终定向 PG 48 testcase 零失败/错误/跳过，目标 no-seed 错误变体有效。完整计划回归由 T-ACCESS-076 承担。
