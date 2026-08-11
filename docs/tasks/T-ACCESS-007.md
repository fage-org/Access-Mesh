---
doc_type: task
id: T-ACCESS-007
title: 合并系统配置与操作审计并落实日志事务分级
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#52-表合并边界
  - docs/design/access-service-architecture.md#82-审计事务分级
  - docs/design/project-rules.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-004
blocks: []
acceptance:
  - "sys_config 与原 system_config 收敛为唯一 system_config，配置键使用 admin./permission./access. 命名空间"
  - "sys_audit_log 与原 operation_log 收敛为唯一 operation_log，target_id 支持字符串且 module 可区分三个边界"
  - "原配置与日志 API 通过适配保持路径、DTO、响应和错误码兼容"
  - "operation_log、sys_login_log、sys_job_log 使用独立短事务；permission_change_log 的强事务写链路唯一归 T-ACCESS-005，本任务不重复修改权限写编排"
  - "请求体、响应和身份信息按规范脱敏、限长，密码、Token、密钥不会入库"
  - "独立日志异步执行使用有界线程池，队列满和写入失败具有同步降级或明确监控告警"
  - "测试覆盖主事务回滚、独立日志失败和敏感字段脱敏"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-11
---

# T-ACCESS-007 合并系统配置与操作审计并落实日志事务分级

## 背景

两服务存在真实重复的配置与普通操作日志，但安全登录、权限变更和任务日志具有不同的一致性与查询语义。

## 范围

- 合并配置和普通操作日志持久层及服务入口。
- 实现普通操作、登录和任务日志的独立事务；核对但不重复实现 T-ACCESS-005 所有的强事务权限审计。
- 建立脱敏、限长、线程池与失败监控测试。

## 完成记录

（待实施后填写。）
