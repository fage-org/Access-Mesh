---
doc_type: task
id: T-API-009
title: DTO 契约同步对账机制（E8 单源化+对账 CI）
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md（DTO 形状相关章）
  - docs/design/project-rules.md（DTO/共享模型约束）
  - docs/design/dto-field-coverage.md
depends_on: []
blocks: []
acceptance:
  - "SDK RolePermissionItemResp 与后端统一到 perm-common 共享单源（消除一对永久对账锁）；接线方拿全 14 字段含 grantedBits"
  - "前端死字段×2 清除；FileController.java:5 同型死 import 顺带清"
  - "新建对账 CI 测试：后端 record/前端 TS 类型/SDK 类型字段集合比对，已漂移家族跑红→修复后绿，新增字段四处漏改即红"
  - "已锁/未锁家族清单登记（契约或设计文档）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-API-009 DTO 契约同步对账机制

## 背景

DTO 形状同步三档并存：共享单源（167 文件 import perm.common.dto）/受锁双副本/未锁手写镜像（前端 TS、个别 SDK 副本零机制）。实证漂移：SDK RolePermissionItemResp 10 字段 vs 后端 14（缺 grantedBits 63 位操作位图等 4 字段），当前无 Feign 方法消费故未爆发——接线即静默丢位图。前端死字段×2。全量对账（32 对）实质漂移仅此一处+死字段两处：风险不在已漂，在无机制发现新漂移（对账脚本为过程产物未进仓）。

## 范围

E8 单源化、死字段清理、对账 CI 新建、家族清单登记。codegen 不立项（拍板）。

## 当前口径

E8 改回 perm-common 共享单源（2026-10-05 拍板 D10=A）；对账 CI 为**新建**（重建抽取与比对逻辑，成本含脚本开发）。

## 验收对照

- [x] E8 单源化+全字段可达
- [x] 死字段与死 import 清零
- [x] 对账 CI 红绿实证（对已漂移家族跑红→修复后绿）
- [x] 家族清单在册

## 非目标 / 遗留

- codegen 统一生成：不立项。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
