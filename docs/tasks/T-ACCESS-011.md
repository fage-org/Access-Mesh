---
doc_type: task
id: T-ACCESS-011
title: 完成契约、回滚、架构、空库和双实例验收
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#10-验收门禁
  - docs/design/project-rules.md
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
depends_on:
  - T-ACCESS-006
  - T-ACCESS-007
  - T-ACCESS-009
  - T-ACCESS-010
blocks: []
acceptance:
  - "Java 21 全量 clean install、单元测试和 access-service Spring Context 测试通过"
  - "空 PostgreSQL DDL 测试、统一 Redis 配置测试和种子数据校验通过"
  - "除退役的 /admin/sync-task/* 外，admin 与 permission 现有 HTTP 路径、DTO、统一响应和错误码契约回归通过"
  - "负向验收确认 /admin/sync-task/* 无路由或返回明确的不存在响应，且 access-service 不注册对应 Controller 映射"
  - "跨域事务故障注入、强事务审计、独立日志失败和缓存 afterCommit/rollback 测试通过"
  - "安全矩阵、租户隔离、来源所有权和请求上下文清理负向测试通过"
  - "两个 access-service 实例共享 PostgreSQL/Redis 的权限失效、缓存故障、任务抢占和故障接管测试通过"
  - "授权缓存端到端测试覆盖 access-service L2 已接近10秒过期、Gateway 全链路回源延迟接近5秒后再缓存15秒的边界，证明从权限事实变更起的总陈旧窗口仍不超过30秒"
  - "Gateway 快照回源超过5秒时不写入本地缓存并返回503；测试证明服务发现、连接、发送、服务端处理、响应读取/解码和失效竞争重试共享同一全链路截止时间"
  - "Gateway 权限回源不可达时固定返回503；配置、代码和测试证明 open、stale-allow 与 gateway.permission.fail-mode 已删除"
  - "架构测试证明跨域写/读白名单、禁止横向依赖和禁止跨域写 QueryMapper"
  - "生成验收记录，列出命令、测试数、故障场景和所有遗留项；P0/P1 未关闭项阻止任务完成"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-11
---

# T-ACCESS-011 完成契约、回滚、架构、空库和双实例验收

## 背景

该任务是归并的质量门禁，不实现新的产品能力，只证明此前任务形成的单体在正常与故障场景下满足权威设计。

## 范围

- 汇总并补齐自动化测试矩阵。
- 执行全量、空库、契约、安全、事务、缓存、任务和双实例验证。
- 记录可复现的验收证据和未关闭风险。

## 完成记录

（待实施后填写。）
