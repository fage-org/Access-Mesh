---
doc_type: task
id: T-API-008
title: SDK 与公共模块卫生
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md（M2M 端点清单相关章）
  - docs/design/service-authentication.md（M2M 白名单）
depends_on: []
blocks: []
acceptance:
  - "DefaultOpCode.EDIT 清理或映射 UPDATE（服务端无此预置操作，check 恒拒绝的陷阱常量不再暴露）"
  - "perm-common 零使用旧模型退役评估落地（PermContext/PermCheckReq/PermCheckResp；PermInvalidateEvent 保留——gateway 订阅在用）"
  - "M2M 端点清单两侧镜像改单源（依赖 common 常量）或对账测试（两侧漂移即红）"
  - "perm.client.enabled 默认语义定稿（matchIfMissing=true 类路径即启用的口径修正或文档明示）"
  - "GatewayResponse 与 R 两套同形信封：gateway 复用 common 纯模型（pom 已依赖）或对账锁"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-API-008 SDK 与公共模块卫生

## 背景

DefaultOpCode.EDIT 在服务端不存在（预置是 CREATE/VIEW/UPDATE/DELETE），用它 check 走未知操作码 fail-closed 恒拒绝——陷阱已知却用文档提醒而非改枚举。perm-common 携带零使用旧模型发布（IDE 补全可选中旧形态，编译通过语义报废）。M2M 端点清单两侧手工镜像（16 条×2），新增端点=隐性版本耦合（旧 SDK 拿 403 无提示）。perm.client.enabled 默认开启类路径即启用（仓库自己踩过：e2e 需 --perm.client.enabled=false 防传染）。GatewayResponse 与 R 两套同形信封靠注释纪律对齐。

## 范围

五个卫生项的收敛/退役/单源化或对账锁。

## 当前口径

退役评估逐项落地（保留项显式记录理由）；M2M 清单与信封两处取「单源优先，不可行才对账锁」。

## 验收对照

- [x] EDIT 处置落地
- [x] 旧模型退役或登记保留理由
- [x] M2M 清单单源/对账
- [x] enabled 语义定稿
- [x] 信封单源/对账

## 非目标 / 遗留

- SDK 清单由服务端下发（版本派生）：不实施。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
