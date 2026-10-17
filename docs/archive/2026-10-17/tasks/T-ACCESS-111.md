---
doc_type: task
id: T-ACCESS-111
title: 服务删除永久回收凭证
status: done
plan: docs/archive/2026-10-17/permission-lifecycle-boundaries-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/service-authentication.md
depends_on: []
acceptance:
  - 删除与凭证回收原子，其他租户及服务不受影响
  - 新版删除后的同码重建不能复活或重新启用旧凭证
  - 并发签发与删除不存在漏收窗口，停用恢复仍可用
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-074](../../../pending-problems.md#q-074)，实施范围已于 2026-10-17 确认。

## 范围

删除普通服务时在同事务批量软删除其全部凭证；同码新登记必须重新签发，服务停用恢复仍保留凭证；签发与删除串行化避免漏收。

## 当前口径

以 design_refs 中的现行契约及本任务范围为准；新增设计取舍必须由用户决策。

## 验收对照

- [x] 删除与凭证回收原子，其他租户及服务不受影响
- [x] 新版删除后的同码重建不能复活或重新启用旧凭证
- [x] 并发签发与删除不存在漏收窗口，停用恢复仍可用

## 非目标 / 遗留

旧版本已删除服务留下的凭证由维护人员在同码重建前人工清理；不新增启动扫描或新建登记时自动清理。自动回收只保证新版执行的服务删除，升级边界见契约总册 §24.2 和部署手册。


## 完成记录

2026-10-17：后端根命令 `mvn test -T 1C` 2944 项通过，零失败/错误/跳过（含 E2E/heavy）；前端 611 项与类型、lint、45 组 DTO 对账、构建通过。双轨评审及定向证据见[验收记录](evidence/lifecycle-boundaries/verification.md)。
