---
doc_type: task
id: T-ACCESS-088
title: 安全语义失败形态判别表
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/extension-guide.md（§2.4 失败形态判别）
  - docs/design/project-rules.md（§3.3 分类状态码）
depends_on: []
blocks: []
acceptance:
  - "extension-guide 补「失败形态判别表」：四档（Biz=200/参数=400/Security=403/兜底=500）+Gateway 真 403+sync accepted=false 各自含义与判别指引"
  - "判别表与三类拒绝（Gateway 403/业务信封 30004/sync accepted=false）实测一致"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-ACCESS-088 安全语义失败形态判别表

## 背景

同一个「无权限」两种方言：授权未生效死在 Gateway=真 HTTP 403；授权生效业务终检拒绝=HTTP 200+code=30004；签名失败=200+30003；sync 安全拒绝=200+code=200+accepted=false（凭证错却是 403）。HTTP 侧监控/告警只看得见 Gateway 拒绝，业务层安全拒绝在 access log 全是 200；extension-guide 自己警告「只看 HTTP 状态会误判成功」。

## 范围

判别表文档。方言本身不改。

## 当前口径

维持分类状态码约定（2026-10-05 拍板 D7=A，project-rules §3.3 既有定案）；升非 200 不实施（重访条件：出现按 HTTP 状态做监控的真实消费方）。

## 验收对照

- [x] 判别表在册且覆盖四档+Gateway 403+sync accepted
- [x] 三类拒绝实测一致

## 非目标 / 遗留

- 安全类业务拒绝升非 200（错误契约破坏性变更）：不实施。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
