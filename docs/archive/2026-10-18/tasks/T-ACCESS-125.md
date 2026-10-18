---
doc_type: task
id: T-ACCESS-125
title: 岗位成员锁内结构读取
status: done
plan: docs/archive/2026-10-18/backend-concurrency-hardening.md
domain: org-user
design_refs:
  - docs/design/redis-transaction-locks.md
  - docs/design/default-org-tree-user-lifecycle.md#member-write-concurrency
depends_on:
  - T-ACCESS-121
acceptance:
  - "Q-085 assign/remove 使用锁内最新 parent"
  - "保持批量读取及原门禁，移除后不遗留岗位权限"
  - "移动与分配/移除的确定性反例通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

来源为 [T-ACCESS-121 排查](T-ACCESS-121.md)，原始证据和全部调用点清单随该卡归档。

## 范围

- Q-085 assign/remove 使用锁内最新 parent
- 保持批量读取及原门禁，移除后不遗留岗位权限
- 移动与分配/移除的确定性反例通过

## 当前口径

遵循[公共锁与并发接入设计](../../../design/redis-transaction-locks.md)；不把单一机制替代所有并发保障。

## 验收对照

- [x] Q-085 assign/remove 使用锁内最新 parent
- [x] 保持批量读取及原门禁，移除后不遗留岗位权限
- [x] 移动与分配/移除的确定性反例通过
- [x] 收口完整回归、本地双轨与设计回写。

## 非目标 / 遗留

原暂缓事项不变。同根因入口已纳入本卡验证，无未完成的同范围事项。

## 完成记录

2026-10-18：定向反例、原有功能验收与本地双轨通过；完整 `mvn test -T 1C` 3018项，0失败/错误/跳过，包含E2E/heavy。共同证据见[联合验收](evidence/backend-concurrency/verification.md)，不将单纯组件提取当作问题闭合。
