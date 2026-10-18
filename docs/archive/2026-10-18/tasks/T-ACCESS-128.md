---
doc_type: task
id: T-ACCESS-128
title: Gateway 清理代际不回退
status: done
plan: docs/archive/2026-10-18/backend-concurrency-hardening.md
domain: gateway
design_refs:
  - docs/design/redis-transaction-locks.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-121
acceptance:
  - "Q-089 孤立清理不能使失效 token 重新有效"
  - "保持同步提交边界、回源截止与现有 TTL"
  - "确定性清理/失效/回源交错验证通过"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

来源为 [T-ACCESS-121 排查](T-ACCESS-121.md)，原始证据和全部调用点清单随该卡归档。

## 范围

- Q-089 孤立清理不能使失效 token 重新有效
- 保持同步提交边界、回源截止与现有 TTL
- 确定性清理/失效/回源交错验证通过

## 当前口径

遵循[公共锁与并发接入设计](../../../design/redis-transaction-locks.md)；不把单一机制替代所有并发保障。

## 验收对照

- [x] Q-089 孤立清理不能使失效 token 重新有效
- [x] 保持同步提交边界、回源截止与现有 TTL
- [x] 确定性清理/失效/回源交错验证通过
- [x] 收口完整回归、本地双轨与设计回写。

## 非目标 / 遗留

原暂缓事项不变。同根因入口已纳入本卡验证，无未完成的同范围事项。

## 完成记录

2026-10-18：定向反例、原有功能验收与本地双轨通过；完整 `mvn test -T 1C` 3018项，0失败/错误/跳过，包含E2E/heavy。共同证据见[联合验收](evidence/backend-concurrency/verification.md)，不将单纯组件提取当作问题闭合。
