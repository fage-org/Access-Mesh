---
doc_type: task
id: T-ACCESS-106
title: 租户保底管理员连续性与成员变更审计
status: done
plan: null
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md#tenant-admin-continuity
  - docs/design/access-service-api-contract.md
  - docs/design/access-service-architecture.md
depends_on: []
acceptance:
  - 相关成员与账号写入口拒绝使合格保底成员归零，并发变更不能各自依据旧成员集放行
  - 资格按已采纳规则计算，临时登录锁定与强制改密不改变资格
  - 交接完成后初始化检查不再要求原首管理员及其绑定存在
  - 保留现有权限门禁，补全管理员成员变更审计
  - 行为回归、本地双轨评审与设计回写完成
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

承接 [Q-059](../../../pending-problems.md#q-059)。初始化权限只读保护没有保证仍有用户持有管理员角色；成员撤回可能使租户失去正常管理入口。

## 范围

核对并保护账号启停/删除、角色成员新增/改期/撤回及其共享写入口；核对内置角色生命周期与初始化检查；复用现有审计记录管理员成员变化。真实数据库并发与交接验证复用已有测试基础设施。

## 当前口径

规则唯一来源为[租户保底管理员连续性](../../../design/tenant-lifecycle.md#tenant-admin-continuity)。涉及新的产品支持边界或与现有约束冲突时，先由用户决定，不以实现便利替代决策。

## 验收对照

- [x] 写入口与并发保护。
- [x] 成员计入与临时认证限制。
- [x] 初始化支持交接。
- [x] 现有门禁保留与审计补全。
- [x] 回归、本地双轨评审及设计回写。

## 非目标 / 遗留

不引入审批、平台代租户管理或新的凭据恢复权限；不处理 Q-075 内置服务登记启停的独立产品边界。

## 完成记录

2026-10-17：`mvn test -T 1C` 完整回归 2888 项，0 失败/错误/跳过，含 E2E 与 heavy；实现收尾定向 80 项通过，最终 PG 行为组 14 项及初始化隔离组 20 项通过。后续定向覆盖与 Docker 探测跳过处置、代码/文档双轨结论见[验收记录](evidence/T-ACCESS-106/verification.md)。
