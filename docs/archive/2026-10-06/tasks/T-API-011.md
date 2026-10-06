---
doc_type: task
id: T-API-011
title: orgType 线格式统一 Integer 与常量治理
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md（用户族/组织族 orgType 线格式与迁移说明）
depends_on: []
blocks: []
acceptance:
  - "user 族响应 orgType 改 Integer（UserPageItemResp/UserResp 去字符串化）；组织族接口与前端组织树节点不动"
  - "契约迁移说明（breaking 注记）在册；前端用户页消费点同步（string→number，typecheck 绿）"
  - "分页默认值收拢（15/20/200 三档→同用途统一；页大小与批量枚举上限不强行归一）"
  - "领域常量收拢（操作码字面量/LOCAL_USER/orgType 数字等统一常量文件），生产表达式不再散落同值字面量（常量定义、测试夹具与展示说明除外）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-API-011 orgType 线格式统一 Integer 与常量治理

## 背景

orgType 同域两线格式：user 族 String（"2"，String.valueOf 转换）vs org 族 Integer（2）——前端同一文件两种注释并存（`user-manage.ts:44,57-61`）；无契约条目收拢，新端点抄哪个先例全看参考了哪段旧代码。分页默认值散落三档（15/20/200）；领域常量字面量散布（ROLE:VIEW/LOCAL_USER/orgType===1 等多处内联）。

## 范围

线格式统一（破坏性变更）、默认值与常量收拢。

## 当前口径

统一 Integer（2026-10-05 拍板 D14=B 子拍板=统一 Integer）：user 族改数字（orgType 为小数字字典值无精度问题，组织族/前端树节点现状已是 number 不动）；公开响应破坏性变更带契约迁移说明与前端同步。

## 验收对照

- [x] user 族 orgType 数字线格式（契约测试+用例）
- [x] 迁移说明与 breaking 注记在册；前端 typecheck 绿
- [x] 默认值收拢（同用途）
- [x] 常量收拢 grep 残留为零

## 非目标 / 遗留

- 其他字段的线格式审计（bigint 已有 string 先例，不扩面）。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
