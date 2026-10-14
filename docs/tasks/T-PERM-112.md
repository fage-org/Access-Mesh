---
doc_type: task
id: T-PERM-112
title: 互斥审计请求关联与主体补全
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "QueryAuditCollector 提交互斥审计条目前读取 OperatorContext 已有 requestId 填入（不再传 null 由下游生成无关 UUID）"
  - "摘要固定段补明确判定主体（User 写 subjectUserId；Roles 查询不虚构用户）"
  - "回归锁：合成 requestId 经真实 engine→collector→AuditDomainServiceImpl 链路后，写入条目 requestId 等于原请求 ID、摘要含主体（旧实现下失败）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P2（外部持续审查发现，探针实证真实引擎链路，2026-10-14 主代理核实成立）：`engine/query/QueryAuditCollector` 的 submit 构造 `AuditDomainService.OperationLogEntry`（`AuditDomainService.java:54-68`，第 10 参 requestId、第 5 参 targetId）时 requestId 传 null（targetId 亦 null），摘要仅含 execution/规则/阶段/affected；`AuditDomainServiceImpl` 对 null 生成独立 UUID，`OperationLogMapper.xml:27` 按 request_id 精确等值筛选——审计员拿业务请求的 requestId 检索 CONFLICT_DETECTED 永远命中不了；多人同角色命中互斥时，放宽检索也无法分清受影响主体。入口 `RequestContextInterceptor` 已绑定 requestId、`OperatorContext` 可读，属现成信息未消费。NOT NULL 兜底让行看起来有完整 ID，掩盖 ID 与请求无关的事实。

## 范围

collector 提交前补 requestId 与摘要主体字段；不改审计表结构、执行器语义、截断、一小时去重与尽力提交边界。

## 当前口径

复用现有请求上下文，不新增审计表、诊断 API、历史回放、outbox 或外部 TRACE；M2M operator=null 语义保持。

## 验收对照

见 frontmatter `acceptance`；探针（真实引擎+真实 AuditDomainServiceImpl+mock mapper 参数捕获）可转正为回归测试。

## 非目标 / 遗留

Q-060（排障重做）、Q-061（导出保留）不重启；008/011 等既有问题不随卡重提。
