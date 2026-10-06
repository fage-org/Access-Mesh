---
doc_type: task
id: T-PERM-108
title: 同步对接方体验补齐
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§2.5 同步契约）
  - docs/ops/runbook-full-sync.md（对接指引章节）
depends_on: []
blocks: []
acceptance:
  - "full-sync items 豁免维持（契约 §2.5 已登记 2026-09-21 拍板）+runbook 补规模与分批指引"
  - "离职 DELETE 残留授权与绑定行的校准语义写进 runbook/契约"
  - "类型所有权 fail-closed 裸码补契约级拒绝码语义表（与实现一致；细化 reason 属设计变更随卡评估）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-PERM-108 同步对接方体验补齐

## 背景

full-sync 单事务阻塞、items 无上限（豁免引擎 @Size(1000)，DTO 仅 @NotEmpty）、无异步无进度——10 万存量首推=巨型 HTTP 请求挂在单事务里三面受敌（网关超时/客户端超时/事务膨胀），断在哪无从得知。离职 DELETE 残留授权与绑定行，数据卫生靠后续 full-sync 校准（未写进文档）。类型所有权排障信息仅服务端日志（fail-closed 裸码），对接方排障=找平台方看日志。

## 范围

runbook/契约的对接指引补齐与拒绝码语义表。items 硬上限与异步化不实施（豁免是既定拍板）。

## 当前口径

维持「完整快照豁免」（清单完整性是协议语义）；runbook 承载规模预期与分批纪律；拒绝码语义表对齐实现。同步状态可观测面归 T-FE-065（本卡不含端点/页面）。

## 验收对照

- [x] runbook 规模与分批指引在册
- [x] DELETE 校准语义在册
- [x] 拒绝码语义表与实现一致

## 非目标 / 遗留

- full-sync 异步任务/进度查询：不在本卡（真实大租户对接出现再立项）。
- items 硬上限：须重新拍板取代豁免定案才可做，本卡不默认。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
