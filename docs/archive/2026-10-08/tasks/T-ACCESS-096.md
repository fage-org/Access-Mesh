---
doc_type: task
id: T-ACCESS-096
title: 租户运营产品验收与文档收口
status: done
plan: docs/archive/2026-10-08/tenant-lifecycle-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md
  - docs/design/access-service-api-contract.md
  - docs/design/access-service-rebuild-runbook.md
  - docs/ops/deployment.md
depends_on:
  - T-ACCESS-091
  - T-ACCESS-092
  - T-ACCESS-093
  - T-ACCESS-094
  - T-ACCESS-095
  - T-GW-013
  - T-FE-067
acceptance:
  - 经产品入口完成平台登录、真实开通双租户、强制改密、隔离与停用恢复验收
  - 同步和 OAuth2 凭据、后台任务、Redis 故障恢复及身份混用边界已验证
  - 本地代码和文档双轨评审及收口全量回归含 E2E 和 heavy 通过
  - 运行文档和现行规范回写、残留清扫完成，Q-063 收敛并归档计划
design_writeback:
  required: true
  status: done
last_updated: 2026-10-08
---

## 背景

共享协议或直接造数据的测试不能代替租户运营产品链交付。

## 范围

e2e 与浏览器旅程、完整回归、本地双轨评审、文档和生命周期收口。

## 当前口径

产品验收中的租户必须通过开通接口创建。全量输出落盘；失败先隔离定性；收口不得跳过 E2E 或 heavy。外部评审仅用户另行要求时执行。

## 验收对照

- [x] 真实双租户产品旅程。
- [x] 生命周期与故障场景。
- [x] 本地双轨及收口全量验证。
- [x] 规范回写、Q-063 收敛与归档。

## 非目标 / 遗留

不自动执行真实部署库销毁、上线发布或远端推送；部署迁移按现有重建规程由环境负责人执行。

## 验收证据

已完成实现、设计回写和本地双轨检查。统一验证结果见[验收记录](evidence/tenant-lifecycle/verification.md)。
