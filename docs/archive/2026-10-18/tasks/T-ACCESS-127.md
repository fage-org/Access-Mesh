---
doc_type: task
id: T-ACCESS-127
title: 字典父子生命周期互斥
status: done
plan: docs/archive/2026-10-18/backend-concurrency-hardening.md
domain: access-service
design_refs:
  - docs/design/redis-transaction-locks.md
  - docs/design/access-service-architecture.md#backend-write-concurrency
depends_on:
  - T-ACCESS-121
acceptance:
  - "创建补齐schema既有非空默认值，真实PG验证通过"
  - "Q-088 类型删除检查与数据新增/改绑共用父行锁"
  - "批量锁顺序固定，已有错误码与缓存提交语义保持"
  - "两向交错无孤儿或旧编码数据附着"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

来源为 [T-ACCESS-121 排查](T-ACCESS-121.md)，原始证据和全部调用点清单随该卡归档。

## 范围

- Q-088 类型删除检查与数据新增/改绑共用父行锁
- 批量锁顺序固定，已有错误码与缓存提交语义保持
- 两向交错无孤儿或旧编码数据附着

创建路径补齐 schema 已定义的 sort_order=0/is_default=false，修复显式 NULL 绕过默认值的既有缺陷。

## 当前口径

遵循[公共锁与并发接入设计](../../../design/redis-transaction-locks.md)；不把单一机制替代所有并发保障。

## 验收对照

- [x] 创建补齐schema既有非空默认值，真实PG验证通过。

- [x] Q-088 类型删除检查与数据新增/改绑共用父行锁
- [x] 批量锁顺序固定，已有错误码与缓存提交语义保持
- [x] 两向交错无孤儿或旧编码数据附着
- [x] 收口完整回归、本地双轨与设计回写。

## 非目标 / 遗留

原暂缓事项不变。同根因入口已纳入本卡验证，无未完成的同范围事项。

## 完成记录

2026-10-18：定向反例、原有功能验收与本地双轨通过；完整 `mvn test -T 1C` 3018项，0失败/错误/跳过，包含E2E/heavy。共同证据见[联合验收](evidence/backend-concurrency/verification.md)，不将单纯组件提取当作问题闭合。
