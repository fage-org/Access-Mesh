---
doc_type: task
id: T-ACCESS-056
title: （ADM-T01）准入定案回写与协议落账
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §7/§8.1/§8.2
depends_on:
  - T-PERM-080
  - T-PERM-082
blocks: []
acceptance:
  - "准入候选/子行规则（结构有效即候选、CONTEXT_DEFERRED 运行时父判定延后业务）、新快照安全读取（新鲜数据库操作定义目录，§5.3）、映射歧义规则（同要求去重/异要求 AMBIGUOUS_REQUIREMENT 阻断/引用损坏 503 配置故障/无注册拒绝）、服务迁移门槛（最终检查代码位置+反向拒绝测试）核对与设计 §7/§8 一致并当轮归本设计/契约的当前章节、补可追溯来源"
  - "新错误原因族（AMBIGUOUS_REQUIREMENT、准入拒绝、准入配置故障）编号段落 AccessErrorCode 并挂契约总册新章（版本化准入协议——名称 interface-admission 族为建议）"
  - "N 系验收用例（§10.3）分配到 T-ACCESS-057~062 各卡验收面落账"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-056 （ADM-T01）准入定案回写与协议落账

## 背景

设计 §7/§8（报告临时编号 ADM-T01）。方案 A 主线沿 [历史定案原文](../archive/2026-09-26/decision-registry-before.md) 2026-09-09 方向定案（API 不单独授权、接口权限由操作权限关联派生）；本卡把设计定稿细则落成可引用的协议与验收分配。

## 范围

- 定案当轮归所属规范并补来源、契约总册新章（含 requiredPermission={resourceTypeCode, operationCode} 对外 DTO 形态）、错误码选段（按错误业务语义选 1xxxx/2xxxx 段）。

## 非目标 / 遗留

- 不写准入实现代码（在 T-ACCESS-057+）。
