---
doc_type: task
id: T-ACCESS-121
title: 后端并发专项排查与公共 Redis 锁方案
status: done
plan: null
domain: cross-service
design_refs:
  - docs/design/redis-transaction-locks.md
depends_on: []
acceptance:
  - 以指定 gpt-6.1-sol 排查后端现状，保留原始提示词和报告
  - 核实候选问题、已有保障和接受边界，登记未修复问题
  - 形成 common 最小公共事务锁方案及业务修复优先级
  - 完成代码证据与文档两轨本地检查，明确未运行的验证
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

用户要求使用 gpt-6.1-sol 排查后端并发问题，并形成公共模块 Redis 锁统一方案。当前仅测试环境，既有暂缓事项不因此重启。

## 范围

基于 8733a51c24adfe64b1ff4cb9a75594a1b324a3d6 排查 access-service、common、Gateway、SDK、example-service 的相关生产代码、SQL、框架行为与当前设计，产出[公共锁草案](../../../design/redis-transaction-locks.md)与逐项核实证据。本卡不承载跨业务实现迁移。

## 当前口径

公共化复用现有事务锁机械责任，树写锁与密码重置锁保留业务门面；数据库约束、CAS、租约及本地代际机制继续按适用场景使用。方案目前为 draft，不替代现役契约。

## 验收对照

- [x] 指定模型参数的只读排查及原始报告保存。
- [x] 逐项代码和当前边界核实。
- [x] 公共锁方案、迁移清单及优先级形成。
- [x] 本地双轨检查、链接验证及归档。

## 非目标 / 遗留

Q-083～Q-089 登记于[待解决问题](../../../pending-problems.md)，均未修复；本卡完成只表示排查与方案交付，不能据此关闭缺陷。公共类尚未从 access-service 迁入 common；未新增业务锁、改变既有产品语义或执行测试环境部署。Q-060/Q-062/Q-065/Q-068 与 T-PERM-036 的暂缓边界保持。

## 完成记录

原始报告、模型调用参数、框架依据、问题处置映射与验证边界见[核实记录](evidence/T-ACCESS-121/verification.md)。
