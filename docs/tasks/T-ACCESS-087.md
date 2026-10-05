---
doc_type: task
id: T-ACCESS-087
title: 管理面一致性杂项收敛
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md（§7.8 用户创建/PERSONAL 口径/域配置）
depends_on: []
blocks: []
acceptance:
  - "双「创建用户」端点语义分叉：契约 §7.8 删「适合管理端」误导标注或补两轨选择指引"
  - "PERSONAL 三方口径对齐（README「未交付」/前端不提供/后端可建可授——三选一对齐并回写三处）"
  - "成员搜索补用户名/手机号维度（MemberTab）"
  - "UserBatchCreateReq 死 DTO+残留 import 删除；FileController.java:5 同型死 PageReq import 顺带清"
  - "biz-domain 页补「域如何被消费」提示（域配置→授权页子权限允许集的因果说明）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-ACCESS-087 管理面一致性杂项收敛

## 背景

两套「创建用户」端点（user/create 与 abstract-user/perm 族）语义分叉，契约对 perm 族还标注「适合管理端」误导；PERSONAL 角色三方口径不一致（根 README「未交付」、前端 MANAGEABLE_ROLE_TYPES 不含、后端 resolveTypeValue 正常创建）；成员搜索仅姓名/邮箱/状态（无用户名/手机号）；UserBatchCreateReq 死 DTO+残留 import；biz-domain 配置的消费链完全隐式（页面无「在域页配 SUB_PERM 会影响授权页子权限可选集」的说明——消费链真实存在：GrantChildConfigurator 恒传 domainCode=null 但按 parentResourceTypeCode 反查域配置，`PermissionGrantPlanDomainServiceImpl.java:780-791`）。

## 范围

口径对齐三处、搜索维度补齐、死代码清理、域消费说明。

## 当前口径

PERSONAL 对齐方向（改 README/后端拒/前端放出三选一）实现时定稿并回写三处一致；域说明只做提示不做显式域筛选（GrantContext P1-1 定案不质疑）。

## 验收对照

- [ ] 契约 §7.8 修正
- [ ] PERSONAL 三处一致
- [ ] 搜索维度补齐
- [ ] 死 DTO/import 清零
- [ ] 域消费提示在页

## 非目标 / 遗留

- 授权页显式域筛选：不做（P1-1 定案维持）。
