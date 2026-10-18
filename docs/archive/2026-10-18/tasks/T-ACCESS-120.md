---
doc_type: task
id: T-ACCESS-120
title: Redis 事务锁取锁中断后的释放责任
status: done
plan: null
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#password-reset-lock
  - docs/design/access-service-architecture.md#tree-write-lock
  - docs/design/access-service-api-contract.md#password-reset-concurrency
depends_on: []
acceptance:
  - 用实际 Redisson 确认取锁等待中断与迟到成功的泄漏窗口
  - 密码重置和树写锁共用可靠的获取结果及事务释放逻辑
  - 中断请求不得继续业务写入，迟到成功不得遗留无人负责的续期锁
  - 保留非等待重置、阻塞树锁、身份域键隔离和事务完成释放语义
  - 完成定向反例、完整回归及本地双轨评审
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

用户要求 Codex 评审 `c754c414e` 并修复确认问题。外部报告提出取锁同步等待中断可能遗留迟到获取的续期锁；同族扫描覆盖既有 TreeWriteLockSupport。

## 范围

仅处理 Redis 事务锁获取结果丢失、中断退出和异常释放，复用现有事务与 Redisson。原始报告和核实证据随本卡归档。

## 当前口径

正常取得锁后仍持有到 afterCompletion。线程中断不能取消并丢弃仍可能成功的获取结果；只有结果确定后才能处置本次获取的所有权。保留用户已接受的提交后交付窗口，不引入冷却、领取协议或 fencing。

## 验收对照

- [x] 真实中断反例与修复验证。
- [x] 公共实现及同族覆盖。
- [x] 完整回归与双轨评审。
- [x] 设计回写与归档。

## 非目标 / 遗留

不恢复其他暂缓事项，不改变三个重置入口的授权/身份范围或树写锁等待语义。Redis 故障及续期丢失的既有边界保持。

## 完成记录

2026-10-18：Codex 外评 1 项 P2 经实际 Redisson 中断反例确认并修复，同族树写锁一并收口。`mvn test -pl access-service -Dtest=PasswordResetLockSupportTest,UserLoginRowLockPgIT,TreeCycleHardeningPgIT` 单测 9 项、容器 11 项通过；完整 `mvn test -T 1C` 3004 项，0 失败/错误/跳过，含 E2E/heavy。本地双轨无未处置项；原始外评、核实与回归证据见[验收记录](evidence/T-ACCESS-120/verification.md)。未追加外部评审，其他暂缓事项保持。
