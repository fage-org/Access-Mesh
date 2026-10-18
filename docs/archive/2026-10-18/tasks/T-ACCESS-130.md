---
doc_type: task
id: T-ACCESS-130
title: 后端并发与公共 Redis 锁规范入口
status: done
plan: null
domain: common
design_refs:
  - docs/design/project-rules.md#backend-concurrency
  - docs/design/redis-transaction-locks.md#transaction-lock-contract
depends_on:
  - T-ACCESS-129
acceptance:
  - 工程总则明确公共 Redis 事务锁的强制复用范围及接入、验收要求
  - AGENTS、定案导航、开发规则与技能、评审入口能定位同一权威规范
  - 技能双副本一致，链接与场景检查及本地双轨完成
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

用户要求将 Redis 锁纳入项目规范。原公共锁文档已为 adopted，但工程总则及开发/评审入口未明确接入，容易漏读。

## 范围

在工程规范 §9.4 登记统一组件的适用范围、事务责任、锁序、新鲜读取、竞争入口与验收要求；公共锁文档承载机制细节，其余入口仅链接。同步 `.agents` / `.claude` 技能镜像。

## 当前口径

同步数据库事务需要 Redis 互斥时，必须复用公共组件或既有业务门面。数据库原子保障、任务租约/fencing、缓存与本地同步保持按场景选型；原有接受边界不因规范接入而扩大或撤回。详见[工程规范](../../../design/project-rules.md#backend-concurrency)。

## 验收对照

- [x] 工程总则与公共锁规范已接入。
- [x] 开发、评审和文档入口已补齐。
- [x] 链接、场景、技能镜像及本地双轨检查。

## 非目标 / 遗留

不改生产或测试代码，不新增锁/AOP/配置，不自动扩大 Redis 使用范围。原暂缓事项保持。

## 完成记录

2026-10-18：仅文档与开发指引变更；链接、稳定锚点、技能双副本、差异空白检查通过。未运行 Maven/容器/E2E，不将上一任务业务测试计为本任务实跑。

代码轨只读对照现有公共组件、树锁和重置门面：规范与实际事务检查、原线程获取/释放、afterCompletion、非等待冲突及技术错误处理一致。生产 getLock 入口仍在公共组件；本任务无代码改动。实证通过项：源码对照和变更文件范围检查。P0～P3未处置项、存疑待决项、过度设计可裁剪项均无。

文档轨在代码轨后独立核对：工程总则→公共规范→AGENTS/定案导航/开发与评审入口可追溯，镜像同步且未复制完整协议。场景检查覆盖树写等待、重置非等待、字典父子行锁、任务租约/fencing、缓存故障回源与锁故障终止的区分、Reactive/跨线程不适用；原接受边界保持。P0～P3未处置项、存疑待决项、过度设计可裁剪项均无。
