---
doc_type: task
id: T-PERM-038
title: 全局 TODO 收口（improvement-plan 附录 A 全量核实与处置）
status: done
plan: docs/archive/2026-09-12/frontend-phase4-plan.md
domain: permission-center
design_refs: []
depends_on: []
blocks: []
acceptance:
  - "附录 A 七条 TODO 逐条核实现状并处置（实现/删除/保留定性），处置记录可追溯"
  - "全仓 TODO/FIXME 扫描（java/ts/vue/xml/yml/properties，排除 node_modules 与 docs）无未定性条目"
design_writeback:
  required: false
last_updated: 2026-09-12
---

# T-PERM-038 全局 TODO 收口

> 状态：done（2026-09-12 收口；工程改进型任务，无设计文档回写，处置记录即本卡）
> 来源：improvement-plan（已归档）附录 A「当前 TODO 清单」7 条 + 全仓补充扫描

## 处置记录（2026-09-12）

### 附录 A 七条逐项定性

| # | 附录 A 原条目 | 现状核实 | 处置 |
|---|---|---|---|
| 1 | `AsyncConfig.java:50` 告警通知 TODO | 行号漂移至 129；内容为「可扩展为发送告警通知（钉钉、邮件等）」——无排期的功能设想，告警渠道不在开源 IAM 核心范围，异常可观测性已由 log.error 覆盖，本仓无 Issue 机制承载 | **删除注释**（handleUncaughtException 标准做法自明） |
| 2 | `PermissionQueryAppServiceImpl.java:162` treeMode TODO | 已随 treeMode 契约移除消解（29f2602b2，评审批次 4a F-09 用户决策：无真实消费方删契约） | 无动作（定性登记） |
| 3 | `PermissionGrantAppServiceImpl.java:60,87` 构造 14 依赖 TODO | TODO 注释已不在；构造依赖 14→10（T-PERM-034 旧端点退役等改造自然瘦身）；且 rule §4 已定案「不设硬上限，逻辑内聚优先」 | 无动作（定性登记） |
| 4 | `PermissionGrantAppServiceImpl.java:130` autoGrantForInsert TODO | 已随 T-PERM-034 第 5 项旧端点退役消解（5757ea59b，五旧写入口删除） | 无动作（定性登记） |
| 5 | `PermissionGrantDomainService.java:15` resolveAutoGrants TODO | 存活 | **保留**（T-PERM-035 暂缓预留锚点，20048 禁用态，registry 已登记，不属本任务清理面） |
| 6 | `PermissionGrantDomainServiceImpl.java:44` resolveAutoGrants TODO | 存活（同上，含 GrantSource.MANUAL 口径） | **保留**（同上） |
| 7 | `TypeResolutionServiceImpl.java:51→55` 构造 7 依赖 TODO | 存活；注释建议「按类型拆分解析服务」与 rule §10「类型解析 MUST 通过 TypeResolutionService 批量方法（统一入口）」直接矛盾，且 rule §4 已定案不设硬上限；7 依赖 = 6 实体 mapper + CacheService，多 mapper 是跨实体类型统一解析职责的本性，类有独立测试（TypeResolutionServiceImplTest） | **删除注释**（与两条现行定案冲突的事实性修正，非重构） |

### 全仓补充扫描定性（附录 A 之外）

| 命中 | 定性 |
|---|---|
| 前端 `resource-dependency` 相关 8 处 batch-sync TODO 注释（api/mock/utils/perms/index/router） | **保留**：Q5=B 产品决策标注（前端 P0 不实现 batch-sync UI 与 mock，后端端点已收口），口径与 `design/frontend/resource-dependency.md` 验收表第 10 项一致，非债务 |
| `frontend/types/global-components.d.ts:15` | **保留**：element-plus 上游模板注释，非项目债务 |
| xml/yml/properties 等其他文件类型 | 零命中 |

### 结论

代码改动仅两处 TODO 注释删除（AsyncConfig + TypeResolutionServiceImpl 构造 Javadoc），无行为变更；附录 A 七条中 3 条已被后续批次消解（各带提交锚点）、2 条为 T-PERM-035 有意预留、2 条删除；全仓无其他未定性 TODO。

## 完成记录

- 2026-09-12：附录 A 全量核实（含 3 条消解提交溯源）+ 全仓扫描定性 + 两处注释删除；access-service mvn compile 通过。
