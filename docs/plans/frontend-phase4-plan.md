---
doc_type: plan
title: 前端 Phase 4 — 扩展性验证 + 代码清理 + 文档
status: proposed
domain: common
design_refs:
  - docs/design/architecture.md
  - docs/design/frontend/extension-guide.md
tasks:
  - T-FE-023
  - T-FE-024
  - T-ADMIN-020
  - T-PERM-038
  - T-PERM-039
acceptance: "扩展点验证通过；扩展指南文档完成；admin-service CRUD 清理；全局 TODO 收口；测试补充达标。"
last_updated: 2026-06-29
---

# 前端 Phase 4 — 扩展性验证 + 代码清理 + 文档

> 状态：proposed
> 来源：`docs/plans/improvement-plan.md` §4 Phase 4 拆分
> 准入：Phase 3 联调通过

## 目标

- 扩展性验证（SPI / 配置驱动 / 代码级扩展）+ 接入文档
- 代码清理（admin-service CRUD、全局 TODO、构造函数膨胀）
- 测试补充
- 文档更新

## 任务清单

| ID | 内容 | 领域 | 优先级 |
|---|---|---|---|
| T-FE-023 | SPI 策略扩展验证 + 扩展指南（`docs/design/frontend/extension-guide.md`） | frontend | 🔴 |
| T-FE-024 | ReConditionPicker + ReConditionEditor 条件选择/编辑组件抽取 | frontend | 🟡 |
| T-ADMIN-020 | admin-service CRUD 代码清理（痛点 #6） | admin-service | 🟢 低 |
| T-PERM-038 | 全局 TODO 收口（improvement-plan 附录 A） | permission-center | 🟡 |
| T-PERM-039 | 测试补充（permission-center 新增改造接口测试） | permission-center | 🟡 |

> 文档更新项（api-contract / core-flows / architecture / extension-guide）随各任务 design_writeback 完成，不单列任务。

## 归档条件

- 4 项任务 done
- 扩展指南文档完成
- improvement-plan §4.4 文档更新表覆盖

## 当前进度

- 2026-06-29：建立本 plan + 拆分 4 个任务。全部 proposed，待 Phase 3 收尾。
